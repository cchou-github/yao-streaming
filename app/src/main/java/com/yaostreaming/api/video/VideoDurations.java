package com.yaostreaming.api.video;

/** Formats a duration for display, e.g. 91 → "1:31" and 3700 → "1:01:40". */
final class VideoDurations {

	private VideoDurations() {
	}

	static String format(Integer totalSeconds) {
		if (totalSeconds == null || totalSeconds < 0) {
			return "--:--";
		}

		int hours = totalSeconds / 3600;
		int minutes = (totalSeconds % 3600) / 60;
		int seconds = totalSeconds % 60;

		return hours > 0
				? "%d:%02d:%02d".formatted(hours, minutes, seconds)
				: "%d:%02d".formatted(minutes, seconds);
	}

}
