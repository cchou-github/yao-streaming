package com.yaostreaming.api.video;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class VideoDetailTest {

	private static VideoDetail detail(VideoStatus status, String playbackUrl) {
		return new VideoDetail(1L, "Clip", null, status, 90, playbackUrl, "Owner", Instant.EPOCH);
	}

	@Test
	void isPlayableOnlyWhenReadyAndAUrlExists() {
		assertThat(detail(VideoStatus.READY, "http://example/master.m3u8").isPlayable()).isTrue();
		assertThat(detail(VideoStatus.READY, null).isPlayable()).isFalse();
		assertThat(detail(VideoStatus.PROCESSING, "http://example/master.m3u8").isPlayable()).isFalse();
		assertThat(detail(VideoStatus.UPLOADING, null).isPlayable()).isFalse();
		assertThat(detail(VideoStatus.FAILED, null).isPlayable()).isFalse();
	}

	@Test
	void explainsWhyEachNonPlayableStateCannotBeWatched() {
		assertThat(detail(VideoStatus.UPLOADING, null).statusMessage()).contains("upload");
		assertThat(detail(VideoStatus.PROCESSING, null).statusMessage()).contains("Transcoding");
		assertThat(detail(VideoStatus.FAILED, null).statusMessage()).contains("failed");
	}

	@Test
	void flagsTheInconsistentCaseOfReadyWithoutAUrl() {
		assertThat(detail(VideoStatus.READY, null).statusMessage())
				.contains("no playback URL");
	}

	@Test
	void saysNothingWhenTheVideoIsActuallyPlayable() {
		assertThat(detail(VideoStatus.READY, "http://example/master.m3u8").statusMessage()).isEmpty();
	}

	@Test
	void formatsDurationForDisplay() {
		assertThat(detail(VideoStatus.READY, "u").formattedDuration()).isEqualTo("1:30");
	}

}
