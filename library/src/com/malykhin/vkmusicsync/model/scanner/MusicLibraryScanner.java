package com.malykhin.vkmusicsync.model.scanner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.malykhin.gateway.vk.VkGatewayException;
import com.malykhin.vkmusicsync.model.MusicDirectory;
import com.malykhin.vkmusicsync.model.scanner.AlbumScanner.AlbumScannerResult;
import com.malykhin.vkmusicsync.model.scanner.TrackScanner.TrackScannerResult;
import com.malykhin.vkmusicsync.util.Analytics;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class MusicLibraryScanner {
	
	public static class MusicLibraryScannerResult {
		
		public final AlbumScannerResult albumScannerResult;
		public final TrackScannerResult trackScannerResult;
		
		public MusicLibraryScannerResult(AlbumScannerResult albumScannerResult, 
				TrackScannerResult trackScannerResult) 
		{
			this.albumScannerResult = albumScannerResult;
			this.trackScannerResult = trackScannerResult;
		}
	}

	private static final String TAG = MusicLibraryScanner.class.getSimpleName();
	
	private static MusicLibraryScanner instance = new MusicLibraryScanner();

	private Map<MusicDirectory, MusicLibraryScannerResult> cachedResults = 
			Collections.synchronizedMap(new HashMap<MusicDirectory, MusicLibraryScannerResult>());
	
	public static MusicLibraryScanner getInstance() {
		return instance;
	}

	/**
	 * 
	 * Finds local tracks and albums that are exists remotely and marks them as synced.
	 *  
	 * If music dir belongs to logged user, finds local tracks and albums that are not exists 
	 * remotely, and marks them as not synced.
	 *  
	 * Finds synced tracks and albums for which local file/dir is missed and marks them as not 
	 * synced. 
     * 
     * Result will be cached.
     * 
     * @throws VkGatewayException
	 */
	public synchronized MusicLibraryScannerResult scan(MusicDirectory musicDir) 
			throws VkGatewayException 
	{
		MusicLibraryScannerResult result = cachedResults.get(musicDir);
		
		if (result != null) {
			return result;
		}

		AlbumScannerResult albumScannerResult = null;
		TrackScannerResult trackScannerResult = null;
		
		try {
			albumScannerResult = new AlbumScanner().scan(musicDir);
			trackScannerResult = new TrackScanner(albumScannerResult.remoteEntities).scan(musicDir);
		} catch (VkGatewayException exception) {
			Analytics.logException(exception);
			throw exception;
		}
		
		result = new MusicLibraryScannerResult(albumScannerResult, trackScannerResult);
		cachedResults.put(musicDir, result);
		
		Analytics.logMusicLibraryScan(
				result.trackScannerResult.remoteEntities.size(), 
				result.trackScannerResult.syncedEntities.getCount(), 
				result.trackScannerResult.notSyncedLocalEntities.size(), 
				result.albumScannerResult.remoteEntities.size(), 
				result.albumScannerResult.syncedEntities.getCount(), 
				result.albumScannerResult.notSyncedLocalEntities.size()
		);
		
		return result;
	}
	
	/**
	 * Clears cached results.
	 */
	public void clearCache() {
		cachedResults.clear();
	}
	
	private MusicLibraryScanner() {}

}
