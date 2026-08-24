package com.yaostreaming.api.storage;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Against real AWS, the app is only ever meant to be reached through the
 * CloudFront distribution that proxies it (see terraform/cloudfront.tf) -
 * but CloudFront talks to the ALB over plain HTTP (the ALB has no HTTPS
 * listener) and its origin request policy deliberately strips the
 * browser's real {@code Host} header, substituting the ALB's own hostname
 * instead. Left alone, every relative redirect the app issues (Spring
 * Security's {@code sendRedirect("/login")}, the post-login
 * {@code sendRedirect("/")}, etc.) gets resolved by the servlet container
 * against that raw connection - producing an absolute {@code Location}
 * pointing at the bare ALB over HTTP, which leaks the browser off the
 * CloudFront domain entirely and breaks the signed-cookie flow (the
 * cookies {@link CloudFrontCookieSigner} sets are only ever sent back to
 * CloudFront's domain, not the ALB's). Confirmed live: {@code GET /}
 * through CloudFront came back {@code 302 Location:
 * http://<alb-dns-name>/login}.
 *
 * <p>Rather than trust forwarded headers - none of which reliably carry
 * the truth here, since CloudFront doesn't forward the original
 * {@code Host} and the ALB's own {@code X-Forwarded-Proto} only describes
 * the CloudFront-to-ALB hop (genuinely HTTP) rather than what the browser
 * actually used - this overrides the request's reported scheme/host/port
 * directly with what's already known to be correct whenever CloudFront is
 * enabled: HTTPS (CloudFront forces this - see {@code viewer_protocol_policy}
 * in cloudfront.tf), at {@link CloudFrontProperties#domainName()}.
 *
 * <p>The request wrapper alone isn't enough, though: {@code
 * HttpServletResponse.sendRedirect(String)} resolves a relative location
 * into an absolute {@code Location} header using the raw connector-level
 * request the container actually received - it never consults an
 * {@code HttpServletRequestWrapper}'s overridden methods, since those only
 * affect application code that explicitly calls {@code request.getScheme()}
 * and friends. Confirmed live even after wrapping the request alone:
 * {@code GET /} still came back {@code Location: http://<alb-dns>/login}.
 * The response has to be wrapped too, so {@code sendRedirect} itself builds
 * the absolute URL before the container gets a chance to - the same
 * approach Spring's own {@code ForwardedHeaderFilter} uses, for the same
 * reason.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CloudFrontForwardedRequestFilter extends OncePerRequestFilter {

	private final CloudFrontProperties properties;

	public CloudFrontForwardedRequestFilter(CloudFrontProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (properties.enabled()) {
			chain.doFilter(
					new CloudFrontRequestWrapper(request, properties.domainName()),
					new CloudFrontResponseWrapper(response, properties.domainName()));
			return;
		}
		chain.doFilter(request, response);
	}

	/**
	 * Everything a servlet container consults to turn a relative redirect
	 * into an absolute {@code Location} header - overridden to report the
	 * CloudFront-facing identity instead of the raw ALB connection this
	 * request actually arrived on.
	 */
	private static final class CloudFrontRequestWrapper extends HttpServletRequestWrapper {

		private final String domainName;

		private CloudFrontRequestWrapper(HttpServletRequest request, String domainName) {
			super(request);
			this.domainName = domainName;
		}

		@Override
		public String getScheme() {
			return "https";
		}

		@Override
		public boolean isSecure() {
			return true;
		}

		@Override
		public String getServerName() {
			return domainName;
		}

		@Override
		public int getServerPort() {
			return 443;
		}
	}

	/**
	 * The piece that actually fixes the redirect: intercepts {@code
	 * sendRedirect} before the container gets it, and if the location isn't
	 * already absolute, resolves it against the CloudFront domain itself
	 * rather than letting Tomcat resolve it against the raw ALB connection.
	 */
	private static final class CloudFrontResponseWrapper extends HttpServletResponseWrapper {

		private final String domainName;

		private CloudFrontResponseWrapper(HttpServletResponse response, String domainName) {
			super(response);
			this.domainName = domainName;
		}

		@Override
		public void sendRedirect(String location) throws IOException {
			if (location == null || location.startsWith("http://") || location.startsWith("https://")) {
				super.sendRedirect(location);
				return;
			}
			String path = location.startsWith("/") ? location : "/" + location;
			super.sendRedirect("https://" + domainName + path);
		}
	}

}
