package com.yaostreaming.api.video;

/**
 * @param videoId   the created row, used to poll processing status afterwards
 * @param uploadUrl presigned S3 URL the client PUTs the file to
 * @param sourceKey S3 key the file will occupy in the raw bucket
 */
public record UploadResponse(Long videoId, String uploadUrl, String sourceKey) {
}
