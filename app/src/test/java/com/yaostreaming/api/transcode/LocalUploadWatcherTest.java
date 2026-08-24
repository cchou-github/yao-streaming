package com.yaostreaming.api.transcode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yaostreaming.api.storage.VideoStorage;
import com.yaostreaming.api.user.User;
import com.yaostreaming.api.video.Video;
import com.yaostreaming.api.video.VideoRepository;
import com.yaostreaming.api.video.VideoStatus;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalUploadWatcherTest {

	@Mock
	private VideoRepository videoRepository;

	@Mock
	private VideoStorage videoStorage;

	@Mock
	private LocalVideoTranscodeService videoTranscodeService;

	private LocalUploadWatcher watcher;

	private final User owner = new User("owner@example.com", "Owner", "hash");

	@BeforeEach
	void setUp() {
		watcher = new LocalUploadWatcher(videoRepository, videoStorage, videoTranscodeService);
	}

	/** Ids are database-generated, so set one directly for the test. */
	private Video videoWithId(long id, String sourceKey) {
		Video video = new Video(owner, "Clip " + id, sourceKey);
		try {
			Field field = Video.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(video, id);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
		return video;
	}

	@Test
	void transcodesUploadsWhoseObjectHasArrived() {
		Video arrived = videoWithId(1L, "uploads/a/clip.mp4");
		when(videoRepository.findByStatus(VideoStatus.UPLOADING)).thenReturn(List.of(arrived));
		when(videoStorage.rawObjectExists("uploads/a/clip.mp4")).thenReturn(true);

		watcher.transcodeArrivedUploads();

		verify(videoTranscodeService).process(1L);
	}

	@Test
	void leavesUploadsAloneUntilTheObjectExists() {
		Video notYet = videoWithId(2L, "uploads/b/clip.mp4");
		when(videoRepository.findByStatus(VideoStatus.UPLOADING)).thenReturn(List.of(notYet));
		when(videoStorage.rawObjectExists("uploads/b/clip.mp4")).thenReturn(false);

		watcher.transcodeArrivedUploads();

		verify(videoTranscodeService, never()).process(any());
	}

	@Test
	void oneFailureDoesNotStopTheRemainingUploads() {
		Video first = videoWithId(1L, "uploads/a/clip.mp4");
		Video second = videoWithId(2L, "uploads/b/clip.mp4");
		when(videoRepository.findByStatus(VideoStatus.UPLOADING)).thenReturn(List.of(first, second));
		when(videoStorage.rawObjectExists(any())).thenReturn(true);
		doThrow(new RuntimeException("storage blew up")).when(videoTranscodeService).process(1L);

		watcher.transcodeArrivedUploads();

		verify(videoTranscodeService).process(2L);
	}

	@Test
	void doesNothingWhenNothingIsAwaitingUpload() {
		when(videoRepository.findByStatus(VideoStatus.UPLOADING)).thenReturn(List.of());

		watcher.transcodeArrivedUploads();

		verify(videoTranscodeService, never()).process(any());
	}

}
