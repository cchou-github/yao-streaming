package com.yaostreaming.api.transcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yaostreaming.api.video.VideoRepository;
import com.yaostreaming.api.video.VideoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoStatusTransitionsTest {

	@Mock
	private VideoRepository videoRepository;

	private VideoStatusTransitions statusTransitions;

	/** A field initializer here would run before Mockito injects @Mock. */
	@BeforeEach
	void setUp() {
		statusTransitions = new VideoStatusTransitions(videoRepository);
	}

	@Test
	void completeFromUploadDelegatesToTheCasUpdateAndReportsSuccess() {
		when(videoRepository.completeIfStillIn(1L, VideoStatus.UPLOADING, VideoStatus.READY,
				"videos/1/master.m3u8", 42)).thenReturn(1);

		boolean applied = statusTransitions.completeFromUpload(1L, "videos/1/master.m3u8", 42);

		assertThat(applied).isTrue();
		verify(videoRepository).completeIfStillIn(1L, VideoStatus.UPLOADING, VideoStatus.READY,
				"videos/1/master.m3u8", 42);
	}

	@Test
	void completeFromUploadReportsFalseWhenTheRowAlreadyLeftUploading() {
		when(videoRepository.completeIfStillIn(1L, VideoStatus.UPLOADING, VideoStatus.READY,
				"videos/1/master.m3u8", 42)).thenReturn(0);

		boolean applied = statusTransitions.completeFromUpload(1L, "videos/1/master.m3u8", 42);

		assertThat(applied).isFalse();
	}

	@Test
	void completeFromUploadToleratesAnUnknownDuration() {
		when(videoRepository.completeIfStillIn(1L, VideoStatus.UPLOADING, VideoStatus.READY,
				"videos/1/master.m3u8", null)).thenReturn(1);

		assertThat(statusTransitions.completeFromUpload(1L, "videos/1/master.m3u8", null)).isTrue();
	}

}
