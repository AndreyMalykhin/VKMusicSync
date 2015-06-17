package com.malykhin.vkmusicsync.model.track;

import java.io.File;

import org.apache.commons.io.FilenameUtils;


/**
 * 
 * @author Andrey Malykhin
 *
 */
public class NotSyncedLocalTrack {

	private static final String TAG = NotSyncedLocalTrack.class.getSimpleName();
	
	private File file;
	
	/**
	 * 
	 * @throws NullPointerException
	 */
	public NotSyncedLocalTrack(File file) {
		
		if (file == null) {
			throw new NullPointerException();
		}

		this.file = file;
	}
	
	public boolean isSyncable(File musicDir, 
			LocalToRemoteSyncableTrackCollection localToRemoteSyncableTracks) 
	{
		File albumDir = file.getParentFile();
		String album = albumDir.equals(musicDir) ? null : albumDir.getName();
		
		return localToRemoteSyncableTracks.getByAlbumAndFilename(album, file.getName()) != null;
	}

	public File getFile() {
		return file;
	}

	public String getAlbum(File musicDir) {
		File parentDir = file.getParentFile();
		return musicDir.equals(parentDir) ? null : parentDir.getName();
	}
	
	/**
	 * 
	 * @return Artist + title
	 */
	public String getFullTitle() {
		return FilenameUtils.getBaseName(file.getName());
	}
	
	@Override
	public boolean equals(Object o) {
		
		if (o == null) {
			return false;
		}
		
		if (o == this) {
			return true;
		}
		
		return file.equals(((NotSyncedLocalTrack) o).getFile());
	}
	
	@Override
	public int hashCode() {
		return file.hashCode();
	}
	
	@Override
	public String toString() {
		return file.toString();
	}

}
