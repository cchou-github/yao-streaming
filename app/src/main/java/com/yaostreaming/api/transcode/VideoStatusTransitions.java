package com.yaostreaming.api.transcode;

import com.yaostreaming.api.video.VideoRepository;
import com.yaostreaming.api.video.VideoStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short, transactional status writes around a transcode.
 *
 * <p>A separate bean on purpose: {@code @Transactional} is applied by a proxy
 * around the bean, so a self-invocation ({@code this.markReady(...)}) inside
 * {@link LocalVideoTranscodeService} would silently run with no transaction at all —
 * and the entity mutations in {@code markReady} would never be flushed.
 */
@Component
public class VideoStatusTransitions {

	private final VideoRepository videoRepository;

	public VideoStatusTransitions(VideoRepository videoRepository) {
		this.videoRepository = videoRepository;
	}

	/** @return true if this caller is the one that moved the row into PROCESSING */
	@Transactional
	public boolean claim(Long videoId) {
		return videoRepository.changeStatus(videoId, VideoStatus.UPLOADING, VideoStatus.PROCESSING) == 1;
	}

	@Transactional
	public void markReady(Long videoId, String playbackUrl, Integer durationSeconds) {
		videoRepository.findById(videoId).ifPresent(video -> {
			video.setPlaybackUrl(playbackUrl);
			video.setDurationSeconds(durationSeconds);
			video.setStatus(VideoStatus.READY);
		});
	}

	@Transactional
	public void markFailed(Long videoId) {
		videoRepository.findById(videoId).ifPresent(video -> video.setStatus(VideoStatus.FAILED));
	}

	/**
	 * The AWS completion path: {@code UPLOADING} -> {@code READY} directly,
	 * with no {@code PROCESSING} claim in between (nothing on AWS "claims" a
	 * video the way {@link #claim} does locally — MediaConvert runs the job
	 * out of band). {@code outputKey} is stored in the same column
	 * {@link #markReady} writes a URL into; the catalogue presigns it into an
	 * actual URL at watch time instead of trusting a cached one.
	 *
	 * @return true if this call is the one that completed the video —
	 *         false means it was already out of UPLOADING (a redelivered
	 *         callback, harmlessly ignored)
	 */
	@Transactional
	public boolean completeFromUpload(Long videoId, String outputKey, Integer durationSeconds) {
		return videoRepository.completeIfStillIn(videoId, VideoStatus.UPLOADING, VideoStatus.READY,
				outputKey, durationSeconds) == 1;
	}

}
