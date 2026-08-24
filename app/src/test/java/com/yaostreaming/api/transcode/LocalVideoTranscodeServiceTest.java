package com.yaostreaming.api.transcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yaostreaming.api.user.User;
import com.yaostreaming.api.video.Video;
import com.yaostreaming.api.video.VideoRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalVideoTranscodeServiceTest {

	@Mock
	private VideoRepository videoRepository;

	@Mock
	private TranscodePipeline transcodePipeline;

	@Mock
	private VideoStatusTransitions statusTransitions;

	private LocalVideoTranscodeService service;

	private Video video;

	@BeforeEach
	void setUp() {
		service = new LocalVideoTranscodeService(videoRepository, transcodePipeline, statusTransitions);
		video = new Video(new User("owner@example.com", "Owner", "hash"), "Clip", "uploads/abc/clip.mp4");
	}

	@Test
	void marksReadyWithTheOutputKeyAndDuration() {
		when(statusTransitions.claim(1L)).thenReturn(true);
		when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
		when(transcodePipeline.transcode("uploads/abc/clip.mp4", "videos/1"))
				.thenReturn(new TranscodeResult("videos/1/master.m3u8", 42));

		service.process(1L);

		// The key itself, not a URL - VideoCatalogService presigns it fresh at
		// watch time instead of a URL going stale in the database.
		verify(statusTransitions).markReady(1L, "videos/1/master.m3u8", 42);
		verify(statusTransitions, never()).markFailed(any());
	}

	@Test
	void doesNothingWhenTheVideoWasAlreadyClaimedByAnotherRun() {
		when(statusTransitions.claim(1L)).thenReturn(false);

		service.process(1L);

		verifyNoInteractions(transcodePipeline);
		verify(statusTransitions, never()).markReady(any(), any(), any());
		verify(statusTransitions, never()).markFailed(any());
	}

	@Test
	void marksFailedWhenThePipelineThrows() {
		when(statusTransitions.claim(1L)).thenReturn(true);
		when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
		when(transcodePipeline.transcode(any(), any()))
				.thenThrow(new TranscodeException("ffmpeg exited with 1"));

		service.process(1L);

		verify(statusTransitions).markFailed(1L);
		verify(statusTransitions, never()).markReady(any(), any(), any());
	}

	@Test
	void doesNotAttemptTranscodingWhenTheRowVanishedAfterBeingClaimed() {
		when(statusTransitions.claim(1L)).thenReturn(true);
		when(videoRepository.findById(1L)).thenReturn(Optional.empty());

		service.process(1L);

		verifyNoInteractions(transcodePipeline);
		verify(statusTransitions, never()).markFailed(any());
	}

	@Test
	void toleratesAnUnknownDuration() {
		when(statusTransitions.claim(1L)).thenReturn(true);
		when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
		when(transcodePipeline.transcode(any(), any()))
				.thenReturn(new TranscodeResult("videos/1/master.m3u8", null));

		service.process(1L);

		verify(statusTransitions).markReady(eq(1L), any(), eq(null));
	}

	@Test
	void namesOutputAfterTheVideoIdSoRunsCannotCollide() {
		assertThat(LocalVideoTranscodeService.outputPrefix(7L)).isEqualTo("videos/7");
		assertThat(LocalVideoTranscodeService.outputPrefix(8L)).isEqualTo("videos/8");
	}

}
