package com.yaostreaming.api.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request for an upload slot. The client sends metadata only — the bytes go
 * straight to S3 via the presigned URL returned in {@link UploadResponse}.
 */
public record UploadRequest(
		@NotBlank @Size(max = 255) String title,

		@Size(max = 2000) String description,

		@NotBlank @Size(max = 255) String filename,

		@NotBlank @Pattern(regexp = "video/[-\\w.+]+", message = "must be a video content type")
		String contentType) {
}
