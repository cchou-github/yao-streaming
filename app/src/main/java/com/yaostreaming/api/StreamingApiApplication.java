package com.yaostreaming.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @ConfigurationPropertiesScan} auto-registers every
 * {@code @ConfigurationProperties} class found under this package (
 * {@code StorageProperties}, {@code TranscodeProperties},
 * {@code CloudFrontProperties}) as a bean - replaces three separate,
 * arbitrarily-placed {@code @EnableConfigurationProperties(X.class)}
 * declarations that used to sit on whichever consumer class happened to
 * need each one first ({@code S3Config}, {@code LocalFfmpegTranscodePipeline},
 * {@code CloudFrontCookieSigner}).
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class StreamingApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(StreamingApiApplication.class, args);
	}

}
