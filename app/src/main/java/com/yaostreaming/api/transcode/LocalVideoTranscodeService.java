package com.yaostreaming.api.transcode;

import com.yaostreaming.api.video.Video;
import com.yaostreaming.api.video.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drives one video through transcoding, so the pipeline only has to produce
 * output and the watcher only has to notice work.
 *
 * <p>Deliberately not transactional as a whole: transcoding takes seconds to
 * minutes, and holding a transaction open for its duration would pin a database
 * connection and a row lock the whole time. The status writes are short,
 * independent transactions in {@link VideoStatusTransitions}.
 */
@Service
public class LocalVideoTranscodeService {

	private static final Logger log = LoggerFactory.getLogger(LocalVideoTranscodeService.class);

	private final VideoRepository videoRepository;

	private final TranscodePipeline transcodePipeline;

	private final VideoStatusTransitions statusTransitions;

	public LocalVideoTranscodeService(VideoRepository videoRepository,
			TranscodePipeline transcodePipeline, VideoStatusTransitions statusTransitions) {
		this.videoRepository = videoRepository;
		this.transcodePipeline = transcodePipeline;
		this.statusTransitions = statusTransitions;
	}

	public void process(Long videoId) {
		if (!statusTransitions.claim(videoId)) {
			return;
		}

		Video video = videoRepository.findById(videoId).orElse(null);
		if (video == null) {
			log.warn("Video {} disappeared after being claimed", videoId);
			return;
		}

		try {
			TranscodeResult result = transcodePipeline.transcode(video.getSourceKey(), outputPrefix(videoId));
			// The processed-bucket key, not a URL: VideoCatalogService presigns it
			// fresh at watch time (real AWS keeps this bucket fully private, so a
			// URL cached here would either 403 or - if presigned - go stale within
			// the hour). See VideoStatusTransitions.completeFromUpload for the
			// AWS-path equivalent of this same write.
			statusTransitions.markReady(videoId, result.masterPlaylistKey(), result.durationSeconds());
			log.info("Video {} is ready", videoId);
		}
		catch (RuntimeException ex) {
			// Record the failure, otherwise the row sits in PROCESSING forever and
			// the watcher — which only looks at UPLOADING — never revisits it.
			log.error("Transcoding video {} failed", videoId, ex);
			statusTransitions.markFailed(videoId);
		}
	}

	static String outputPrefix(Long videoId) {
		return "videos/" + videoId;
	}

}
