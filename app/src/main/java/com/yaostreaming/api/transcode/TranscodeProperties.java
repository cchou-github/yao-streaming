package com.yaostreaming.api.transcode;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param pollInterval    how often to look for uploads that have arrived
 * @param timeout         how long a single ffmpeg/ffprobe invocation may run
 * @param segmentSeconds  target HLS segment length
 */
@ConfigurationProperties(prefix = "app.transcode")
public record TranscodeProperties(
		Duration pollInterval,
		Duration timeout,
		int segmentSeconds) {
}
