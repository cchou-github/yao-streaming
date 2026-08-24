package com.yaostreaming.api.security;

import com.yaostreaming.api.user.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Looks up login credentials in the {@code users} table. Spring Security calls
 * this during form login and compares the submitted password against the stored
 * BCrypt hash itself — the hash is never compared here by hand.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public DatabaseUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return userRepository.findByEmail(email)
				.map(user -> User.withUsername(user.getEmail())
						.password(user.getPasswordHash())
						.authorities("ROLE_USER")
						.build())
				.orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
	}

}
