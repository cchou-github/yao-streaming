package com.yaostreaming.api.user;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated user, so callers do not each have to reach into the
 * security context and re-derive it.
 */
@Component
public class CurrentUserProvider {

	private final UserRepository userRepository;

	public CurrentUserProvider(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User require() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			throw new AccessDeniedException("No authenticated user");
		}

		String email = authentication.getName();
		// The session outlives the row, so a user deleted mid-session lands here.
		return userRepository.findByEmail(email).orElseThrow(
				() -> new AccessDeniedException("Authenticated as " + email + " but no such user exists"));
	}

}
