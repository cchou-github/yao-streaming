-- Local development seed data. NOT run by Flyway (which only reads
-- db/migration) and never executed automatically — run it explicitly:
--
--   docker compose exec -T mysql mysql -u streaming -pstreaming streaming \
--     < app/src/main/resources/db/seed/dev_user.sql
--
-- Logins (all sharing the same password so any of the five can be used
-- interchangeably): dev@example.com, dev2@example.com, dev3@example.com,
-- dev4@example.com, dev5@example.com / password
-- Five accounts exist so testing up to 5 concurrent live streams (the
-- MediaLive channel pool's fixed size) doesn't require manually creating
-- accounts first.
-- The hash is bcrypt("password"), generated with the same BCryptPasswordEncoder
-- that verifies it at login. SeedUserCredentialsTest asserts they still agree.

INSERT INTO users (email, display_name, password_hash)
VALUES ('dev@example.com', 'Dev User',
        '$2a$10$lwGtyJriDQ5fUto0vVoiGOgNY1BBJ3G5i8hkpF/85ONoaWfY/fjVu') AS new
ON DUPLICATE KEY UPDATE display_name = new.display_name,
                        password_hash = new.password_hash;

INSERT INTO users (email, display_name, password_hash)
VALUES ('dev2@example.com', 'Dev User 2',
        '$2a$10$lwGtyJriDQ5fUto0vVoiGOgNY1BBJ3G5i8hkpF/85ONoaWfY/fjVu') AS new
ON DUPLICATE KEY UPDATE display_name = new.display_name,
                        password_hash = new.password_hash;

INSERT INTO users (email, display_name, password_hash)
VALUES ('dev3@example.com', 'Dev User 3',
        '$2a$10$lwGtyJriDQ5fUto0vVoiGOgNY1BBJ3G5i8hkpF/85ONoaWfY/fjVu') AS new
ON DUPLICATE KEY UPDATE display_name = new.display_name,
                        password_hash = new.password_hash;

INSERT INTO users (email, display_name, password_hash)
VALUES ('dev4@example.com', 'Dev User 4',
        '$2a$10$lwGtyJriDQ5fUto0vVoiGOgNY1BBJ3G5i8hkpF/85ONoaWfY/fjVu') AS new
ON DUPLICATE KEY UPDATE display_name = new.display_name,
                        password_hash = new.password_hash;

INSERT INTO users (email, display_name, password_hash)
VALUES ('dev5@example.com', 'Dev User 5',
        '$2a$10$lwGtyJriDQ5fUto0vVoiGOgNY1BBJ3G5i8hkpF/85ONoaWfY/fjVu') AS new
ON DUPLICATE KEY UPDATE display_name = new.display_name,
                        password_hash = new.password_hash;
