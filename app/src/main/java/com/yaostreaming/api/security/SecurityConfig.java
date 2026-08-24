package com.yaostreaming.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/**
	 * BCrypt is deliberately slow and salts each hash, so identical passwords
	 * produce different stored values and brute-forcing a leaked hash is costly.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(requests -> requests
						// Liveness/readiness must stay reachable without a session.
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/login").permitAll()
						// The completion Lambda calls this with no session at all - the
						// gate is network-level (only reachable via the internal ALB,
						// itself security-group-restricted to the Lambda's own SG), not
						// app-level auth. See TranscodeCallbackController.
						.requestMatchers("/internal/**").permitAll()
						.anyRequest().authenticated())
				.formLogin(login -> login
						.loginPage("/login")
						.defaultSuccessUrl("/", true)
						.permitAll())
				.logout(logout -> logout
						.logoutSuccessUrl("/login?logout")
						.permitAll());
		// CSRF protection stays enabled: sessions are cookie-based, so a
		// cross-site POST would otherwise be able to act as the logged-in user.
		// Browser JS reads the token from a meta tag and sends it as a header.
		// /internal/** is exempt: the completion Lambda has no session or CSRF
		// token to send - same network-level gate as the permitAll above.
		http.csrf(csrf -> csrf.ignoringRequestMatchers("/internal/**"));
		return http.build();
	}

}
