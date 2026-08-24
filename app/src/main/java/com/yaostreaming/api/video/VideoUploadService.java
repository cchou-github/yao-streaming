package com.yaostreaming.api.video;

import com.yaostreaming.api.storage.VideoStorage;
import com.yaostreaming.api.user.User;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoUploadService {

	private final VideoRepository videoRepository;

	private final VideoStorage videoStorage;

	public VideoUploadService(VideoRepository videoRepository, VideoStorage videoStorage) {
		this.videoRepository = videoRepository;
		this.videoStorage = videoStorage;
	}

	/**
	 * Records the intended upload and hands back a presigned URL. The row starts
	 * as {@link VideoStatus#UPLOADING} — nothing has been uploaded yet, and the
	 * bytes never pass through here.
	 *
	 * <p>Two-phase save: the real key embeds the video's own id (the AWS submit
	 * Lambda reads it straight out of the key with no DB lookup), but the id
	 * only exists once the row has been inserted. So this saves once with a
	 * placeholder key to obtain the id, then rewrites {@code sourceKey} on the
	 * still-managed entity — JPA's dirty checking flushes that as an UPDATE at
	 * commit, no second explicit {@code save()} needed.
	 */
	@Transactional
	public UploadResponse createUpload(User user, UploadRequest request) {
		String filename = sanitizeFilename(request.filename());

		Video video = new Video(user, request.title(), "pending/" + UUID.randomUUID() + "/" + filename);
		if (request.description() != null && !request.description().isBlank()) {
			video.setDescription(request.description());
		}
		videoRepository.save(video);

		String sourceKey = buildSourceKey(video.getId(), filename);
		video.setSourceKey(sourceKey);

		String uploadUrl = videoStorage.presignRawUpload(sourceKey, request.contentType()).toString();
		return new UploadResponse(video.getId(), uploadUrl, sourceKey);
	}

	/**
	 * {@code uploads/{videoId}/{filename}} — the video's own id already makes
	 * this unique per upload (every upload creates a new row), so unlike the
	 * pre-videoId key format there's no need for a UUID segment too. The AWS
	 * submit Lambda parses {@code videoId} straight out of this key.
	 */
	static String buildSourceKey(Long videoId, String filename) {
		return "uploads/" + videoId + "/" + filename;
	}

	/**
	 * Client-supplied filenames are untrusted: strip any directory components so
	 * a crafted name cannot place the object elsewhere in the bucket, and reduce
	 * the rest to characters that are unambiguous in an S3 key.
	 */
	static String sanitizeFilename(String filename) {
		String name = filename.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1);
		name = name.replaceAll("[^A-Za-z0-9._-]", "_");
		while (name.startsWith(".")) {
			name = name.substring(1);
		}
		return name.isBlank() ? "upload" : name;
	}

}
