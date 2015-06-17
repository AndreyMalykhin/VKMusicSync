package com.malykhin.gateway.vk;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class VkGatewayException extends Exception {

	private static final long serialVersionUID = -70549786372950599L;
	protected VkException vkException;
	
	/**
	 * 
	 * @return Null if exception is not caused by VK
	 */
	public VkException getVkException() {
		return vkException;
	}

	public VkGatewayException() {
	}

	public VkGatewayException(String detailMessage) {
		super(detailMessage);
	}

	public VkGatewayException(Throwable throwable) {
		super(throwable);
	}
	
	public VkGatewayException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}
	
	public VkGatewayException(VkException vkException) {
		this.vkException = vkException;
	}

	/**
	 * 
	 * @param detailMessage
	 * @param throwable
	 * @param vkException Can be null
	 */
	public VkGatewayException(String detailMessage, Throwable throwable, VkException vkException) {
		super(detailMessage, throwable);
		this.vkException = vkException;
	}

}
