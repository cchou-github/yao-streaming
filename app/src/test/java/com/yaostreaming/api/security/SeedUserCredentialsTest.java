package com.yaostreaming.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * The seed script's hash and the documented password are two separate strings
 * that have to agree, and nothing else would catch them drifting apart — login
 * would just silently fail for the seeded user.
 */
class SeedUserCredentialsTest {

	private static final Path SEED_SCRIPT = Path.of("src/main/resources/db/seed/dev_user.sql");

	private static final Pattern BCRYPT_HASH = Pattern.compile("'(\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53})'");

	@Test
	void seededHashMatchesTheDocumentedPassword() throws IOException {
		String sql = Files.readString(SEED_SCRIPT);

		Matcher matcher = BCRYPT_HASH.matcher(sql);
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		int hashesChecked = 0;
		while (matcher.find()) {
			String hash = matcher.group(1);
			assertThat(encoder.matches("password", hash))
					.as("hash %s in %s should be bcrypt(\"password\")", hash, SEED_SCRIPT)
					.isTrue();
			hashesChecked++;
		}

		assertThat(hashesChecked).as("seed script should contain a bcrypt hash").isGreaterThan(0);
	}

}
