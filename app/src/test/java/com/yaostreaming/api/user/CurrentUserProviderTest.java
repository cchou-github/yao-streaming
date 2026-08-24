package com.yaostreaming.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CurrentUserProviderTest {

	@Mock
	private UserRepository userRepository;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private CurrentUserProvider provider() {
		return new CurrentUserProvider(userRepository);
	}

	private static void authenticateAs(String email) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(email, "n/a",
						List.of(new SimpleGrantedAuthority("ROLE_USER"))));
	}

	@Test
	void returnsTheAuthenticatedUser() {
		User user = new User("owner@example.com", "Owner", "hash");
		when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
		authenticateAs("owner@example.com");

		assertThat(provider().require()).isSameAs(user);
	}

	@Test
	void rejectsWhenThereIsNoAuthentication() {
		assertThatThrownBy(() -> provider().require())
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("No authenticated user");
	}

	@Test
	void rejectsAnonymousAuthentication() {
		SecurityContextHolder.getContext().setAuthentication(
				new AnonymousAuthenticationToken("key", "anonymousUser",
						List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		assertThatThrownBy(() -> provider().require())
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("No authenticated user");
	}

	@Test
	void rejectsWhenTheAuthenticatedUserNoLongerExists() {
		when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
		authenticateAs("ghost@example.com");

		assertThatThrownBy(() -> provider().require())
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("ghost@example.com");
	}

}
