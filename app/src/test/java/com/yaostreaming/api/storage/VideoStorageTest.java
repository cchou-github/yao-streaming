package com.yaostreaming.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class VideoStorageTest {

	private static final String RAW_BUCKET = "test-raw";

	private static final String PROCESSED_BUCKET = "test-processed";

	private static LocalStackContainer localstack;

	private static S3Client s3Client;

	private static S3Presigner s3Presigner;

	private static VideoStorage storage;

	private static StorageProperties properties;

	@BeforeAll
	static void startLocalStack() {
		// withReuse: this container survives past this JVM exiting (Ryuk won't
		// clean it up), so the next `gradlew test` run finds the same one
		// instead of paying LocalStack's startup cost again. Requires
		// testcontainers.reuse.enable=true in ~/.testcontainers.properties -
		// baked into Dockerfile.dev, since docker compose run --rm recreates
		// this container (and anything not on a volume) on every invocation.
		localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
				.withReuse(true);
		localstack.start();

		String endpoint = localstack.getEndpoint().toString();
		properties = new StorageProperties(endpoint, endpoint, "us-east-1", RAW_BUCKET, PROCESSED_BUCKET,
				Duration.ofMinutes(15), Duration.ofMinutes(45));

		var credentials = StaticCredentialsProvider.create(
				AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
		var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

		s3Client = S3Client.builder()
				.endpointOverride(URI.create(endpoint))
				.region(Region.of(properties.region()))
				.credentialsProvider(credentials)
				.serviceConfiguration(pathStyle)
				.build();

		s3Presigner = S3Presigner.builder()
				.endpointOverride(URI.create(endpoint))
				.region(Region.of(properties.region()))
				.credentialsProvider(credentials)
				.serviceConfiguration(pathStyle)
				.build();

		createBucketIfAbsent(RAW_BUCKET);
		createBucketIfAbsent(PROCESSED_BUCKET);

		storage = new VideoStorage(s3Client, s3Presigner, properties);
	}

	/**
	 * With container reuse the bucket may already exist from a previous test
	 * run against the same LocalStack container - CreateBucket on an
	 * already-owned bucket isn't guaranteed idempotent, so check first.
	 */
	private static void createBucketIfAbsent(String bucket) {
		try {
			s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
		}
		catch (NoSuchBucketException ex) {
			s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
		}
	}

	/**
	 * Deliberately doesn't stop {@code localstack}: that's the one call that
	 * would defeat {@code withReuse(true)} above - reuse only skips the
	 * automatic Ryuk cleanup on JVM exit, an explicit stop() still stops (and
	 * removes) the container same as always, which would mean paying
	 * LocalStack's startup cost on every single test run regardless.
	 */
	@AfterAll
	static void closeClients() {
		if (s3Client != null) {
			s3Client.close();
		}
		if (s3Presigner != null) {
			s3Presigner.close();
		}
	}

	private static void putRaw(String key, String body) {
		s3Client.putObject(PutObjectRequest.builder().bucket(RAW_BUCKET).key(key).build(),
				RequestBody.fromString(body));
	}

	@Test
	void presignedUrlAcceptsAnUploadFromAnHttpClient() throws Exception {
		URL url = storage.presignRawUpload("raw/presigned.mp4", "video/mp4");

		HttpResponse<Void> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(url.toURI())
						.header("Content-Type", "video/mp4")
						.PUT(HttpRequest.BodyPublishers.ofString("pretend-video-bytes"))
						.build(),
				HttpResponse.BodyHandlers.discarding());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(storage.rawObjectExists("raw/presigned.mp4")).isTrue();
	}

	@Test
	void reportsWhetherRawObjectExists() {
		putRaw("raw/exists.mp4", "data");

		assertThat(storage.rawObjectExists("raw/exists.mp4")).isTrue();
		assertThat(storage.rawObjectExists("raw/missing.mp4")).isFalse();
	}

	@Test
	void returnsRawObjectSize() {
		putRaw("raw/sized.mp4", "1234567890");

		assertThat(storage.rawObjectSize("raw/sized.mp4")).isEqualTo(10L);
	}

	@Test
	void throwsWhenSizingMissingRawObject() {
		assertThatThrownBy(() -> storage.rawObjectSize("raw/nope.mp4"))
				.isInstanceOf(NoSuchKeyException.class);
	}

	@Test
	void downloadsRawObjectToDisk(@TempDir Path tempDir) throws IOException {
		putRaw("raw/download.mp4", "downloaded-content");
		Path destination = tempDir.resolve("out.mp4");

		storage.downloadRawObject("raw/download.mp4", destination);

		assertThat(Files.readString(destination)).isEqualTo("downloaded-content");
	}

	@Test
	void uploadsDirectoryPreservingRelativePathsAndContentTypes(@TempDir Path tempDir) throws IOException {
		Path source = Files.createDirectories(tempDir.resolve("hls"));
		Files.writeString(source.resolve("master.m3u8"), "#EXTM3U");
		Path variant = Files.createDirectories(source.resolve("v0"));
		Files.writeString(variant.resolve("segment0.ts"), "segment-bytes");
		Files.writeString(variant.resolve("notes.txt"), "other");

		storage.uploadProcessedDirectory(source, "videos/42");

		assertThat(processedBody("videos/42/master.m3u8")).isEqualTo("#EXTM3U");
		assertThat(processedBody("videos/42/v0/segment0.ts")).isEqualTo("segment-bytes");
		assertThat(processedContentType("videos/42/master.m3u8")).isEqualTo("application/vnd.apple.mpegurl");
		assertThat(processedContentType("videos/42/v0/segment0.ts")).isEqualTo("video/mp2t");
		assertThat(processedContentType("videos/42/v0/notes.txt")).isEqualTo("application/octet-stream");
	}

	private static void putProcessed(String key, String body) {
		s3Client.putObject(PutObjectRequest.builder().bucket(PROCESSED_BUCKET).key(key).build(),
				RequestBody.fromString(body));
	}

	@Test
	void presignedGetUrlDownloadsTheProcessedObject() throws Exception {
		putProcessed("videos/42/master.m3u8", "#EXTM3U");

		URL url = storage.presignProcessedGet("videos/42/master.m3u8");

		HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(url.toURI()).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("#EXTM3U");
	}

	private static String processedBody(String key) {
		return s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(PROCESSED_BUCKET).key(key).build()).asUtf8String();
	}

	private static String processedContentType(String key) {
		return s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(PROCESSED_BUCKET).key(key).build()).response().contentType();
	}

}
