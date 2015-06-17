package com.malykhin.gateway.vk;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class VkException extends Exception {

	private static final long serialVersionUID = -1711419321193887333L;

	public VkException() {
	}

	public VkException(String detailMessage) {
		super(detailMessage);
	}

	public VkException(Throwable throwable) {
		super(throwable);
	}

	public VkException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

}
