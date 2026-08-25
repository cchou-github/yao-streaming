package com.yaostreaming.api.storage;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Credentials come from the SDK's default provider chain, which picks up
 * AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY from the environment locally and an
 * IAM role once deployed — so nothing credential-related is configured here.
 *
 * <p>{@code StorageProperties} is registered as a bean via
 * {@code @ConfigurationPropertiesScan} on {@code StreamingApiApplication},
 * not declared here - constructor injection below is all this class needs.
 */
@Configuration
public class S3Config {

	/** Path-style addressing: LocalStack does not serve virtual-host style buckets. */
	private static final S3Configuration PATH_STYLE = S3Configuration.builder()
			.pathStyleAccessEnabled(true)
			.build();

	@Bean
	S3Client s3Client(StorageProperties properties) {
		var builder = S3Client.builder().region(Region.of(properties.region()));
		if (properties.hasEndpointOverride()) {
			builder.endpointOverride(URI.create(properties.endpoint()))
					.serviceConfiguration(PATH_STYLE);
		}
		return builder.build();
	}

	@Bean
	S3Presigner s3Presigner(StorageProperties properties) {
		var builder = S3Presigner.builder().region(Region.of(properties.region()));
		if (properties.hasPublicEndpointOverride()) {
			builder.endpointOverride(URI.create(properties.publicEndpoint()))
					.serviceConfiguration(PATH_STYLE);
		}
		return builder.build();
	}

}
