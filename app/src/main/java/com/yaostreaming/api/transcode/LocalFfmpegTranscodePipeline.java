package com.yaostreaming.api.transcode;

import com.yaostreaming.api.storage.VideoStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local stand-in for MediaConvert: downloads the raw upload, runs ffmpeg to
 * produce a single-rendition HLS ladder, and uploads the result.
 *
 * <p>MediaConvert will produce several bitrates behind one master playlist. This
 * deliberately produces one rendition — enough to prove the pipeline shape and
 * keep local runs fast, since adaptive bitrate is the encoder's concern rather
 * than the application's.
 *
 * <p>{@code TranscodeProperties} is registered as a bean via
 * {@code @ConfigurationPropertiesScan} on {@code StreamingApiApplication},
 * not declared here - constructor injection below is all this class needs.
 */
@Component
public class LocalFfmpegTranscodePipeline implements TranscodePipeline {

	private static final Logger log = LoggerFactory.getLogger(LocalFfmpegTranscodePipeline.class);

	private static final String MASTER_PLAYLIST = "master.m3u8";

	private final VideoStorage videoStorage;

	private final TranscodeProperties properties;

	public LocalFfmpegTranscodePipeline(VideoStorage videoStorage, TranscodeProperties properties) {
		this.videoStorage = videoStorage;
		this.properties = properties;
	}

	@Override
	public TranscodeResult transcode(String sourceKey, String outputPrefix) {
		Path workDir = createWorkDirectory();
		try {
			Path input = workDir.resolve("input");
			videoStorage.downloadRawObject(sourceKey, input);

			Path outputDir = Files.createDirectories(workDir.resolve("hls"));
			runFfmpeg(input, outputDir);

			Integer duration = probeDurationSeconds(input);
			videoStorage.uploadProcessedDirectory(outputDir, outputPrefix);

			log.info("Transcoded {} to {}/{}", sourceKey, outputPrefix, MASTER_PLAYLIST);
			return new TranscodeResult(outputPrefix + "/" + MASTER_PLAYLIST, duration);
		}
		catch (IOException ex) {
			throw new TranscodeException("Failed to transcode " + sourceKey, ex);
		}
		finally {
			deleteRecursively(workDir);
		}
	}

	private void runFfmpeg(Path input, Path outputDir) {
		run("ffmpeg",
				"-nostdin",
				"-loglevel", "error",
				"-y",
				"-i", input.toString(),
				"-c:v", "h264",
				"-c:a", "aac",
				"-f", "hls",
				"-hls_time", String.valueOf(properties.segmentSeconds()),
				"-hls_playlist_type", "vod",
				"-hls_segment_filename", outputDir.resolve("segment%03d.ts").toString(),
				outputDir.resolve(MASTER_PLAYLIST).toString());
	}

	/**
	 * Duration is metadata for display only, so a failure here is logged and the
	 * field left null rather than failing an otherwise successful transcode.
	 */
	private Integer probeDurationSeconds(Path input) {
		try {
			String output = run("ffprobe",
					"-v", "error",
					"-show_entries", "format=duration",
					"-of", "csv=p=0",
					input.toString());
			return (int) Math.round(Double.parseDouble(output.trim()));
		}
		catch (RuntimeException ex) {
			// Covers both a failed ffprobe run and unparseable output.
			log.warn("Could not determine duration for {}: {}", input, ex.getMessage());
			return null;
		}
	}

	private String run(String... command) {
		Process process = null;
		try {
			process = new ProcessBuilder(command).redirectErrorStream(true).start();
			// Read before waitFor: a full pipe buffer would otherwise deadlock us.
			String output = new String(process.getInputStream().readAllBytes());

			if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				throw new TranscodeException(command[0] + " timed out after " + properties.timeout());
			}
			if (process.exitValue() != 0) {
				throw new TranscodeException(
						command[0] + " exited with " + process.exitValue() + ": " + output.trim());
			}
			return output;
		}
		catch (IOException ex) {
			throw new TranscodeException("Could not run " + command[0], ex);
		}
		catch (InterruptedException ex) {
			if (process != null) {
				process.destroyForcibly();
			}
			Thread.currentThread().interrupt();
			throw new TranscodeException(command[0] + " was interrupted", ex);
		}
	}

	private static Path createWorkDirectory() {
		try {
			return Files.createTempDirectory("transcode-");
		}
		catch (IOException ex) {
			throw new TranscodeException("Could not create a working directory", ex);
		}
	}

	/** Best-effort cleanup: a leftover temp directory must not fail the job. */
	private static void deleteRecursively(Path directory) {
		try (var paths = Files.walk(directory)) {
			List<Path> deepestFirst = paths.sorted(Comparator.reverseOrder()).toList();
			for (Path path : deepestFirst) {
				Files.deleteIfExists(path);
			}
		}
		catch (IOException ex) {
			log.warn("Could not clean up {}: {}", directory, ex.getMessage());
		}
	}

}
