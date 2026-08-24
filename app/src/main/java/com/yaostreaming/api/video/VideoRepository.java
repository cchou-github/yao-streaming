package com.yaostreaming.api.video;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, Long> {

	/** Backed by idx_videos_status. */
	List<Video> findByStatus(VideoStatus status);

	/** Catalogue listing. Fetches the uploader eagerly to avoid a query per row. */
	@Query("select v from Video v join fetch v.user order by v.createdAt desc")
	List<Video> findAllByOrderByCreatedAtDesc();

	/**
	 * Moves a row between statuses only if it is still in {@code from}, returning
	 * the number of rows changed. Used to claim work: a second caller racing for
	 * the same video updates 0 rows and backs off, so a video cannot be
	 * transcoded twice.
	 */
	@Modifying
	@Query("update Video v set v.status = :to where v.id = :id and v.status = :from")
	int changeStatus(@Param("id") Long id, @Param("from") VideoStatus from, @Param("to") VideoStatus to);

	/**
	 * Same CAS guard as {@link #changeStatus}, but also records the output key
	 * and duration in the one update. The AWS completion path has no
	 * {@code PROCESSING} claim step to make this transition exclusive some
	 * other way, and the completion callback can be redelivered (EventBridge is
	 * at-least-once) — a duplicate call finds the row no longer in {@code from}
	 * and updates 0 rows, a harmless no-op.
	 */
	@Modifying
	@Query("update Video v set v.status = :to, v.playbackUrl = :playbackUrl, v.durationSeconds = :durationSeconds "
			+ "where v.id = :id and v.status = :from")
	int completeIfStillIn(@Param("id") Long id, @Param("from") VideoStatus from, @Param("to") VideoStatus to,
			@Param("playbackUrl") String playbackUrl, @Param("durationSeconds") Integer durationSeconds);

}
