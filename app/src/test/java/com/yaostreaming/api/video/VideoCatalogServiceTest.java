package com.yaostreaming.api.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yaostreaming.api.storage.CloudFrontProperties;
import com.yaostreaming.api.storage.VideoStorage;
import com.yaostreaming.api.user.User;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoCatalogServiceTest {

	@Mock
	private VideoRepository videoRepository;

	@Mock
	private VideoStorage videoStorage;

	private static final CloudFrontProperties CLOUDFRONT_DISABLED =
			new CloudFrontProperties(false, null, null, null, Duration.ofMinutes(45));

	private VideoCatalogService service;

	private final User owner = new User("owner@example.com", "Reiko", "hash");

	@BeforeEach
	void setUp() {
		service = new VideoCatalogService(videoRepository, videoStorage, CLOUDFRONT_DISABLED);
	}

	private static URL url(String value) {
		try {
			return URI.create(value).toURL();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Ids and generated columns are database-assigned, so set them directly. */
	private static Video video(long id, String title, VideoStatus status, User owner) {
		Video video = new Video(owner, title, "uploads/" + id + "/clip.mp4");
		video.setStatus(status);
		set(video, "id", id);
		return video;
	}

	private static void set(Object target, String fieldName, Object value) {
		try {
			Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Test
	void mapsEachVideoToASummaryIncludingTheUploader() {
		Video ready = video(1L, "Holiday", VideoStatus.READY, owner);
		ready.setDurationSeconds(91);
		when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(ready));

		List<VideoSummary> summaries = service.listRecent();

		assertThat(summaries).hasSize(1);
		VideoSummary summary = summaries.getFirst();
		assertThat(summary.id()).isEqualTo(1L);
		assertThat(summary.title()).isEqualTo("Holiday");
		assertThat(summary.status()).isEqualTo(VideoStatus.READY);
		assertThat(summary.uploaderName()).isEqualTo("Reiko");
		assertThat(summary.formattedDuration()).isEqualTo("1:31");
		assertThat(summary.isPlayable()).isTrue();
	}

	@Test
	void includesVideosThatAreNotReadyYetSoUploadsStayVisible() {
		when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
				video(1L, "Done", VideoStatus.READY, owner),
				video(2L, "Converting", VideoStatus.PROCESSING, owner),
				video(3L, "Broken", VideoStatus.FAILED, owner)));

		List<VideoSummary> summaries = service.listRecent();

		assertThat(summaries).extracting(VideoSummary::title)
				.containsExactly("Done", "Converting", "Broken");
		assertThat(summaries).extracting(VideoSummary::isPlayable)
				.containsExactly(true, false, false);
	}

	@Test
	void returnsAnEmptyListWhenNothingHasBeenUploaded() {
		when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

		assertThat(service.listRecent()).isEmpty();
	}

	@Test
	void mapsASingleVideoToItsDetailPresigningTheStoredKeyIntoAUrl() {
		Video ready = video(7L, "Holiday", VideoStatus.READY, owner);
		ready.setDescription("In Kyoto");
		ready.setDurationSeconds(31);
		ready.setPlaybackUrl("videos/7/master.m3u8");
		when(videoRepository.findById(7L)).thenReturn(Optional.of(ready));
		when(videoStorage.presignProcessedGet("videos/7/master.m3u8"))
				.thenReturn(url("https://processed.s3.amazonaws.com/videos/7/master.m3u8?X-Amz-Signature=abc"));

		VideoDetail detail = service.findDetail(7L).orElseThrow();

		assertThat(detail.id()).isEqualTo(7L);
		assertThat(detail.description()).isEqualTo("In Kyoto");
		assertThat(detail.playbackUrl())
				.isEqualTo("https://processed.s3.amazonaws.com/videos/7/master.m3u8?X-Amz-Signature=abc");
		assertThat(detail.uploaderName()).isEqualTo("Reiko");
		assertThat(detail.isPlayable()).isTrue();
	}

	@Test
	void buildsAPlainCloudFrontUrlInsteadOfPresigningWhenCloudFrontIsEnabled() {
		VideoCatalogService cloudFrontService = new VideoCatalogService(videoRepository, videoStorage,
				new CloudFrontProperties(true, "d123.cloudfront.net", "KEYPAIRID", "unused", Duration.ofMinutes(45)));
		Video ready = video(7L, "Holiday", VideoStatus.READY, owner);
		ready.setPlaybackUrl("videos/7/master.m3u8");
		when(videoRepository.findById(7L)).thenReturn(Optional.of(ready));

		VideoDetail detail = cloudFrontService.findDetail(7L).orElseThrow();

		assertThat(detail.playbackUrl()).isEqualTo("https://d123.cloudfront.net/videos/7/master.m3u8");
		verifyNoInteractions(videoStorage);
	}

	@Test
	void leavesPlaybackUrlNullWhenNotYetReadyWithoutCallingStorage() {
		Video processing = video(9L, "Still going", VideoStatus.PROCESSING, owner);
		when(videoRepository.findById(9L)).thenReturn(Optional.of(processing));

		VideoDetail detail = service.findDetail(9L).orElseThrow();

		assertThat(detail.playbackUrl()).isNull();
		assertThat(detail.isPlayable()).isFalse();
		verifyNoInteractions(videoStorage);
	}

	@Test
	void returnsEmptyForAnUnknownVideo() {
		when(videoRepository.findById(404L)).thenReturn(Optional.empty());

		assertThat(service.findDetail(404L)).isEmpty();
	}

}
