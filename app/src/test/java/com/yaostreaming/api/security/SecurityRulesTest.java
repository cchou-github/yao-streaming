package com.yaostreaming.api.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yaostreaming.api.storage.CloudFrontProperties;
import com.yaostreaming.api.user.CurrentUserProvider;
import com.yaostreaming.api.video.VideoUploadController;
import com.yaostreaming.api.video.VideoUploadService;
import com.yaostreaming.api.web.HomeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ HomeController.class, VideoUploadController.class })
@Import(SecurityConfig.class)
class SecurityRulesTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DatabaseUserDetailsService userDetailsService;

	@MockitoBean
	private CurrentUserProvider currentUserProvider;

	@MockitoBean
	private VideoUploadService videoUploadService;

	@MockitoBean
	private CloudFrontProperties cloudFrontProperties;

	@Test
	void loginPageIsPublic() throws Exception {
		mockMvc.perform(get("/login")).andExpect(status().isOk());
	}

	@Test
	void homeRedirectsToLoginWhenNotAuthenticated() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/login"));
	}

	@Test
	void uploadPageRedirectsToLoginWhenNotAuthenticated() throws Exception {
		mockMvc.perform(get("/upload"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/login"));
	}

	@Test
	void uploadApiRejectsUnauthenticatedRequests() throws Exception {
		mockMvc.perform(post("/api/videos").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	@WithMockUser
	void protectedPageIsReachableWhenAuthenticated() throws Exception {
		mockMvc.perform(get("/upload")).andExpect(status().isOk());
	}

	@Test
	@WithMockUser
	void postWithoutCsrfTokenIsForbidden() throws Exception {
		mockMvc.perform(post("/api/videos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser
	void postWithCsrfTokenReachesValidation() throws Exception {
		// 400 rather than 403 proves the CSRF filter let the request through.
		mockMvc.perform(post("/api/videos").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest());
	}

}
