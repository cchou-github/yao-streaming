package com.yaostreaming.api.video;

import com.yaostreaming.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "videos")
public class Video {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(length = 2000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VideoStatus status;

	@Column(name = "source_key", nullable = false, length = 1024)
	private String sourceKey;

	@Column(name = "playback_url", length = 1024)
	private String playbackUrl;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

	protected Video() {
	}

	public Video(User user, String title, String sourceKey) {
		this.user = user;
		this.title = title;
		this.sourceKey = sourceKey;
		this.status = VideoStatus.UPLOADING;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public VideoStatus getStatus() {
		return status;
	}

	public void setStatus(VideoStatus status) {
		this.status = status;
	}

	public String getSourceKey() {
		return sourceKey;
	}

	/**
	 * Lets the upload key be rewritten once the DB-generated id is known — see
	 * {@link com.yaostreaming.api.video.VideoUploadService#createUpload}'s
	 * two-phase save.
	 */
	public void setSourceKey(String sourceKey) {
		this.sourceKey = sourceKey;
	}

	public String getPlaybackUrl() {
		return playbackUrl;
	}

	public void setPlaybackUrl(String playbackUrl) {
		this.playbackUrl = playbackUrl;
	}

	public Integer getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(Integer durationSeconds) {
		this.durationSeconds = durationSeconds;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
