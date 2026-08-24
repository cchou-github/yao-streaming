package com.yaostreaming.api.video;

import com.yaostreaming.api.storage.CloudFrontCookieSigner;
import com.yaostreaming.api.storage.CloudFrontProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class VideoPageController {

	private final VideoCatalogService videoCatalogService;
	private final CloudFrontProperties cloudFrontProperties;
	private final CloudFrontCookieSigner cloudFrontCookieSigner;

	public VideoPageController(VideoCatalogService videoCatalogService, CloudFrontProperties cloudFrontProperties,
			CloudFrontCookieSigner cloudFrontCookieSigner) {
		this.videoCatalogService = videoCatalogService;
		this.cloudFrontProperties = cloudFrontProperties;
		this.cloudFrontCookieSigner = cloudFrontCookieSigner;
	}

	@GetMapping("/")
	public String catalog(Model model) {
		model.addAttribute("videos", videoCatalogService.listRecent());
		return "videos/catalog";
	}

	@GetMapping("/videos/{id}")
	public String watch(@PathVariable Long id, Model model, HttpServletResponse response) {
		VideoDetail video = videoCatalogService.findDetail(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such video"));
		if (cloudFrontProperties.enabled() && video.isPlayable()) {
			// Deterministic key convention, also used by the submit Lambda and
			// MediaConvertJobSettings - not read off VideoDetail, which (in the
			// CloudFront-enabled branch) already holds the final playback URL,
			// not the bare key this needs.
			String playbackKey = "videos/" + id + "/master.m3u8";
			cloudFrontCookieSigner.cookiesFor(playbackKey)
					.forEach(cookie -> response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString()));
		}
		model.addAttribute("video", video);
		return "videos/watch";
	}

}
