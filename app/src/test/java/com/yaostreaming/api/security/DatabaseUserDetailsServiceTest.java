package com.yaostreaming.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yaostreaming.api.user.User;
import com.yaostreaming.api.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

	@Mock
	private UserRepository userRepository;

	@Test
	void buildsUserDetailsFromTheStoredRow() {
		when(userRepository.findByEmail("dev@example.com"))
				.thenReturn(Optional.of(new User("dev@example.com", "Dev User", "stored-hash")));

		UserDetails details = new DatabaseUserDetailsService(userRepository)
				.loadUserByUsername("dev@example.com");

		assertThat(details.getUsername()).isEqualTo("dev@example.com");
		assertThat(details.getPassword()).isEqualTo("stored-hash");
		assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
	}

	@Test
	void throwsWhenNoUserMatchesTheEmail() {
		when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		DatabaseUserDetailsService service = new DatabaseUserDetailsService(userRepository);

		assertThatThrownBy(() -> service.loadUserByUsername("nobody@example.com"))
				.isInstanceOf(UsernameNotFoundException.class)
				.hasMessageContaining("nobody@example.com");
	}

}
