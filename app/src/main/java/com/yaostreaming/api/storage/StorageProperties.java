package com.yaostreaming.api.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint         S3 endpoint for server-side calls. Empty against real
 *                         AWS, which uses the SDK's default resolution.
 * @param publicEndpoint   S3 endpoint used when signing URLs handed to a browser.
 *                         Differs from {@code endpoint} locally: the app reaches
 *                         LocalStack as {@code localstack:4566} on the compose
 *                         network, while the browser reaches it as
 *                         {@code localhost:4566}. SigV4 signs the Host header, so
 *                         the URL has to be signed for the host the browser will
 *                         actually send.
 * @param uploadUrlTtl     how long a presigned upload URL stays valid
 * @param playbackUrlTtl   how long a presigned playback (GET) URL stays valid.
 *                         Kept well under an hour: against real AWS this is
 *                         signed with IRSA's temporary STS credentials, and a
 *                         presigned URL cannot outlive the credentials that
 *                         signed it.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
		String endpoint,
		String publicEndpoint,
		String region,
		String rawBucket,
		String processedBucket,
		Duration uploadUrlTtl,
		Duration playbackUrlTtl) {

	public boolean hasEndpointOverride() {
		return endpoint != null && !endpoint.isBlank();
	}

	public boolean hasPublicEndpointOverride() {
		return publicEndpoint != null && !publicEndpoint.isBlank();
	}

}
