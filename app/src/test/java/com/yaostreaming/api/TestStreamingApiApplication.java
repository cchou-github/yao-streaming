package com.yaostreaming.api;

import org.springframework.boot.SpringApplication;

public class TestStreamingApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(StreamingApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
