package com.yaostreaming.api.transcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yaostreaming.api.storage.StorageProperties;
import com.yaostreaming.api.storage.VideoStorage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Exercises the real ffmpeg invocation against real S3. Skipped when ffmpeg is
 * absent, so the suite still runs outside the dev container.
 */
@EnabledIf("ffmpegAvailable")
class LocalFfmpegTranscodePipelineTest {

	private static final String RAW_BUCKET = "transcode-raw";

	private static final String PROCESSED_BUCKET = "transcode-processed";

	private static LocalStackContainer localstack;

	private static S3Client s3Client;

	private static S3Presigner s3Presigner;

	private static VideoStorage storage;

	private static LocalFfmpegTranscodePipeline pipeline;

	static boolean ffmpegAvailable() {
		try {
			return new ProcessBuilder("ffmpeg", "-version")
					.redirectErrorStream(true)
					.start()
					.waitFor(20, TimeUnit.SECONDS);
		}
		catch (IOException ex) {
			return false;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	@BeforeAll
	static void startLocalStack() {
		// See VideoStorageTest's identical setup for why: withReuse lets this
		// container survive past this JVM exiting instead of paying
		// LocalStack's startup cost on every `gradlew test` run.
		localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
				.withReuse(true);
		localstack.start();

		String endpoint = localstack.getEndpoint().toString();
		StorageProperties properties = new StorageProperties(endpoint, endpoint, "us-east-1",
				RAW_BUCKET, PROCESSED_BUCKET, Duration.ofMinutes(15), Duration.ofMinutes(45));

		var credentials = StaticCredentialsProvider.create(
				AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
		var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

		s3Client = S3Client.builder()
				.endpointOverride(URI.create(endpoint))
				.region(Region.of("us-east-1"))
				.credentialsProvider(credentials)
				.serviceConfiguration(pathStyle)
				.build();
		s3Presigner = S3Presigner.builder()
				.endpointOverride(URI.create(endpoint))
				.region(Region.of("us-east-1"))
				.credentialsProvider(credentials)
				.serviceConfiguration(pathStyle)
				.build();

		createBucketIfAbsent(RAW_BUCKET);
		createBucketIfAbsent(PROCESSED_BUCKET);

		storage = new VideoStorage(s3Client, s3Presigner, properties);
		pipeline = new LocalFfmpegTranscodePipeline(storage,
				new TranscodeProperties(Duration.ofSeconds(5), Duration.ofMinutes(2), 1));
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

	/** Generates a short real video with ffmpeg and puts it in the raw bucket. */
	private static void putTestVideo(String key, Path tempDir, int seconds) throws Exception {
		Path source = tempDir.resolve("source.mp4");
		Process process = new ProcessBuilder("ffmpeg", "-loglevel", "error", "-y",
				"-f", "lavfi", "-i", "testsrc=duration=" + seconds + ":size=320x240:rate=15",
				"-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds,
				"-c:v", "libx264", "-c:a", "aac", "-shortest", source.toString())
				.redirectErrorStream(true).start();
		assertThat(process.waitFor(2, TimeUnit.MINUTES)).isTrue();
		assertThat(process.exitValue()).isZero();

		s3Client.putObject(PutObjectRequest.builder().bucket(RAW_BUCKET).key(key).build(),
				RequestBody.fromFile(source));
	}

	private static List<String> processedKeys(String prefix) {
		return s3Client.listObjectsV2(ListObjectsV2Request.builder()
				.bucket(PROCESSED_BUCKET).prefix(prefix).build())
				.contents().stream().map(o -> o.key()).toList();
	}

	@Test
	void producesAPlayableHlsLadderInTheProcessedBucket(@TempDir Path tempDir) throws Exception {
		putTestVideo("uploads/x/clip.mp4", tempDir, 3);

		TranscodeResult result = pipeline.transcode("uploads/x/clip.mp4", "videos/1");

		assertThat(result.masterPlaylistKey()).isEqualTo("videos/1/master.m3u8");
		assertThat(result.durationSeconds()).isEqualTo(3);

		List<String> keys = processedKeys("videos/1/");
		assertThat(keys).contains("videos/1/master.m3u8");
		assertThat(keys).anyMatch(key -> key.endsWith(".ts"));

		String manifest = s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(PROCESSED_BUCKET).key("videos/1/master.m3u8").build()).asUtf8String();
		assertThat(manifest)
				.startsWith("#EXTM3U")
				.contains("#EXT-X-PLAYLIST-TYPE:VOD")
				.contains("#EXT-X-ENDLIST")
				.contains(".ts");

		// Segments are referenced relatively, so uploading must preserve layout.
		assertThat(manifest).doesNotContain("/tmp/");
	}

	@Test
	void failsClearlyWhenTheSourceIsNotVideo() {
		s3Client.putObject(PutObjectRequest.builder().bucket(RAW_BUCKET).key("uploads/y/not-video.mp4").build(),
				RequestBody.fromString("this is definitely not a video"));

		assertThatThrownBy(() -> pipeline.transcode("uploads/y/not-video.mp4", "videos/2"))
				.isInstanceOf(TranscodeException.class)
				.hasMessageContaining("ffmpeg");

		assertThat(processedKeys("videos/2/")).isEmpty();
	}

	@Test
	void doesNotLeaveTemporaryDirectoriesBehind(@TempDir Path tempDir) throws Exception {
		putTestVideo("uploads/z/clip.mp4", tempDir, 1);
		long before = countTranscodeTempDirs();

		pipeline.transcode("uploads/z/clip.mp4", "videos/3");

		assertThat(countTranscodeTempDirs()).isEqualTo(before);
	}

	private static long countTranscodeTempDirs() throws IOException {
		Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
		try (var entries = Files.list(tmp)) {
			return entries.filter(p -> p.getFileName().toString().startsWith("transcode-")).count();
		}
	}

}
