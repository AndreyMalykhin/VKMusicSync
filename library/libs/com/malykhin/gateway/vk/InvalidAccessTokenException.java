package com.malykhin.gateway.vk;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class InvalidAccessTokenException extends VkException {

	private static final long serialVersionUID = 2399003011665454910L;

	public InvalidAccessTokenException() {
	}

	public InvalidAccessTokenException(String detailMessage) {
		super(detailMessage);
	}

	public InvalidAccessTokenException(Throwable throwable) {
		super(throwable);
	}

	public InvalidAccessTokenException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

}
