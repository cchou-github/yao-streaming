package com.yaostreaming.api.storage;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.cookie.CookiesForCustomPolicy;
import software.amazon.awssdk.services.cloudfront.model.CustomSignerRequest;

/**
 * Mints CloudFront signed cookies authorizing a whole video's file set (the
 * master manifest, the variant manifest MediaConvert always adds beneath it,
 * and every segment) from a single call - a canned-policy signature can only
 * ever authorize one exact URL, so this uses CloudFront's <em>custom</em>
 * policy signing instead, with a wildcard {@code resourceUrlPattern}.
 *
 * <p>Only constructed with real key material when
 * {@link CloudFrontProperties#enabled()} is true; against LocalStack there is
 * no CloudFront to sign for at all, and {@link #cookiesFor} is never called.
 *
 * <p>{@code CloudFrontProperties} is registered as a bean via
 * {@code @ConfigurationPropertiesScan} on {@code StreamingApiApplication},
 * not declared here - constructor injection below is all this class needs.
 */
@Component
public class CloudFrontCookieSigner {

	private final CloudFrontProperties properties;
	private final CloudFrontUtilities cloudFrontUtilities = CloudFrontUtilities.create();
	private final PrivateKey privateKey;

	public CloudFrontCookieSigner(CloudFrontProperties properties) {
		this.properties = properties;
		this.privateKey = properties.enabled() ? loadPrivateKey(properties.privateKeyPem()) : null;
	}

	/**
	 * @param playbackKey the processed-bucket (or, for live, MediaPackage
	 *                    path-alias) key of the master manifest, e.g.
	 *                    {@code "videos/12/master.m3u8"} or
	 *                    {@code "live/pool-2/master.m3u8"}.
	 */
	public List<ResponseCookie> cookiesFor(String playbackKey) {
		String prefix = playbackKey.substring(0, playbackKey.lastIndexOf('/'));
		String resourceUrlPattern = "https://" + properties.domainName() + "/" + prefix + "/*";
		// The cookie's own Path attribute, not the signed policy's resource
		// pattern above - a browser only ever attaches a cookie to a request
		// whose path starts with this. Scoped to the whole top-level prefix
		// ("/videos/" or "/live/"), not the specific video/stream's own
		// subdirectory: matches this method's original videos/-only behavior
		// exactly for that content type (see this class's own test), and
		// extends the same whole-tree granularity to live rather than
		// inventing a narrower one just for it.
		String cookiePath = "/" + playbackKey.substring(0, playbackKey.indexOf('/') + 1);

		// resourceUrlPattern alone is NOT enough here: getCookiesForCustomPolicy
		// silently ignores it in the SDK version this project pins (verified by
		// this class's own test - the policy's Resource field came back as the
		// exact resourceUrl, not the pattern, despite aws-sdk-java-v2#6127
		// supposedly being fixed upstream). Passing the wildcard as resourceUrl
		// itself works regardless: undocumented but stable behavior, since
		// resourceUrl is what actually becomes the policy's Resource field.
		CustomSignerRequest request = CustomSignerRequest.builder()
				.resourceUrl(resourceUrlPattern)
				.resourceUrlPattern(resourceUrlPattern)
				.privateKey(privateKey)
				.keyPairId(properties.keyPairId())
				.expirationDate(Instant.now().plus(properties.cookieTtl()))
				.build();

		CookiesForCustomPolicy cookies = cloudFrontUtilities.getCookiesForCustomPolicy(request);
		return List.of(
				cookie(cookies.policyHeaderValue(), cookiePath),
				cookie(cookies.signatureHeaderValue(), cookiePath),
				cookie(cookies.keyPairIdHeaderValue(), cookiePath));
	}

	/**
	 * Each SDK header value already comes as {@code "Name=Value"} (e.g.
	 * {@code "CloudFront-Policy=eyJTdGF0ZW1lbnQi..."}) - split rather than
	 * assume a fixed name, so this stays correct regardless of exactly how
	 * the SDK formats it.
	 */
	private ResponseCookie cookie(String nameEqualsValue, String path) {
		int splitAt = nameEqualsValue.indexOf('=');
		String name = nameEqualsValue.substring(0, splitAt);
		String value = nameEqualsValue.substring(splitAt + 1);
		return ResponseCookie.from(name, value)
				// Scoped to this content type's own paths, not the whole site.
				.path(path)
				// Never read by JS - the <video>/hls.js element just needs the
				// browser to keep sending these automatically.
				.httpOnly(true)
				// CloudFront's viewer connection is always HTTPS
				// (viewer_protocol_policy = redirect-to-https in cloudfront.tf).
				.secure(true)
				// Safe to use Lax, not None: the app is proxied through this
				// same distribution too (see cloudfront.tf's header comment),
				// so this is genuinely same-origin, never a cross-site request.
				.sameSite("Lax")
				.maxAge(properties.cookieTtl())
				.build();
	}

	private static PrivateKey loadPrivateKey(String pem) {
		String base64 = pem
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		try {
			byte[] der = Base64.getDecoder().decode(base64);
			return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
		}
		catch (Exception ex) {
			throw new IllegalStateException("CLOUDFRONT_PRIVATE_KEY is not a valid PKCS#8 PEM RSA key", ex);
		}
	}

}
