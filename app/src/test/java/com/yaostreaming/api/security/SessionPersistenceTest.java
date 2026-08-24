package com.yaostreaming.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.yaostreaming.api.TestcontainersConfiguration;
import com.yaostreaming.api.user.User;
import com.yaostreaming.api.user.UserRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Proves sessions actually land in MySQL rather than living only in this
 * pod's memory — the entire point of switching to spring-session-jdbc.
 *
 * Uses a real embedded server (RANDOM_PORT) rather than MockMvc: MockMvc
 * builds its filter chain by discovering Filter beans and sorting them,
 * which doesn't reliably reproduce the ordering a real servlet container
 * guarantees between Spring Session's filter and Spring Security's — and
 * that ordering is exactly the thing this test needs to exercise for real.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class SessionPersistenceTest {

	private static final Pattern CSRF_TOKEN_PATTERN = Pattern.compile("_csrf\" value=\"([^\"]+)\"");

	private static final String TEST_EMAIL = "session-test@example.com";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * Not wrapped in a rolled-back @Transactional (the login POST runs in a
	 * separate request thread with its own transaction, so a test-managed one
	 * wouldn't cover it anyway) - and with the MySQL container now reused
	 * across separate `gradlew test` runs (see TestcontainersConfiguration),
	 * a leftover row from a previous run would either violate the email
	 * unique constraint on the next save() or inflate the session count this
	 * test asserts on. Clean up explicitly instead.
	 */
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("delete from SPRING_SESSION where PRINCIPAL_NAME = ?", TEST_EMAIL);
		jdbcTemplate.update("delete from users where email = ?", TEST_EMAIL);
	}

	@Test
	void loggingInWritesASessionRowIndexedByThePrincipal() {
		userRepository.save(new User(TEST_EMAIL, "Session Tester",
				passwordEncoder.encode("password")));

		ResponseEntity<String> loginPage = restTemplate.getForEntity("/login", String.class);
		String csrfToken = extractCsrfToken(loginPage.getBody());
		String sessionCookie = loginPage.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("username", TEST_EMAIL);
		form.add("password", "password");
		form.add("_csrf", csrfToken);

		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, sessionCookie);

		restTemplate.postForEntity("/login", new HttpEntity<>(form, headers), Void.class);

		Integer sessionCount = jdbcTemplate.queryForObject(
				"select count(*) from SPRING_SESSION where PRINCIPAL_NAME = ?",
				Integer.class, TEST_EMAIL);

		assertThat(sessionCount).isEqualTo(1);
	}

	private static String extractCsrfToken(String loginPageHtml) {
		Matcher matcher = CSRF_TOKEN_PATTERN.matcher(loginPageHtml);
		if (!matcher.find()) {
			throw new IllegalStateException("CSRF token not found in login page");
		}
		return matcher.group(1);
	}

}
