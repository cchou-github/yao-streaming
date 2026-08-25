package com.yaostreaming.api.video;

import java.time.Instant;

/** A single video's watch page. {@code playbackUrl} is null until READY. */
public record VideoDetail(
		Long id,
		String title,
		String description,
		VideoStatus status,
		Integer durationSeconds,
		String playbackUrl,
		String uploaderName,
		Instant createdAt) {

	public boolean isPlayable() {
		return status == VideoStatus.READY && playbackUrl != null;
	}

	public String formattedDuration() {
		return VideoDurations.format(durationSeconds);
	}

	/** Message shown in place of the player while a video is not watchable. */
	public String statusMessage() {
		return switch (status) {
			case UPLOADING -> "Waiting for the upload to finish.";
			case PROCESSING -> "Transcoding now — this page will play it once it is ready.";
			case FAILED -> "Transcoding failed, so this video cannot be played.";
			case READY -> playbackUrl == null
					? "Marked ready but no playback URL was recorded."
					: "";
		};
	}

}
