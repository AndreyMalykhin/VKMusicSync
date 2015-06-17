package com.malykhin.io;

import java.io.IOException;

public class NotWritableDirectoryException extends IOException {

	private static final long serialVersionUID = -374963710507463908L;

	public NotWritableDirectoryException(String detailMessage) {
		super(detailMessage);
	}
}
