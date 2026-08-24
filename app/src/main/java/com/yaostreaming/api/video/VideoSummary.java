package com.yaostreaming.api.video;

import java.time.Instant;

/**
 * A row in the catalogue. Built inside the service's transaction so the view
 * never touches a lazily-loaded association.
 */
public record VideoSummary(
		Long id,
		String title,
		VideoStatus status,
		Integer durationSeconds,
		String uploaderName,
		Instant createdAt) {

	public boolean isPlayable() {
		return status == VideoStatus.READY;
	}

	public String formattedDuration() {
		return VideoDurations.format(durationSeconds);
	}

}
