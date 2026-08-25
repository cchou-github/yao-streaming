package com.yaostreaming.api.transcode;

/**
 * @param masterPlaylistKey key of the HLS master playlist in the processed bucket
 * @param durationSeconds   source duration, or null if it could not be determined
 */
public record TranscodeResult(String masterPlaylistKey, Integer durationSeconds) {
}
