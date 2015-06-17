package com.malykhin.vkmusicsync.model.track;

import android.text.TextUtils;

import com.malykhin.orm.DomainModelCollection;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class LocalToRemoteSyncableTrackCollection extends
		DomainModelCollection<LocalToRemoteSyncableTrack> 
{
	public LocalToRemoteSyncableTrackCollection() {
		super();
	}

	public LocalToRemoteSyncableTrackCollection(int capacity) {
		super(capacity);
	}

	/**
	 * 
	 * @return Null if not found
	 */
	public LocalToRemoteSyncableTrack getByAlbumAndFilename(String album, String filename) {
		
		for (LocalToRemoteSyncableTrack item : getItems()) {
			
			if (TextUtils.equals(item.getAlbum(), album) 
				&& TextUtils.equals(item.getFilename(), filename)) 
			{
				return item;
			}
		}
		
		return null;
	}
}
