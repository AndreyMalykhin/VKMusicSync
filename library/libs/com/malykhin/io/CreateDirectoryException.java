package com.malykhin.io;

import java.io.IOException;

public class CreateDirectoryException extends IOException {

	private static final long serialVersionUID = 1641717579826399795L;
	
	public CreateDirectoryException(String detailMessage) {
		super(detailMessage);
	}
}
