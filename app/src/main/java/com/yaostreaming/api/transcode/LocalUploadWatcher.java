package com.yaostreaming.api.transcode;

import com.yaostreaming.api.storage.VideoStorage;
import com.yaostreaming.api.video.Video;
import com.yaostreaming.api.video.VideoRepository;
import com.yaostreaming.api.video.VideoStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Notices uploads that have arrived and hands them to transcoding.
 *
 * <p>Polls storage rather than trusting the client to report completion: the
 * browser can be closed mid-upload, and a client that never calls back would
 * leave the row stuck. In AWS this role belongs to an S3 event triggering a
 * Lambda — also storage-driven, so the client stays uninvolved either way.
 *
 * <p>Runs on the scheduler thread, so transcodes are serialised. That is fine
 * locally and arguably desirable, since ffmpeg is CPU-bound; a real deployment
 * gets parallelism from MediaConvert instead.
 *
 * <p>{@code @ConditionalOnProperty} disables this bean entirely (not just the
 * scheduled method - it never gets constructed at all) against real AWS, via
 * TRANSCODE_WATCHER_ENABLED=false in k8s/aws/app.yaml. Without this, it races
 * the real S3-triggered Lambda pipeline for every upload and reliably wins -
 * local DB polling every few seconds is much faster than an S3 event
 * reaching a cold Lambda and a real MediaConvert job. It claims the row,
 * tries to run ffmpeg (not present in the production image on purpose - see
 * app/Dockerfile), fails, and marks the video FAILED before MediaConvert
 * ever finishes - the AWS completion callback's CAS guard then correctly
 * refuses to override that terminal state with the real READY. Caught for
 * real: every video failed exactly this way the first time this pipeline was
 * tested end-to-end.
 */
@Component
@ConditionalOnProperty(name = "app.transcode.watcher-enabled", havingValue = "true", matchIfMissing = true)
public class LocalUploadWatcher {

	private static final Logger log = LoggerFactory.getLogger(LocalUploadWatcher.class);

	private final VideoRepository videoRepository;

	private final VideoStorage videoStorage;

	private final LocalVideoTranscodeService videoTranscodeService;

	public LocalUploadWatcher(VideoRepository videoRepository, VideoStorage videoStorage,
			LocalVideoTranscodeService videoTranscodeService) {
		this.videoRepository = videoRepository;
		this.videoStorage = videoStorage;
		this.videoTranscodeService = videoTranscodeService;
	}

	@Scheduled(fixedDelayString = "${app.transcode.poll-interval}")
	public void transcodeArrivedUploads() {
		List<Video> awaiting = videoRepository.findByStatus(VideoStatus.UPLOADING);
		for (Video video : awaiting) {
			try {
				if (videoStorage.rawObjectExists(video.getSourceKey())) {
					videoTranscodeService.process(video.getId());
				}
			}
			catch (RuntimeException ex) {
				// One bad video must not stop the others, nor kill the schedule.
				log.error("Could not start transcoding video {}", video.getId(), ex);
			}
		}
	}

}
