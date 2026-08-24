package com.yaostreaming.api.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

	/** The POST is handled by Spring Security's filter, not by a controller. */
	@GetMapping("/login")
	public String login() {
		return "login";
	}

}
