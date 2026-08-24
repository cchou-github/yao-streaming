package com.yaostreaming.api.storage;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CloudFrontForwardedRequestFilterTest {

	private static final String DOMAIN = "d123example.cloudfront.net";

	@Test
	void passesTheOriginalRequestAndResponseThroughUnchangedWhenDisabled() throws Exception {
		CloudFrontProperties disabled = new CloudFrontProperties(false, null, null, null, Duration.ofMinutes(45));
		CloudFrontForwardedRequestFilter filter = new CloudFrontForwardedRequestFilter(disabled);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		// Not wrapped at all - the exact same instances reach the chain.
		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(chain.getResponse()).isSameAs(response);
	}

	@Test
	void reportsTheCloudFrontIdentityOnTheWrappedRequestWhenEnabled() throws Exception {
		CloudFrontForwardedRequestFilter filter = new CloudFrontForwardedRequestFilter(enabledProperties());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/videos/1");
		request.setScheme("http");
		request.setServerName("yao-streaming-alb-1848652714.ap-northeast-1.elb.amazonaws.com");
		request.setServerPort(80);
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		HttpServletRequest seenByChain = (HttpServletRequest) chain.getRequest();
		assertThat(seenByChain.getScheme()).isEqualTo("https");
		assertThat(seenByChain.isSecure()).isTrue();
		assertThat(seenByChain.getServerName()).isEqualTo(DOMAIN);
		assertThat(seenByChain.getServerPort()).isEqualTo(443);
	}

	/**
	 * The actual bug this filter exists to fix - confirmed live before this
	 * existed: {@code GET /} through CloudFront came back {@code Location:
	 * http://<alb-dns-name>/login}, because the servlet container resolves a
	 * relative {@code sendRedirect} location using the raw connection it
	 * received, never consulting an {@link HttpServletRequestWrapper}'s
	 * overridden methods. This asserts the fix at the one point that
	 * actually matters: what {@code sendRedirect} produces.
	 */
	@Test
	void rewritesARelativeRedirectToAnAbsoluteCloudFrontUrlWhenEnabled() throws Exception {
		CloudFrontForwardedRequestFilter filter = new CloudFrontForwardedRequestFilter(enabledProperties());
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
		HttpServletResponse seenByChain = (HttpServletResponse) chain.getResponse();
		seenByChain.sendRedirect("/login");

		MockHttpServletResponse underlying = (MockHttpServletResponse) unwrap(chain.getResponse());
		assertThat(underlying.getRedirectedUrl()).isEqualTo("https://" + DOMAIN + "/login");
	}

	@Test
	void leavesAnAlreadyAbsoluteRedirectUntouched() throws Exception {
		CloudFrontForwardedRequestFilter filter = new CloudFrontForwardedRequestFilter(enabledProperties());
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
		HttpServletResponse seenByChain = (HttpServletResponse) chain.getResponse();
		seenByChain.sendRedirect("https://elsewhere.example/callback");

		MockHttpServletResponse underlying = (MockHttpServletResponse) unwrap(chain.getResponse());
		assertThat(underlying.getRedirectedUrl()).isEqualTo("https://elsewhere.example/callback");
	}

	private static CloudFrontProperties enabledProperties() {
		return new CloudFrontProperties(true, DOMAIN, "K2JCJMDEHXQW5F", null, Duration.ofMinutes(45));
	}

	/**
	 * {@code MockHttpServletResponse} doesn't implement any unwrap-yourself
	 * interface, so the only way to get back to it through the wrapper is
	 * casting through the standard {@code ServletResponseWrapper} contract.
	 */
	private static jakarta.servlet.ServletResponse unwrap(jakarta.servlet.ServletResponse response) {
		while (response instanceof jakarta.servlet.ServletResponseWrapper wrapper) {
			response = wrapper.getResponse();
		}
		return response;
	}

}
