package com.yaostreaming.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * withReuse: MySQL's own startup (creating system tables/users) is the
	 * single biggest cost in this suite - ~50s the first time a
	 * {@code @SpringBootTest} needs it. Reuse lets that container survive past
	 * this JVM exiting instead of paying that cost on every `gradlew test`
	 * run. Requires testcontainers.reuse.enable=true in
	 * ~/.testcontainers.properties (baked into Dockerfile.dev) and, since the
	 * container's data now genuinely persists across runs, any test using it
	 * outside a rolled-back @Transactional must clean up its own rows - see
	 * SessionPersistenceTest's @AfterEach.
	 */
	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:8.4")).withReuse(true);
	}

}
