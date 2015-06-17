package com.malykhin.vkmusicsync;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class UpgradeException extends Exception {

	private static final long serialVersionUID = 5359599006183141496L;

	public UpgradeException() {
	}

	public UpgradeException(String detailMessage) {
		super(detailMessage);
	}

	public UpgradeException(Throwable throwable) {
		super(throwable);
	}

	public UpgradeException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

}
