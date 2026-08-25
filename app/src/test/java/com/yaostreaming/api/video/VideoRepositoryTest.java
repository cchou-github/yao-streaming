package com.yaostreaming.api.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.yaostreaming.api.TestcontainersConfiguration;
import com.yaostreaming.api.user.User;
import com.yaostreaming.api.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class VideoRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private VideoRepository videoRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void savesAndLoadsVideoWithStatusAndUser() {
		User user = userRepository.save(new User("viewer@example.com", "Viewer", "hashed"));

		Video saved = videoRepository.save(new Video(user, "My First Upload", "raw-uploads/abc123.mp4"));

		// Force a real read from the DB instead of returning the cached
		// in-memory instance, so DB-generated defaults (created_at) are populated.
		entityManager.flush();
		entityManager.clear();

		Video loaded = videoRepository.findById(saved.getId()).orElseThrow();

		assertThat(loaded.getStatus()).isEqualTo(VideoStatus.UPLOADING);
		assertThat(loaded.getUser().getEmail()).isEqualTo("viewer@example.com");
		assertThat(loaded.getCreatedAt()).isNotNull();
	}

	@Test
	void completeIfStillInMovesUploadingStraightToReadyWithOutputKeyAndDuration() {
		User user = userRepository.save(new User("completer@example.com", "Completer", "hashed"));
		Video video = videoRepository.save(new Video(user, "AWS Upload", "uploads/1/clip.mp4"));

		int updated = videoRepository.completeIfStillIn(video.getId(), VideoStatus.UPLOADING, VideoStatus.READY,
				"videos/1/master.m3u8", 42);

		assertThat(updated).isEqualTo(1);
		entityManager.flush();
		entityManager.clear();
		Video loaded = videoRepository.findById(video.getId()).orElseThrow();
		assertThat(loaded.getStatus()).isEqualTo(VideoStatus.READY);
		assertThat(loaded.getPlaybackUrl()).isEqualTo("videos/1/master.m3u8");
		assertThat(loaded.getDurationSeconds()).isEqualTo(42);
	}

	@Test
	void completeIfStillInIsANoOpWhenTheRowAlreadyLeftTheFromStatus() {
		User user = userRepository.save(new User("redelivered@example.com", "Redelivered", "hashed"));
		Video video = videoRepository.save(new Video(user, "AWS Upload", "uploads/2/clip.mp4"));
		videoRepository.completeIfStillIn(video.getId(), VideoStatus.UPLOADING, VideoStatus.READY,
				"videos/2/master.m3u8", 10);
		entityManager.flush();
		entityManager.clear();

		// Simulates a redelivered callback (EventBridge is at-least-once):
		// the row is no longer UPLOADING, so this must change nothing.
		int updated = videoRepository.completeIfStillIn(video.getId(), VideoStatus.UPLOADING, VideoStatus.READY,
				"videos/2/should-not-apply.m3u8", 999);

		assertThat(updated).isEqualTo(0);
		entityManager.flush();
		entityManager.clear();
		Video loaded = videoRepository.findById(video.getId()).orElseThrow();
		assertThat(loaded.getPlaybackUrl()).isEqualTo("videos/2/master.m3u8");
		assertThat(loaded.getDurationSeconds()).isEqualTo(10);
	}

}
