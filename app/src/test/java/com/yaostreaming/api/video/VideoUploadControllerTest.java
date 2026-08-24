package com.yaostreaming.api.video;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yaostreaming.api.security.SecurityConfig;
import com.yaostreaming.api.storage.CloudFrontProperties;
import com.yaostreaming.api.user.CurrentUserProvider;
import com.yaostreaming.api.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VideoUploadController.class)
@Import(SecurityConfig.class)
@WithMockUser
class VideoUploadControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VideoUploadService videoUploadService;

	@MockitoBean
	private CurrentUserProvider currentUserProvider;

	@MockitoBean
	private CloudFrontProperties cloudFrontProperties;

	private static final String VALID_BODY = """
			{"title":"Holiday","description":"In Kyoto","filename":"clip.mp4","contentType":"video/mp4"}
			""";

	@Test
	void returnsCreatedWithTheUploadSlot() throws Exception {
		User user = new User("dev@example.com", "Dev User", "hash");
		when(currentUserProvider.require()).thenReturn(user);
		when(videoUploadService.createUpload(eq(user), any()))
				.thenReturn(new UploadResponse(7L, "http://localhost:4566/raw/signed", "uploads/x/clip.mp4"));

		mockMvc.perform(post("/api/videos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.videoId").value(7))
				.andExpect(jsonPath("$.uploadUrl").value("http://localhost:4566/raw/signed"))
				.andExpect(jsonPath("$.sourceKey").value("uploads/x/clip.mp4"));
	}

	@Test
	void rejectsBlankTitle() throws Exception {
		String body = """
				{"title":"  ","filename":"clip.mp4","contentType":"video/mp4"}
				""";

		mockMvc.perform(post("/api/videos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());

		verify(videoUploadService, never()).createUpload(any(), any());
	}

	@Test
	void rejectsMissingFilename() throws Exception {
		String body = """
				{"title":"Holiday","contentType":"video/mp4"}
				""";

		mockMvc.perform(post("/api/videos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());

		verify(videoUploadService, never()).createUpload(any(), any());
	}

	@Test
	void rejectsNonVideoContentType() throws Exception {
		String body = """
				{"title":"Holiday","filename":"payload.sh","contentType":"application/x-sh"}
				""";

		mockMvc.perform(post("/api/videos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());

		verify(videoUploadService, never()).createUpload(any(), any());
	}

}
