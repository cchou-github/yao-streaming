package com.yaostreaming.api.video;

import com.yaostreaming.api.user.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
public class VideoUploadController {

	private final VideoUploadService videoUploadService;

	private final CurrentUserProvider currentUserProvider;

	public VideoUploadController(VideoUploadService videoUploadService,
			CurrentUserProvider currentUserProvider) {
		this.videoUploadService = videoUploadService;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UploadResponse createUpload(@Valid @RequestBody UploadRequest request) {
		return videoUploadService.createUpload(currentUserProvider.require(), request);
	}

}
