package com.yaostreaming.api.video;

import com.yaostreaming.api.storage.CloudFrontProperties;
import com.yaostreaming.api.storage.VideoStorage;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the catalogue. Maps entities to view models while the transaction
 * is still open, so pages never depend on {@code open-in-view} to resolve the
 * lazily-loaded uploader.
 */
@Service
@Transactional(readOnly = true)
public class VideoCatalogService {

	private final VideoRepository videoRepository;

	private final VideoStorage videoStorage;

	private final CloudFrontProperties cloudFrontProperties;

	public VideoCatalogService(VideoRepository videoRepository, VideoStorage videoStorage,
			CloudFrontProperties cloudFrontProperties) {
		this.videoRepository = videoRepository;
		this.videoStorage = videoStorage;
		this.cloudFrontProperties = cloudFrontProperties;
	}

	/** Everything, newest first — in-progress videos included, so uploads are visible. */
	public List<VideoSummary> listRecent() {
		return videoRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(VideoCatalogService::toSummary)
				.toList();
	}

	public Optional<VideoDetail> findDetail(Long id) {
		return videoRepository.findById(id).map(this::toDetail);
	}

	private static VideoSummary toSummary(Video video) {
		return new VideoSummary(
				video.getId(),
				video.getTitle(),
				video.getStatus(),
				video.getDurationSeconds(),
				video.getUser().getDisplayName(),
				video.getCreatedAt());
	}

	/**
	 * {@code Video.playbackUrl} holds a processed-bucket key, not a URL (see
	 * {@code VideoStatusTransitions}).
	 */
	private VideoDetail toDetail(Video video) {
		String playbackUrl = video.getStatus() == VideoStatus.READY && video.getPlaybackUrl() != null
				? playbackUrlFor(video)
				: null;
		return new VideoDetail(
				video.getId(),
				video.getTitle(),
				video.getDescription(),
				video.getStatus(),
				video.getDurationSeconds(),
				playbackUrl,
				video.getUser().getDisplayName(),
				video.getCreatedAt());
	}

	/**
	 * Against real AWS, the app is proxied through the same CloudFront
	 * distribution that fronts the processed bucket (see terraform/cloudfront.tf),
	 * so a plain distribution URL plus the signed cookies
	 * {@code VideoPageController} sets is enough - no per-request signing here.
	 * Locally/against LocalStack, where no such distribution exists, this falls
	 * back to a presigned S3 GET exactly as before.
	 */
	private String playbackUrlFor(Video video) {
		return cloudFrontProperties.enabled()
				? "https://" + cloudFrontProperties.domainName() + "/" + video.getPlaybackUrl()
				: videoStorage.presignProcessedGet(video.getPlaybackUrl()).toString();
	}

}
