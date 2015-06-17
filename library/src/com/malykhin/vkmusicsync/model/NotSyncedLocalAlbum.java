package com.malykhin.vkmusicsync.model;

import java.io.File;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class NotSyncedLocalAlbum {

	private static final String TAG = NotSyncedLocalAlbum.class.getSimpleName();
	
	private File dir;
	
	/**
	 * 
	 * @throws NullPointerException
	 */
	public NotSyncedLocalAlbum(File dir) {
		
		if (dir == null) {
			throw new NullPointerException();
		}

		this.dir = dir;
	}

	public File getDir() {
		return dir;
	}

	public String getTitle() {
		return dir.getName();
	}

	@Override
	public boolean equals(Object notSyncedLocalAlbum) {
		
		if (notSyncedLocalAlbum == null) {
			return false;
		}

		if (notSyncedLocalAlbum == this) {
			return true;
		}
		
		return dir.equals(((NotSyncedLocalAlbum) notSyncedLocalAlbum).getDir());
	}
	
	@Override
	public int hashCode() {
		return dir.hashCode();
	}
	
	@Override
	public String toString() {
		return dir.toString();
	}
}
