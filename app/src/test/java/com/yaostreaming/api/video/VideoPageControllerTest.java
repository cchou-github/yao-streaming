package com.yaostreaming.api.video;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.yaostreaming.api.security.DatabaseUserDetailsService;
import com.yaostreaming.api.security.SecurityConfig;
import com.yaostreaming.api.storage.CloudFrontCookieSigner;
import com.yaostreaming.api.storage.CloudFrontProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VideoPageController.class)
@Import(SecurityConfig.class)
class VideoPageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VideoCatalogService videoCatalogService;

	@MockitoBean
	private DatabaseUserDetailsService userDetailsService;

	// Neither is a web-layer bean, so @WebMvcTest won't load them - stub
	// like any other VideoPageController dependency. Unstubbed
	// cloudFrontProperties.enabled() defaults to false (Mockito's default
	// for a boolean), matching the existing tests' non-CloudFront behavior
	// without needing explicit stubbing in each of them.
	@MockitoBean
	private CloudFrontProperties cloudFrontProperties;

	@MockitoBean
	private CloudFrontCookieSigner cloudFrontCookieSigner;

	private static VideoDetail ready() {
		return new VideoDetail(7L, "Holiday", "In Kyoto", VideoStatus.READY, 31,
				"http://localhost:4566/processed/videos/7/master.m3u8", "Reiko", Instant.EPOCH);
	}

	@Test
	@WithMockUser
	void catalogListsVideos() throws Exception {
		when(videoCatalogService.listRecent()).thenReturn(List.of(
				new VideoSummary(1L, "Holiday", VideoStatus.READY, 91, "Reiko", Instant.EPOCH)));

		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(view().name("videos/catalog"))
				.andExpect(model().attributeExists("videos"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Holiday")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("1:31")));
	}

	@Test
	@WithMockUser
	void catalogRendersAnEmptyState() throws Exception {
		when(videoCatalogService.listRecent()).thenReturn(List.of());

		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Nothing here yet")));
	}

	@Test
	@WithMockUser
	void watchPageEmbedsThePlayerAndTheSourceUrl() throws Exception {
		when(videoCatalogService.findDetail(7L)).thenReturn(Optional.of(ready()));

		mockMvc.perform(get("/videos/7"))
				.andExpect(status().isOk())
				.andExpect(view().name("videos/watch"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("hls.min.js")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("videos/7/master.m3u8")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("<video")));
	}

	@Test
	@WithMockUser
	void watchPageShowsStatusInsteadOfAPlayerWhileTranscoding() throws Exception {
		when(videoCatalogService.findDetail(8L)).thenReturn(Optional.of(new VideoDetail(
				8L, "Converting", null, VideoStatus.PROCESSING, null, null, "Reiko", Instant.EPOCH)));

		mockMvc.perform(get("/videos/8"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Transcoding now")))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("hls.min.js"))));
	}

	@Test
	@WithMockUser
	void watchPageSetsSignedCookiesWhenCloudFrontIsEnabled() throws Exception {
		when(videoCatalogService.findDetail(7L)).thenReturn(Optional.of(ready()));
		when(cloudFrontProperties.enabled()).thenReturn(true);
		when(cloudFrontCookieSigner.cookiesFor("videos/7/master.m3u8")).thenReturn(List.of(
				ResponseCookie.from("CloudFront-Policy", "policy-value").path("/videos/").build(),
				ResponseCookie.from("CloudFront-Signature", "sig-value").path("/videos/").build(),
				ResponseCookie.from("CloudFront-Key-Pair-Id", "key-value").path("/videos/").build()));

		mockMvc.perform(get("/videos/7"))
				.andExpect(status().isOk())
				.andExpect(cookie().value("CloudFront-Policy", "policy-value"))
				.andExpect(cookie().value("CloudFront-Signature", "sig-value"))
				.andExpect(cookie().value("CloudFront-Key-Pair-Id", "key-value"));
	}

	@Test
	@WithMockUser
	void watchPageSetsNoCookiesWhenCloudFrontIsDisabled() throws Exception {
		when(videoCatalogService.findDetail(7L)).thenReturn(Optional.of(ready()));

		mockMvc.perform(get("/videos/7"))
				.andExpect(status().isOk())
				.andExpect(cookie().doesNotExist("CloudFront-Policy"));
	}

	@Test
	@WithMockUser
	void watchPageSetsNoCookiesWhenNotYetPlayableEvenIfCloudFrontIsEnabled() throws Exception {
		when(videoCatalogService.findDetail(8L)).thenReturn(Optional.of(new VideoDetail(
				8L, "Converting", null, VideoStatus.PROCESSING, null, null, "Reiko", Instant.EPOCH)));
		when(cloudFrontProperties.enabled()).thenReturn(true);

		mockMvc.perform(get("/videos/8"))
				.andExpect(status().isOk())
				.andExpect(cookie().doesNotExist("CloudFront-Policy"));
	}

	@Test
	@WithMockUser
	void unknownVideoIsNotFound() throws Exception {
		when(videoCatalogService.findDetail(any())).thenReturn(Optional.empty());

		mockMvc.perform(get("/videos/404")).andExpect(status().isNotFound());
	}

	@Test
	void catalogRequiresSigningIn() throws Exception {
		mockMvc.perform(get("/")).andExpect(status().is3xxRedirection());
	}

	@Test
	void watchPageRequiresSigningIn() throws Exception {
		mockMvc.perform(get("/videos/7")).andExpect(status().is3xxRedirection());
	}

}
