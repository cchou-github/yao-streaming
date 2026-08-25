package com.yaostreaming.api.transcode;

public class TranscodeException extends RuntimeException {

	public TranscodeException(String message) {
		super(message);
	}

	public TranscodeException(String message, Throwable cause) {
		super(message, cause);
	}

}
