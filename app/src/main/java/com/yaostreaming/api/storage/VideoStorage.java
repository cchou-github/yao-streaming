package com.yaostreaming.api.storage;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** S3 operations for the VOD pipeline, over the raw and processed buckets. */
@Component
public class VideoStorage {

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final StorageProperties properties;

	public VideoStorage(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
		this.s3Client = s3Client;
		this.s3Presigner = s3Presigner;
		this.properties = properties;
	}

	/**
	 * Presigned PUT URL letting a client upload straight to the raw bucket, so
	 * video bytes never pass through the API.
	 */
	public URL presignRawUpload(String key, String contentType) {
		PutObjectRequest putRequest = PutObjectRequest.builder()
				.bucket(properties.rawBucket())
				.key(key)
				.contentType(contentType)
				.build();

		return s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
				.signatureDuration(properties.uploadUrlTtl())
				.putObjectRequest(putRequest)
				.build())
				.url();
	}

	public boolean rawObjectExists(String key) {
		try {
			s3Client.headObject(HeadObjectRequest.builder()
					.bucket(properties.rawBucket())
					.key(key)
					.build());
			return true;
		}
		catch (NoSuchKeyException ex) {
			return false;
		}
	}

	public long rawObjectSize(String key) {
		return s3Client.headObject(HeadObjectRequest.builder()
				.bucket(properties.rawBucket())
				.key(key)
				.build())
				.contentLength();
	}

	public void downloadRawObject(String key, Path destination) {
		s3Client.getObject(GetObjectRequest.builder()
				.bucket(properties.rawBucket())
				.key(key)
				.build(),
				destination);
	}

	/**
	 * Uploads every file under {@code source} to the processed bucket beneath
	 * {@code keyPrefix}, preserving relative paths so an HLS manifest's relative
	 * segment references keep resolving.
	 */
	public void uploadProcessedDirectory(Path source, String keyPrefix) throws IOException {
		try (var files = Files.walk(source)) {
			List<Path> toUpload = files.filter(Files::isRegularFile).toList();
			for (Path file : toUpload) {
				String relative = source.relativize(file).toString().replace('\\', '/');
				s3Client.putObject(PutObjectRequest.builder()
						.bucket(properties.processedBucket())
						.key(keyPrefix + "/" + relative)
						.contentType(contentTypeFor(relative))
						.build(),
						RequestBody.fromFile(file));
			}
		}
	}

	/**
	 * Presigned GET URL for a processed object, computed fresh on every call
	 * rather than cached: the processed bucket is fully private against real
	 * AWS (pending CloudFront), so an unsigned URL would 403, and a presigned
	 * one can't be cached in the database either — it's signed with IRSA's
	 * short-lived STS credentials and would go stale well before a video
	 * stops being watched.
	 */
	public URL presignProcessedGet(String key) {
		GetObjectRequest getRequest = GetObjectRequest.builder()
				.bucket(properties.processedBucket())
				.key(key)
				.build();

		return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(properties.playbackUrlTtl())
				.getObjectRequest(getRequest)
				.build())
				.url();
	}

	private static String contentTypeFor(String filename) {
		if (filename.endsWith(".m3u8")) {
			return "application/vnd.apple.mpegurl";
		}
		if (filename.endsWith(".ts")) {
			return "video/mp2t";
		}
		return "application/octet-stream";
	}

}
