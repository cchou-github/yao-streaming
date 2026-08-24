package com.yaostreaming.api.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class CloudFrontCookieSignerTest {

	private static final String DOMAIN = "d123example.cloudfront.net";
	private static final String KEY_PAIR_ID = "K2JCJMDEHXQW5F";

	private static CloudFrontProperties enabledProperties() throws Exception {
		return new CloudFrontProperties(true, DOMAIN, KEY_PAIR_ID, pkcs8Pem(), Duration.ofMinutes(45));
	}

	private static String pkcs8Pem() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair keyPair = generator.generateKeyPair();
		// java.security.KeyPair's default encoding for RSA is already PKCS#8 -
		// matches what CloudFrontCookieSigner.loadPrivateKey expects.
		String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded());
		return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
	}

	/**
	 * CloudFront's cookie/URL signing uses a modified base64 alphabet
	 * ({@code + -> -}, {@code = -> _}, {@code / -> ~}) - undo it before
	 * standard base64 decoding.
	 */
	private static String decodePolicy(String cloudFrontBase64) {
		String standard = cloudFrontBase64.replace('-', '+').replace('_', '=').replace('~', '/');
		return new String(Base64.getDecoder().decode(standard), StandardCharsets.UTF_8);
	}

	@Test
	void mintsThreeCookiesScopedToTheWholeVideoPrefixNotJustTheOneManifest() throws Exception {
		CloudFrontCookieSigner signer = new CloudFrontCookieSigner(enabledProperties());

		List<ResponseCookie> cookies = signer.cookiesFor("videos/7/master.m3u8");

		assertThat(cookies).extracting(ResponseCookie::getName)
				.containsExactlyInAnyOrder("CloudFront-Policy", "CloudFront-Signature", "CloudFront-Key-Pair-Id");
		assertThat(cookies).allSatisfy(cookie -> {
			assertThat(cookie.isHttpOnly()).isTrue();
			assertThat(cookie.isSecure()).isTrue();
			assertThat(cookie.getPath()).isEqualTo("/videos/");
			assertThat(cookie.getSameSite()).isEqualTo("Lax");
		});

		ResponseCookie keyPairIdCookie = cookies.stream()
				.filter(c -> c.getName().equals("CloudFront-Key-Pair-Id")).findFirst().orElseThrow();
		assertThat(keyPairIdCookie.getValue()).isEqualTo(KEY_PAIR_ID);

		// The important assertion: this is exactly the behavior that was
		// broken in older AWS SDK versions (aws-sdk-java-v2#6127) -
		// resourceUrlPattern silently ignored, policy scoped to only the
		// one exact manifest URL instead of the whole video's file set.
		ResponseCookie policyCookie = cookies.stream()
				.filter(c -> c.getName().equals("CloudFront-Policy")).findFirst().orElseThrow();
		String policyJson = decodePolicy(policyCookie.getValue());
		assertThat(policyJson).contains("https://" + DOMAIN + "/videos/7/*");
		assertThat(policyJson).doesNotContain("master.m3u8");
	}

	@Test
	void scopesTheCookiePathToTheLiveTreeForALivePlaybackKey() throws Exception {
		CloudFrontCookieSigner signer = new CloudFrontCookieSigner(enabledProperties());

		List<ResponseCookie> cookies = signer.cookiesFor("live/pool-2/master.m3u8");

		// The cookie's own Path attribute is what the browser checks before
		// ever attaching it to a request - if this stayed hardcoded to
		// "/videos/" (this method's original behavior), the browser would
		// never send it on a live/... request at all, regardless of what the
		// signed policy inside it authorizes.
		assertThat(cookies).allSatisfy(cookie -> assertThat(cookie.getPath()).isEqualTo("/live/"));

		ResponseCookie policyCookie = cookies.stream()
				.filter(c -> c.getName().equals("CloudFront-Policy")).findFirst().orElseThrow();
		String policyJson = decodePolicy(policyCookie.getValue());
		assertThat(policyJson).contains("https://" + DOMAIN + "/live/pool-2/*");
	}

	@Test
	void neverTouchesKeyMaterialWhenDisabled() {
		CloudFrontProperties disabled = new CloudFrontProperties(false, null, null, null, Duration.ofMinutes(45));

		// Must not throw even though privateKeyPem is null/unparseable -
		// loadPrivateKey is only invoked when enabled() is true.
		new CloudFrontCookieSigner(disabled);
	}

}
