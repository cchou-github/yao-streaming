package com.yaostreaming.api.user;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class UserTest {

	@Test
	void passwordHashIsExcludedFromJsonSerialization() throws Exception {
		User user = new User("viewer@example.com", "Viewer", "super-secret-hash");

		String json = new ObjectMapper().writeValueAsString(user);

		assertThat(json).doesNotContain("passwordHash", "super-secret-hash");
	}

}
