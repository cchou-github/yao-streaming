package com.yaostreaming.api.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled       false everywhere except real AWS - LocalStack has no
 *                      CloudFront equivalent, and the processed bucket is
 *                      already public there, so this feature stays fully
 *                      inert locally.
 * @param domainName    the distribution's domain, e.g. {@code d123.cloudfront.net}.
 * @param keyPairId     id of the CloudFront public key trusted to verify
 *                      cookies signed with {@code privateKeyPem}.
 * @param privateKeyPem PKCS#8 PEM. See {@code CloudFrontCookieSigner} for why
 *                      PKCS#8 specifically.
 * @param cookieTtl     how long a signed cookie stays valid.
 */
@ConfigurationProperties(prefix = "app.cloudfront")
public record CloudFrontProperties(
		boolean enabled,
		String domainName,
		String keyPairId,
		String privateKeyPem,
		Duration cookieTtl) {
}
