package com.malykhin.io;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class FileUtils {
	
	public static String sanitizeFilename(String filename) {
		return filename.replaceAll("[\\]\\[\\^/?%*:|\"<>'\\\\]", "");
	}
}
