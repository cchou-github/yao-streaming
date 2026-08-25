package com.yaostreaming.api.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ConfigTest {

	/**
	 * StorageProperties is normally registered via
	 * {@code @ConfigurationPropertiesScan} on StreamingApiApplication, which
	 * this deliberately isolated context never loads - so it needs its own
	 * way to bind it, same as any {@code @ConfigurationProperties} consumer
	 * tested outside the real application context.
	 */
	@EnableConfigurationProperties(StorageProperties.class)
	@Configuration
	static class TestConfig {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(S3Config.class, TestConfig.class)
			.withSystemProperties(
					"aws.accessKeyId=test",
					"aws.secretAccessKey=test")
			.withPropertyValues(
					"app.storage.region=us-east-1",
					"app.storage.raw-bucket=raw",
					"app.storage.processed-bucket=processed",
					"app.storage.upload-url-ttl=15m");

	@Test
	void buildsClientsWithEndpointOverrides() {
		runner.withPropertyValues(
				"app.storage.endpoint=http://localstack:4566",
				"app.storage.public-endpoint=http://localhost:4566")
				.run(context -> assertThat(context)
						.hasSingleBean(S3Client.class)
						.hasSingleBean(S3Presigner.class));
	}

	@Test
	void buildsClientsWithoutEndpointOverrides() {
		runner.run(context -> assertThat(context)
				.hasSingleBean(S3Client.class)
				.hasSingleBean(S3Presigner.class));
	}

}
