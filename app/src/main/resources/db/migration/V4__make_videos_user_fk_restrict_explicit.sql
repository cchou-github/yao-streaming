-- Deleting a user who still has videos is intentionally blocked: video content
-- must be explicitly reassigned or removed first, so account deletion can never
-- silently destroy it. MySQL already defaults to RESTRICT; this restates it so
-- the intent is visible in the schema rather than inherited from a default.
ALTER TABLE videos DROP FOREIGN KEY fk_videos_user;

ALTER TABLE videos
    ADD CONSTRAINT fk_videos_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT;
