package com.yaostreaming.api.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yaostreaming.api.storage.VideoStorage;
import com.yaostreaming.api.user.User;
import java.net.URL;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VideoUploadServiceTest {

	@Mock
	private VideoRepository videoRepository;

	@Mock
	private VideoStorage videoStorage;

	private VideoUploadService service;

	private final User user = new User("owner@example.com", "Owner", "hash");

	private final AtomicLong nextId = new AtomicLong(42L);

	@BeforeEach
	void setUp() {
		service = new VideoUploadService(videoRepository, videoStorage);
	}

	/**
	 * Mockito's {@code save()} doesn't simulate IDENTITY generation the way a
	 * real Hibernate insert would, so the video id the real code relies on (to
	 * rewrite {@code sourceKey}) has to be faked in here. Incrementing from
	 * call to call, starting at 42, so tests that create more than one video
	 * (like the distinct-key test below) see distinct ids the way real rows
	 * would. Only stubbed by the tests that actually call
	 * {@code createUpload} — Mockito's strict stubs flag it as unnecessary
	 * otherwise (the several tests below that exercise static helpers directly
	 * never touch {@code videoRepository} at all).
	 */
	private void stubIdentityGeneration() {
		doAnswer(invocation -> {
			Video video = invocation.getArgument(0);
			ReflectionTestUtils.setField(video, "id", nextId.getAndIncrement());
			return video;
		}).when(videoRepository).save(any());
	}

	private static URL url(String value) {
		try {
			return URI.create(value).toURL();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Test
	void savesVideoAsUploadingAndReturnsPresignedUrl() {
		stubIdentityGeneration();
		when(videoStorage.presignRawUpload(any(), eq("video/mp4")))
				.thenReturn(url("http://localhost:4566/raw/signed"));

		UploadResponse response = service.createUpload(user,
				new UploadRequest("Holiday", "In Kyoto", "clip.mp4", "video/mp4"));

		ArgumentCaptor<Video> saved = ArgumentCaptor.forClass(Video.class);
		verify(videoRepository).save(saved.capture());

		Video video = saved.getValue();
		assertThat(video.getStatus()).isEqualTo(VideoStatus.UPLOADING);
		assertThat(video.getTitle()).isEqualTo("Holiday");
		assertThat(video.getDescription()).isEqualTo("In Kyoto");
		assertThat(video.getUser()).isSameAs(user);
		assertThat(video.getSourceKey()).isEqualTo(response.sourceKey());
		assertThat(video.getPlaybackUrl()).isNull();

		assertThat(response.uploadUrl()).isEqualTo("http://localhost:4566/raw/signed");
		assertThat(response.sourceKey()).endsWith("/clip.mp4");
	}

	@Test
	void embedsTheGeneratedVideoIdInTheSourceKey() {
		stubIdentityGeneration();
		when(videoStorage.presignRawUpload(any(), any()))
				.thenReturn(url("http://localhost:4566/raw/signed"));

		UploadResponse response = service.createUpload(user,
				new UploadRequest("Title", null, "clip.mp4", "video/mp4"));

		assertThat(response.videoId()).isEqualTo(42L);
		// uploads/{videoId}/{filename} - the AWS submit Lambda parses videoId
		// straight out of this key, with no DB lookup.
		assertThat(response.sourceKey()).isEqualTo("uploads/42/clip.mp4");
	}

	@Test
	void presignsUsingTheSameKeyPersistedOnTheRow() {
		stubIdentityGeneration();
		when(videoStorage.presignRawUpload(any(), any()))
				.thenReturn(url("http://localhost:4566/raw/signed"));

		UploadResponse response = service.createUpload(user,
				new UploadRequest("Title", null, "clip.mp4", "video/mp4"));

		verify(videoStorage).presignRawUpload(response.sourceKey(), "video/mp4");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void leavesDescriptionUnsetWhenBlank(String description) {
		stubIdentityGeneration();
		when(videoStorage.presignRawUpload(any(), any()))
				.thenReturn(url("http://localhost:4566/raw/signed"));

		service.createUpload(user, new UploadRequest("Title", description, "clip.mp4", "video/mp4"));

		ArgumentCaptor<Video> saved = ArgumentCaptor.forClass(Video.class);
		verify(videoRepository).save(saved.capture());
		assertThat(saved.getValue().getDescription()).isNull();
	}

	@Test
	void leavesDescriptionUnsetWhenNull() {
		stubIdentityGeneration();
		when(videoStorage.presignRawUpload(any(), any()))
				.thenReturn(url("http://localhost:4566/raw/signed"));

		service.createUpload(user, new UploadRequest("Title", null, "clip.mp4", "video/mp4"));

		ArgumentCaptor<Video> saved = ArgumentCaptor.forClass(Video.class);
		verify(videoRepository).save(saved.capture());
		assertThat(saved.getValue().getDescription()).isNull();
	}

	@Test
	void buildsKeyAsUploadsVideoIdFilename() {
		String key = VideoUploadService.buildSourceKey(7L, "clip.mp4");

		assertThat(key).isEqualTo("uploads/7/clip.mp4");
	}

	@Test
	void generatesADistinctKeyPerUploadSoConcurrentSameNamesDoNotCollide() {
		stubIdentityGeneration();
		when(videoStorage.presignRawUpload(any(), any()))
				.thenReturn(url("http://localhost:4566/raw/signed"));

		UploadResponse first = service.createUpload(user,
				new UploadRequest("Title", null, "clip.mp4", "video/mp4"));
		UploadResponse second = service.createUpload(user,
				new UploadRequest("Title", null, "clip.mp4", "video/mp4"));

		assertThat(first.sourceKey()).isNotEqualTo(second.sourceKey());
		assertThat(first.sourceKey()).isEqualTo("uploads/42/clip.mp4");
		assertThat(second.sourceKey()).isEqualTo("uploads/43/clip.mp4");
	}

	@ParameterizedTest
	@CsvSource({
			"clip.mp4,clip.mp4",
			"My Holiday Video.mp4,My_Holiday_Video.mp4",
			"../../etc/passwd,passwd",
			"/absolute/path/clip.mp4,clip.mp4",
			"..\\..\\windows\\system32\\evil.exe,evil.exe",
			"clip;rm -rf.mp4,clip_rm_-rf.mp4",
			"クリップ.mp4,____.mp4",
			".hidden.mp4,hidden.mp4"
	})
	void sanitizesUntrustedFilenames(String input, String expected) {
		assertThat(VideoUploadService.sanitizeFilename(input)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "..", ".", "/", "..." })
	void fallsBackWhenNothingUsableRemains(String input) {
		assertThat(VideoUploadService.sanitizeFilename(input)).isEqualTo("upload");
	}

}
