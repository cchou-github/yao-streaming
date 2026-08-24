package com.yaostreaming.api.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StoragePropertiesTest {

	private static StorageProperties withEndpoints(String endpoint, String publicEndpoint) {
		return new StorageProperties(endpoint, publicEndpoint, "us-east-1", "raw", "processed",
				Duration.ofMinutes(15), Duration.ofMinutes(45));
	}

	@Test
	void reportsEndpointOverridePresentWhenSet() {
		StorageProperties properties = withEndpoints("http://localstack:4566", "http://localhost:4566");

		assertThat(properties.hasEndpointOverride()).isTrue();
		assertThat(properties.hasPublicEndpointOverride()).isTrue();
	}

	@Test
	void reportsNoEndpointOverrideWhenNull() {
		StorageProperties properties = withEndpoints(null, null);

		assertThat(properties.hasEndpointOverride()).isFalse();
		assertThat(properties.hasPublicEndpointOverride()).isFalse();
	}

	@Test
	void reportsNoEndpointOverrideWhenBlank() {
		StorageProperties properties = withEndpoints("   ", "");

		assertThat(properties.hasEndpointOverride()).isFalse();
		assertThat(properties.hasPublicEndpointOverride()).isFalse();
	}

	@Test
	void exposesConfiguredValues() {
		StorageProperties properties = withEndpoints("http://localstack:4566", "http://localhost:4566");

		assertThat(properties.region()).isEqualTo("us-east-1");
		assertThat(properties.rawBucket()).isEqualTo("raw");
		assertThat(properties.processedBucket()).isEqualTo("processed");
		assertThat(properties.uploadUrlTtl()).isEqualTo(Duration.ofMinutes(15));
		assertThat(properties.playbackUrlTtl()).isEqualTo(Duration.ofMinutes(45));
	}

}
