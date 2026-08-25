package com.yaostreaming.api.transcode;

/**
 * Turns a raw upload into streamable HLS output.
 *
 * <p>The local implementation shells out to ffmpeg. In AWS the same role is
 * played by MediaConvert, so this interface is the seam where that swap happens.
 *
 * <p>Note the shape will need revisiting for MediaConvert: this contract is
 * synchronous ("return once the output exists"), whereas MediaConvert is
 * submit-a-job-and-get-notified. The status transitions already live outside this
 * interface, in {@link LocalVideoTranscodeService}, which is what makes that change
 * tractable — an asynchronous implementation would report completion through the
 * same transitions rather than by returning.
 */
public interface TranscodePipeline {

	/**
	 * @param sourceKey    object to read from the raw bucket
	 * @param outputPrefix key prefix to write HLS output under in the processed bucket
	 * @throws TranscodeException if the source cannot be converted
	 */
	TranscodeResult transcode(String sourceKey, String outputPrefix);

}
