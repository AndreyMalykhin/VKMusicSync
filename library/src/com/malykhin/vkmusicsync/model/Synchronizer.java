package com.malykhin.vkmusicsync.model;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v22Tag;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.text.TextUtils;

import com.malykhin.gateway.vk.Album;
import com.malykhin.gateway.vk.Track;
import com.malykhin.gateway.vk.VkGateway;
import com.malykhin.io.CreateDirectoryException;
import com.malykhin.io.OutputStreamWithProgress.ProgressListener;
import com.malykhin.orm.DomainModelCollection;
import com.malykhin.util.CommonUtils;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.VkGatewayHelper;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.model.scanner.TrackScanner;
import com.malykhin.vkmusicsync.model.track.LocalToRemoteSyncableTrack;
import com.malykhin.vkmusicsync.model.track.LocalToRemoteSyncableTrackMapper;
import com.malykhin.vkmusicsync.model.track.NotSyncedLocalTrack;
import com.malykhin.vkmusicsync.model.track.RemoteToLocalSyncableTrack;
import com.malykhin.vkmusicsync.model.track.RemoteToLocalSyncableTrackMapper;
import com.malykhin.vkmusicsync.model.track.SyncedTrack;
import com.malykhin.vkmusicsync.model.track.SyncedTrackMapper;
import com.malykhin.vkmusicsync.util.Analytics;

/**
 * TODO fix bug: if both album and track within it were renamed remotely, then this track will be 
 * downloaded again with new name
 * 
 * @author Andrey Malykhin
 *
 */
public class Synchronizer {

	public interface OnDoneListener {
		
		/**
		 * 
		 * @param anyChanges True if any changes were made
		 */
		public void onDone(boolean anyChanges);
	}

	public interface OnStartProcessingTrackListener {
		public void onStartProcessingTrack(String name);
	}
	
	public interface OnStartProcessingAlbumListener {
		public void onStartProcessingAlbum(String title);
	}
	
	public interface OnSearchingDifferenesListener {
		public void onSearchingDifferences();
	}

	public interface OnErrorListener {
		public void onError(String errorMsg);
	}
	
	public interface OnRunListener {
		public void onRun();
	}
	
	public interface OnStopListener {
		public void onStop();
	}
	
	public interface OnProcessTrackProgressUpdateListener {
		public void onProcessTrackProgressUpdate(String trackTitle, long processedBytesCount, 
				long totalBytesCount);
	}

	public enum Status {
		
		/**
		 * Indicates that {@link Synchronizer#run(long)} was not called yet
		 */
		IDLE,
		
		/**
		 * Indicates that {@link Synchronizer#run(long)} is executing
		 */
		RUNNING,
		
		/**
		 * Indicates that {@link Synchronizer#run(long)} has finished
		 */
		DONE
	}

	private abstract static class AbstractEntityProcessor<T> {
		
		/**
		 * Count of errors in row, after which processor will stop. 
		 */
		protected static final int COUNT_OF_ERRORS_IN_ROW_TO_STOP = 10;
		
		/**
		 * Count of retries to process same entity, after which processor will skip that entity.
		 */
		protected static final int COUNT_OF_RETRIES_FOR_SAME_ENTITY = 3;
		
		/**
		 * Time, for which processor will wait for internet conection to be restored, if it was 
		 * lost. In milliseconds.
		 */
		protected static final int TIME_TO_WAIT_FOR_CONNECTION = 120000;
		
		/**
		 * Frequency, with which processor will check for internet connection to be restored, if it 
		 * was lost. In milliseconds. 
		 */
		protected static final int CONNECTION_CHECK_PERIOD = 10000;
		
		protected Synchronizer synchronizer;
		protected LinkedList<T> entities;
		
		/**
		 * Key - entity, value - count of failed processings for that entity.
		 */
		protected Map<T, Integer> countOfFailedProcessAttempts;
		
		private final static String TAG = AbstractEntityProcessor.class.getSimpleName();

		public AbstractEntityProcessor(Synchronizer synchronizer) {
			this.synchronizer = synchronizer;
		}
		
		public void process(LinkedList<T> entities) {
			this.entities = entities;
			countOfFailedProcessAttempts = new HashMap<T, Integer>();
			
			T currentEntity = null;
			int countOfErrorsInRow = 0;
			
			while ((currentEntity = entities.poll()) != null) {
				
				if (synchronizer.stopped) {
					break;
				}

				String currentEntityTitle = getEntityTitle(currentEntity);
				notifyAboutStartProcessingEntity(currentEntityTitle);
				
				try {
					processEntity(currentEntity);
					countOfErrorsInRow = 0;
				} catch (Exception exception) {
					Log.e(TAG, null, exception);
					Analytics.logException(exception);
					
					if (synchronizer.stopped) {
						break;
					}
					
					notifyAboutProcessingError(currentEntityTitle);

					if (!CommonUtils.isInternetEnabled(synchronizer.context)) {
						boolean isConnectionRestored = false;
						int countOfChecksForConnection = 
								TIME_TO_WAIT_FOR_CONNECTION / CONNECTION_CHECK_PERIOD;
						
						for (int i = 0; i < countOfChecksForConnection; i++) {
							try {
								Thread.sleep(CONNECTION_CHECK_PERIOD);
							} catch (InterruptedException exception1) {
							}

							if (synchronizer.stopped) {
								break;
							}
							
							if (CommonUtils.isInternetEnabled(synchronizer.context)) {
								isConnectionRestored = true;
								break;
							}
						}
						
						if (!isConnectionRestored) {
							synchronizer.stopped = true;
							break;
						}
					}

					countOfErrorsInRow++;
					
					if (countOfErrorsInRow >= COUNT_OF_ERRORS_IN_ROW_TO_STOP) {
						synchronizer.stopped = true;
						synchronizer.notifyAboutError(synchronizer.context.getString(
								R.string.stopping_sync_because_of_too_many_errors));
						break;
					}
					
					continue;
				}
				
				incrementCountOfProcessedEntities();
			}
		}
		
		abstract protected void incrementCountOfProcessedEntities();
		
		abstract protected void notifyAboutProcessingError(String entityTitle);

		abstract protected void notifyAboutStartProcessingEntity(String entityTitle);

		abstract protected void processEntity(T entity) throws Exception;
		
		abstract protected String getEntityTitle(T entity);
		
		protected void markEntityForRetryProcess(T entity) {
			Integer countOfFailedProcessAttemptsForEntity = 
					countOfFailedProcessAttempts.get(entity);
			
			if (countOfFailedProcessAttemptsForEntity == null) {
				countOfFailedProcessAttemptsForEntity = 1;
			} else {
				countOfFailedProcessAttemptsForEntity++;
			}
			
			countOfFailedProcessAttempts.put(entity, countOfFailedProcessAttemptsForEntity);
			
			if (countOfFailedProcessAttemptsForEntity < COUNT_OF_RETRIES_FOR_SAME_ENTITY) {
				
				// process entity again
				entities.offer(entity);
			}
		}
	}
	
	/**
	 * @param <T> Album
	 */
	private abstract static class AbstractAlbumsProcessor<T> extends AbstractEntityProcessor<T> {

		public AbstractAlbumsProcessor(Synchronizer synchronizer) {
			super(synchronizer);
		}

		@Override
		protected void notifyAboutProcessingError(String entityTitle) {
			synchronizer.notifyAboutError(synchronizer.context.getString(
					R.string.error_while_syncing_album, entityTitle));
		}

		@Override
		protected void notifyAboutStartProcessingEntity(String entityTitle) {
			synchronizer.notifyAboutStartProcessingAlbum(entityTitle);
		}

		@Override
		protected void incrementCountOfProcessedEntities() {
			synchronizer.countOfProcessedAlbums++;
		}
	}
	
	private static class AddLocalAlbumsRemotelyProcessor 
		extends AbstractAlbumsProcessor<NotSyncedLocalAlbum> 
	{
		private final static String TAG = AddLocalAlbumsRemotelyProcessor.class.getSimpleName();
		
		public AddLocalAlbumsRemotelyProcessor(Synchronizer synchronizer) {
			super(synchronizer);
		}

		@Override
		protected void processEntity(NotSyncedLocalAlbum album) throws Exception {
			Log.d(TAG, "processEntity(); album=" + album);
	
			if (!album.getDir().exists()) {
				return;
			}
			
			Album remoteAlbum = addLocalAlbumRemotely(album);
			markAlbumAsSynced(remoteAlbum);
		}

		private Album addLocalAlbumRemotely(NotSyncedLocalAlbum album) throws Exception {
			String albumTitle = album.getTitle();
			long albumId = 0;
			
			try {
				albumId = synchronizer.vkGateway.addAlbum(albumTitle);
			} catch (Exception exception) {
				markEntityForRetryProcess(album);
				throw exception;
			}
			
			return new Album(albumId, synchronizer.owner.getId(), albumTitle, 
					synchronizer.owner.isGroup());
		}
		
		private void markAlbumAsSynced(Album remoteAlbum) throws SQLException {
			SyncedAlbum syncedAlbum = new SyncedAlbum();
			syncedAlbum.setTitle(remoteAlbum.title)
				.setOwner(synchronizer.owner)
				.setDirName(generateAlbumDirName(remoteAlbum.title))
				.setId(remoteAlbum.id);
			
			SyncedAlbumMapper.getInstance().add(syncedAlbum);
		}
	
		@Override
		protected String getEntityTitle(NotSyncedLocalAlbum localAlbum) {
			return localAlbum.getTitle();
		}
	
	}
	
	private static class AddRemoteAlbumsLocally extends AbstractAlbumsProcessor<Album> {

		private static final String TAG = AddRemoteAlbumsLocally.class.getSimpleName();
		
		public AddRemoteAlbumsLocally(Synchronizer synchronizer) {
			super(synchronizer);
		}

		@Override
		protected void processEntity(Album remoteAlbum) throws Exception {
			Log.d(TAG, "processEntity(); remoteAlbum=" + remoteAlbum);

			SyncedAlbum correspondingSyncedAlbum = synchronizer.syncedAlbums.get(remoteAlbum.id);
			
			if (correspondingSyncedAlbum != null) {
				File albumDir = correspondingSyncedAlbum.getDir();
				
				if (!albumDir.exists() && !albumDir.mkdir()) {
					markEntityForRetryProcess(remoteAlbum);
					throw new CreateDirectoryException(albumDir.toString());
				}
				
			} else {
				correspondingSyncedAlbum = new SyncedAlbum();
				correspondingSyncedAlbum.setId(remoteAlbum.id)
					.setDirName(generateAlbumDirName(remoteAlbum.title))
					.setOwner(synchronizer.owner)
					.setTitle(remoteAlbum.title);
				File albumDir = correspondingSyncedAlbum.getDir();
				
				SyncedAlbumMapper syncedAlbumMapper = SyncedAlbumMapper.getInstance();
				syncedAlbumMapper.beginTransaction();
				
				try {
					syncedAlbumMapper.add(correspondingSyncedAlbum);
					
					if (!albumDir.exists() && !albumDir.mkdir()) {
						throw new CreateDirectoryException(albumDir.toString());
					}
					
					syncedAlbumMapper.setTransactionSuccessful();
				} catch (Exception exception) {
					markEntityForRetryProcess(remoteAlbum);
					throw exception;
				} finally {
					syncedAlbumMapper.endTransaction();
				}
			}
		}

		@Override
		protected String getEntityTitle(Album album) {
			return album.title;
		}
		
	}
	
	private static class UpdateLocalAlbumsProcessor extends AbstractAlbumsProcessor<Album> {
		private final static String TAG = UpdateLocalAlbumsProcessor.class.getSimpleName();
		
		public UpdateLocalAlbumsProcessor(Synchronizer synchronizer) {
			super(synchronizer);
		}

		@Override
		protected void processEntity(Album remoteAlbum) throws Exception {
			Log.d(TAG, "processEntity(); remoteAlbum=" + remoteAlbum);
			
			SyncedAlbum syncedAlbum = synchronizer.syncedAlbums.get(remoteAlbum.id);
			
			if (syncedAlbum == null) {
				return;
			}
			
			File oldAlbumDir = syncedAlbum.getDir();
			syncedAlbum.setDirName(generateAlbumDirName(remoteAlbum.title))
				.setTitle(remoteAlbum.title);
			File newAlbumDir = syncedAlbum.getDir();
			
			SyncedAlbumMapper syncedAlbumMapper = SyncedAlbumMapper.getInstance();
			syncedAlbumMapper.beginTransaction();
			
			try {
				syncedAlbumMapper.update(syncedAlbum);
				
				if (oldAlbumDir.exists() 
					&& !newAlbumDir.exists() 
					&& !oldAlbumDir.renameTo(newAlbumDir)) 
				{
					throw new IOException("Failed to rename dir " + oldAlbumDir + " to " + 
							newAlbumDir);
				}
				
				syncedAlbumMapper.setTransactionSuccessful();
			} catch (Exception exception) {
				markEntityForRetryProcess(remoteAlbum);
				throw exception;
			} finally {
				syncedAlbumMapper.endTransaction();
			}

		}

		@Override
		protected String getEntityTitle(Album album) {
			return album.title;
		}
	}
	
	/**
	 * 
	 * @param <T> Track
	 */
	abstract private static class AbstractTracksProcessor<T> extends AbstractEntityProcessor<T> {
		
		public AbstractTracksProcessor(Synchronizer synchronizer) {
			super(synchronizer);
		}

		/**
		 * How often progress listeners will be notified about progress update. In seconds.
		 */
		protected static final int PROGRESS_UPDATE_FREQUENCY = 2;
		
		/**
		 * In seconds
		 */
		protected static final int CONNECTION_TIMEOUT_TIME = 30;

		private final static String TAG = AbstractTracksProcessor.class.getSimpleName();

		@Override
		protected void notifyAboutProcessingError(String entityTitle) {
			synchronizer.notifyAboutError(synchronizer.context.getString(
					R.string.error_while_syncing_track, entityTitle));
		}

		@Override
		protected void notifyAboutStartProcessingEntity(String entityTitle) {
			synchronizer.notifyAboutStartProcessingTrack(entityTitle);
		}

		@Override
		protected void incrementCountOfProcessedEntities() {
			synchronizer.countOfProcessedTracks++;
		}
		
		/**
		 * 
		 * @return True if successfully written
		 */
		protected boolean writeMp3Tags(File trackFile, Track remoteTrack) {
			Log.d(TAG, "writeMp3Tags(); remoteTrack=" + remoteTrack.getFullTitle());
			
			if (!trackFile.exists()) {
				return false;
			}
			
			System.gc();
			
			try {
				MP3File mp3File = (MP3File) AudioFileIO.read(trackFile);
				AbstractID3v2Tag id3v2tag = null;
				
				if (mp3File.hasID3v2Tag()) {
					id3v2tag = mp3File.getID3v2Tag();
				} else {
					id3v2tag = new ID3v22Tag();
					mp3File.setID3v2Tag(id3v2tag);
				}
	
				id3v2tag.setField(FieldKey.ARTIST, remoteTrack.artist);
				id3v2tag.setField(FieldKey.TITLE, remoteTrack.title);
				
				Tag tags = mp3File.getTagOrCreateAndSetDefault();
				tags.setField(FieldKey.ARTIST, remoteTrack.artist);
				tags.setField(FieldKey.TITLE, remoteTrack.title);
	
				mp3File.save();
			} catch (Exception exception) {
				Log.e(TAG, null, exception);
				
				synchronizer.notifyAboutError(synchronizer.context.getString(
						R.string.error_while_saving_tag, remoteTrack.getFullTitle()));
				return false;
			}
			
			return true;
		}
	}
	
	private static class AddRemoteTracksLocallyProcessor extends AbstractTracksProcessor<Track> {

		public AddRemoteTracksLocallyProcessor(Synchronizer synchronizer) {
			super(synchronizer);
		}

		private final static String TAG = AddRemoteTracksLocallyProcessor.class.getSimpleName();
		
		/**
		 * Key - filename, value - track file
		 */
		private Map<String, File> localTrackFiles;
		
		@Override
		public void process(LinkedList<Track> entities) {
			
			if (entities.size() == 0) {
				return;
			}
			
			Collection<File> localTrackFiles = 
					TrackScanner.getLocalTrackFiles(synchronizer.musicDir);
			this.localTrackFiles = new HashMap<String, File>(localTrackFiles.size());
			
			for (File localTrackFile : localTrackFiles) {
				this.localTrackFiles.put(localTrackFile.getName(), localTrackFile);
			}
			
			super.process(entities);
		}

		@Override
		protected String getEntityTitle(Track track) {
			return track.getFullTitle();
		}

		@Override
		protected void processEntity(Track remoteTrack) throws Exception {
			Log.d(TAG, "processEntity(); remoteTrack=" + remoteTrack);

			SyncedTrack correspondingSyncedTrack = synchronizer.syncedTracks.get(remoteTrack.id);
			File trackFile = null;
			
			if (correspondingSyncedTrack != null) {
				trackFile = correspondingSyncedTrack.getFile();
				
				if (!trackFile.exists()) {
					
					try {
						downloadTrack(remoteTrack, trackFile);
					} catch (Exception exception) {
						markEntityForRetryProcess(remoteTrack);
						throw exception;
					}
					
					writeMp3Tags(trackFile, remoteTrack);
				}
				
			} else {
				correspondingSyncedTrack = new SyncedTrack();
				correspondingSyncedTrack.setAlbumId(remoteTrack.albumId)
					.setOwner(synchronizer.owner)
					.setArtist(remoteTrack.artist)
					.setDuration(remoteTrack.duration)
					.setFilename(generateTrackFilename(remoteTrack))
					.setId(remoteTrack.id)
					.setTitle(remoteTrack.title);
				trackFile = correspondingSyncedTrack.getFile();
				
				SyncedTrackMapper syncedTrackMapper = 
						SyncedTrackMapper.getInstance();
				syncedTrackMapper.beginTransaction();
				
				try {
					syncedTrackMapper.add(correspondingSyncedTrack);
					
					if (!trackFile.exists()) {
						File existingTrackFile = 
								localTrackFiles.get(correspondingSyncedTrack.getFilename());
						
						// if track file doesnt exists in music dir or in any dir within it
						if (existingTrackFile == null) {
							downloadTrack(remoteTrack, trackFile);
							writeMp3Tags(trackFile, remoteTrack);
						} else if (!existingTrackFile.equals(trackFile) 
								   && existingTrackFile.exists()) 
						{
							FileUtils.moveFile(existingTrackFile, trackFile);
						}
					}
					
					syncedTrackMapper.setTransactionSuccessful();
				} catch (Exception exception) {
					markEntityForRetryProcess(remoteTrack);
					throw exception;
				} finally {
					syncedTrackMapper.endTransaction();
				}
				
				synchronizer.syncedTracks.add(correspondingSyncedTrack);
			}
		}
		
		/**
		 * 
		 * @throws MalformedURLException
		 * @throws IOException
		 */
		private void downloadTrack(Track track, File destinationFile) 
				throws MalformedURLException, IOException
		{
			Log.d(TAG, "downloadTrack(); track=" + track);
			
			URLConnection connection = new URL(track.url).openConnection();
	        connection.setConnectTimeout(CONNECTION_TIMEOUT_TIME * 1000);
	        connection.setReadTimeout(CONNECTION_TIMEOUT_TIME * 1000);
	        InputStream inputStream = connection.getInputStream();
	        int trackSize = connection.getContentLength();
	        String trackTitle = track.getFullTitle();
	        
	    	try {
	            FileOutputStream outputStream = FileUtils.openOutputStream(destinationFile);
	            
	            try {
	                byte[] buffer = new byte[4096];
	                int bytesReadCount = 0;
	                int downloadedBytesCount = 0;
	                long currentTime = System.currentTimeMillis();
	                long lastTickTime = currentTime;
	                
	                while (-1 != (bytesReadCount = inputStream.read(buffer))) {
	                	
	                	if (synchronizer.stopped) {
	                		destinationFile.delete();
	                		return;
	                	}
	                	
	                    outputStream.write(buffer, 0, bytesReadCount);
	                    downloadedBytesCount += bytesReadCount;
	                    currentTime = System.currentTimeMillis();
	                    
	                    if (currentTime > lastTickTime + PROGRESS_UPDATE_FREQUENCY * 1000) {
	                    	synchronizer.notifyAboutProcessTrackProgressUpdate(trackTitle, 
	                    			downloadedBytesCount, trackSize);
	                    	lastTickTime = currentTime;
	                    }
	                }
	                
	            } finally {
	                IOUtils.closeQuietly(outputStream);
	            }
	            
	    	} catch (IOException exception) {
	    		destinationFile.delete();
	        	throw exception;
	        } finally {
	        	IOUtils.closeQuietly(inputStream);
	        }
		}
	}
	
	private static class UpdateLocalTracksProcessor extends AbstractTracksProcessor<Track> {

		private final static String TAG = UpdateLocalTracksProcessor.class.getSimpleName();
		
		public UpdateLocalTracksProcessor(Synchronizer synchronizer) {
			super(synchronizer);
		}

		@Override
		protected void processEntity(Track remoteTrack) throws Exception {
			Log.d(TAG, "processEntity(); remoteTrack=" + remoteTrack);
			
			SyncedTrack syncedTrack = synchronizer.syncedTracks.get(remoteTrack.id);
			
			if (syncedTrack == null) {
				return;
			}
			
			String newTrackFilename = generateTrackFilename(remoteTrack);
			File oldTrackFile = syncedTrack.getFile();
			syncedTrack.setAlbumId(remoteTrack.albumId)
				.setArtist(remoteTrack.artist)
				.setFilename(newTrackFilename)
				.setTitle(remoteTrack.title);
			File newTrackFile = syncedTrack.getFile();
			
			SyncedTrackMapper syncedTrackMapper = SyncedTrackMapper.getInstance();
			syncedTrackMapper.beginTransaction();
			
			try {
				syncedTrackMapper.update(syncedTrack);
				
				if (!oldTrackFile.equals(newTrackFile) && oldTrackFile.exists()) {

					if (newTrackFile.exists()) {
						FileUtils.copyFile(oldTrackFile, newTrackFile);
						oldTrackFile.delete();
					} else {
						FileUtils.moveFile(oldTrackFile, newTrackFile);
					}
				}
				
				syncedTrackMapper.setTransactionSuccessful();
			} catch (Exception exception) {
				markEntityForRetryProcess(remoteTrack);
				throw exception;
			} finally {
				syncedTrackMapper.endTransaction();
			}
			
			writeMp3Tags(newTrackFile, remoteTrack);
		}

		@Override
		protected String getEntityTitle(Track track) {
			return track.getFullTitle();
		}
		
	}
	
	private static class AddLocalTracksRemotelyProcessor 
		extends AbstractTracksProcessor<NotSyncedLocalTrack> 
	{
		private final static String TAG = AddLocalTracksRemotelyProcessor.class.getSimpleName();
		
		public AddLocalTracksRemotelyProcessor(Synchronizer synchronizer) {
			super(synchronizer);
		}

		@Override
		protected void processEntity(NotSyncedLocalTrack localTrack) throws Exception {
			Log.d(TAG, "processTrack(); localTrack=" + localTrack);

			if (!localTrack.getFile().exists()) {
				return;
			}
			
			Track remoteTrack = addLocalTrackRemotely(localTrack);
			File syncedTrackLocalFile = markTrackAsSynced(localTrack, remoteTrack);
			
			try {
				setRemoteTrackSyncable(remoteTrack.id);
			} catch (SQLException exception) {
				Log.e(TAG, null, exception);
			}
			
			writeMp3Tags(syncedTrackLocalFile, remoteTrack);
		}

		private void setRemoteTrackSyncable(long id) throws SQLException {
			RemoteToLocalSyncableTrack syncableTrack = new RemoteToLocalSyncableTrack();
			syncableTrack.setId(id)
				.setOwner(synchronizer.owner);
			RemoteToLocalSyncableTrackMapper.getInstance().add(syncableTrack);
		}

		private Track addLocalTrackRemotely(NotSyncedLocalTrack localTrack) throws Exception {
			MP3File mp3File = null;
			
			try {
				mp3File = (MP3File) AudioFileIO.read(localTrack.getFile());
			} catch (Exception exception) {
				Log.e(TAG, null, exception);
			}

			String artist = null;
			String title = null;
			
			if (mp3File != null) {
				Tag tags = mp3File.getTagOrCreateAndSetDefault();
				artist = tags.getFirst(FieldKey.ARTIST);
				title = tags.getFirst(FieldKey.TITLE);
				mp3File = null;
				tags = null;
			}
			
			final String localTrackFullTitle = localTrack.getFullTitle();
			
			if (TextUtils.isEmpty(artist)) {
				artist = localTrackFullTitle;
			}
				
			if (TextUtils.isEmpty(title)) {
				title = localTrackFullTitle;
			}
			
			final long localTrackSize = localTrack.getFile().length();
			
			ProgressListener uploadProgressListener = new ProgressListener() {
				
				@Override
				public void onProgressChanged(long transferredBytesCount) {
					synchronizer.notifyAboutProcessTrackProgressUpdate(localTrackFullTitle, 
							transferredBytesCount, localTrackSize);
				}

				@Override
				public int getNotificationFrequency() {
					return PROGRESS_UPDATE_FREQUENCY * 1000;
				}
				
			}; 
			
			Track remoteTrack = null;
			LocalToRemoteSyncableTrackMapper localToRemoteSyncableTrackMapper = 
					LocalToRemoteSyncableTrackMapper.getInstance();
			File albumDir = localTrack.getFile().getParentFile();
			String albumTitle = albumDir.equals(synchronizer.musicDir) ? null : albumDir.getName();
			
			localToRemoteSyncableTrackMapper.beginTransaction();
			
			try {
				localToRemoteSyncableTrackMapper.deleteByOwnerAndAlbumAndFilename(
						synchronizer.owner, albumTitle, localTrack.getFile().getName());
				remoteTrack = synchronizer.vkGateway.addTrack(localTrack.getFile(), artist, 
						title, uploadProgressListener);
				
				localToRemoteSyncableTrackMapper.setTransactionSuccessful();
			} catch (Exception exception) {
				markEntityForRetryProcess(localTrack);
				throw exception;
			} finally {
				localToRemoteSyncableTrackMapper.endTransaction();
			}
			
			SyncedAlbum album = null;
			
			if (albumTitle != null) {
				album = SyncedAlbumMapper.getInstance().getOneByOwnerAndDirName(
						synchronizer.owner, albumTitle);
			}
			
			if (album != null) {
				synchronizer.vkGateway.moveTracksToAlbum(new long[]{remoteTrack.id}, 
						album.getId());
				remoteTrack = new Track(remoteTrack.id, remoteTrack.artist, remoteTrack.title, 
						remoteTrack.duration, remoteTrack.url, album.getId());
			}
			
			return remoteTrack;
		}

		private File markTrackAsSynced(NotSyncedLocalTrack localTrack, Track remoteTrack) 
				throws SQLException, IOException 
		{
			String trackFilename = generateTrackFilename(remoteTrack);
			File syncedTrackLocalFile = new File(FilenameUtils.getFullPath(
					localTrack.getFile().getAbsolutePath()) + trackFilename);
			SyncedTrack syncedTrack = new SyncedTrack();
			syncedTrack.setAlbumId(remoteTrack.albumId)
				.setOwner(synchronizer.owner)
				.setArtist(remoteTrack.artist)
				.setDuration(remoteTrack.duration)
				.setFilename(trackFilename)
				.setId(remoteTrack.id)
				.setTitle(remoteTrack.title);
			
			SyncedTrackMapper syncedTrackMapper = SyncedTrackMapper.getInstance();
			syncedTrackMapper.beginTransaction();
			
			try {
				syncedTrackMapper.add(syncedTrack);
				
				if (syncedTrackLocalFile.exists()) {
					
					if (!localTrack.getFile().equals(syncedTrackLocalFile)) {
						FileUtils.copyFile(localTrack.getFile(), syncedTrackLocalFile);
						localTrack.getFile().delete();
					}
					
				} else if (!localTrack.getFile().renameTo(syncedTrackLocalFile)) {
					throw new IOException("Failed to rename file " + localTrack.getFile() + 
							" to " + syncedTrackLocalFile);
				}
				
				syncedTrackMapper.setTransactionSuccessful();
			} finally {
				syncedTrackMapper.endTransaction();
			}
			
			return syncedTrackLocalFile;
		}

		@Override
		protected String getEntityTitle(NotSyncedLocalTrack track) {
			return track.getFullTitle();
		}

	}
	
	private static final String TAG = Synchronizer.class.getSimpleName();
	private static final int MAX_FILE_NAME_LENGTH = 128;
	
	private OnStopListener onStopListener;
	private OnErrorListener onErrorListener;
	private Set<OnDoneListener> onDoneListeners = 
			Collections.synchronizedSet(new HashSet<OnDoneListener>(2));
	private Set<OnSearchingDifferenesListener> onSearchingDifferenesListeners = 
			Collections.synchronizedSet(new HashSet<OnSearchingDifferenesListener>(2));
	private Set<OnStartProcessingTrackListener> onStartProcessingTrackListeners = 
			Collections.synchronizedSet(new HashSet<OnStartProcessingTrackListener>(2));
	private Set<OnStartProcessingAlbumListener> onStartProcessingAlbumListeners = 
			Collections.synchronizedSet(new HashSet<OnStartProcessingAlbumListener>(2));
	private Set<OnProcessTrackProgressUpdateListener> onProcessTrackProgressUpdateListeners =
			Collections.synchronizedSet(new HashSet<OnProcessTrackProgressUpdateListener>(2));
	private Set<OnRunListener> onRunListeners = Collections.synchronizedSet(
			new HashSet<OnRunListener>(2));
	private Context context;
	private Status status;
	private File musicDir;
	private VkGateway vkGateway;
	private MusicOwner owner;
	private int countOfTracksToProcess = 0;
	private int countOfProcessedTracks = 0;
	private int countOfAlbumsToProcess = 0;
	private int countOfProcessedAlbums = 0;
	private Thread callerThread;
	private WifiLock wifiLock;
	private boolean anyChanges = false;
	private boolean stopped = false;
	private boolean searchingDifferences = false;
	private DomainModelCollection<SyncedTrack> syncedTracks;
	private DomainModelCollection<SyncedAlbum> syncedAlbums;
	private LinkedList<Track> remoteTracksToAddLocally;
	private LinkedList<Track> remoteTracksToUpdateLocally;
	private LinkedList<NotSyncedLocalTrack> localTracksToAddRemotely;
	private LinkedList<Album> remoteAlbumsToAddLocally;
	private LinkedList<Album> remoteAlbumsToUpdateLocally;
	private LinkedList<NotSyncedLocalAlbum> localAlbumsToAddRemotely;
	
	public static String generateTrackFilename(Track remoteTrack) {
		String sanitizedTrackName = com.malykhin.io.FileUtils.sanitizeFilename(
				remoteTrack.getFullTitle());
		
		if (sanitizedTrackName.length() > MAX_FILE_NAME_LENGTH) {
			sanitizedTrackName = sanitizedTrackName.substring(0, MAX_FILE_NAME_LENGTH);
		}
		
		String sanitizedExtension = FilenameUtils.getExtension(remoteTrack.url)
				.replaceAll("\\?.*", "");
		String newFilename = sanitizedTrackName + "." + sanitizedExtension;
		
		return newFilename;
	}
	
	public static String generateAlbumDirName(String albumTitle) {
		String sanitizedDirName = com.malykhin.io.FileUtils.sanitizeFilename(albumTitle);
		
		if (sanitizedDirName.length() > MAX_FILE_NAME_LENGTH) {
			sanitizedDirName = sanitizedDirName.substring(0, MAX_FILE_NAME_LENGTH);
		}
		
		return sanitizedDirName;
	}
	
	public Synchronizer(Context context) {
		this.context = context;
		status = Status.IDLE;
	}

	public boolean isSearchingDifferences() {
		return searchingDifferences;
	}
	
	public Status getStatus() {
		return status;
	}

	/**
	 * 
	 * @return Overall percentage of sync progress.
	 */
	public int getProgress() {
		
		if (countOfTracksToProcess == 0 && countOfAlbumsToProcess == 0) {
			return 0;
		}
		
		int countOfProcessedEntries = countOfProcessedTracks + countOfProcessedAlbums;
		int countOfEntriesToProcess = countOfTracksToProcess + countOfAlbumsToProcess;
		
		return (int) (((float) countOfProcessedEntries / countOfEntriesToProcess) * 100);
	}

	public void addOnProcessTrackProgressUpdateListener(
			OnProcessTrackProgressUpdateListener listener) 
	{
		onProcessTrackProgressUpdateListeners.add(listener);
	}

	public void deleteOnProcessTrackProgressUpdateListener(
			OnProcessTrackProgressUpdateListener listener) 
	{
		onProcessTrackProgressUpdateListeners.remove(listener);
	}
	
	public void setOnErrorListener(OnErrorListener listener) {
		onErrorListener = listener;
	}
	
	public void setOnStopListener(OnStopListener listener) {
		
		if (listener != null && stopped) {
			listener.onStop();
			return;
		}
		
		onStopListener = listener;
	}
	
	public void addOnDoneListener(OnDoneListener listener) {

		if (onDoneListeners.add(listener) && status == Status.DONE) {
			listener.onDone(anyChanges);
		}
	}
	
	public void deleteOnDoneListener(OnDoneListener listener) {
		onDoneListeners.remove(listener);
	}

	public void addOnRunListener(OnRunListener listener) {

		if (onRunListeners.add(listener) && status == Status.RUNNING) {
			listener.onRun();
		}
	}

	public void deleteOnRunListener(OnRunListener listener) {
		onRunListeners.remove(listener);
	}

	public void addOnStartProcessingTrackListener(OnStartProcessingTrackListener listener) {
		onStartProcessingTrackListeners.add(listener);
	}

	public void deleteOnStartProcessingTrackListener(OnStartProcessingTrackListener listener) {
		onStartProcessingTrackListeners.remove(listener);
	}
	
	public void addOnStartProcessingAlbumListener(OnStartProcessingAlbumListener listener) {
		onStartProcessingAlbumListeners.add(listener);
	}

	public void deleteOnStartProcessingAlbumListener(OnStartProcessingAlbumListener listener) {
		onStartProcessingAlbumListeners.remove(listener);
	}
	
	public void addOnSearchingDifferencesListener(OnSearchingDifferenesListener listener) {
		onSearchingDifferenesListeners.add(listener);
	}

	public void deleteOnSearchingDifferencesListener(OnSearchingDifferenesListener listener) {
		onSearchingDifferenesListeners.remove(listener);
	}

	public void stop() {
		Log.d(TAG, "stop()");
		
		stopped = true;
		Analytics.logSyncStop();
		notifyAboutStop();
		
		if (vkGateway != null) {
			vkGateway.abortAllRequests(callerThread);
			vkGateway = null;
		}
	}

	/**
	 * 
	 * @throws NullPointerException
	 * @throws IllegalStateException If trying to run already runned instance
	 */
	public synchronized void run(MusicOwner owner) {
		Log.d(TAG, "run()");

		if (status != Status.IDLE) {
			throw new IllegalStateException("Already runned. Create new instance and run it");
		}
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		status = Status.RUNNING;
		notifyAboutRun();
		
		callerThread = Thread.currentThread();
		this.owner = owner;
		
		if (!CommonUtils.isInternetEnabled(context)) {
			notifyAboutError(context.getString(R.string.please_enable_internet));
			done();
			return;
		}
		
		try {
			musicDir = Preferences.getInstance().getCurrentOrAddDefaultMusicDir(this.owner)
					.getDirectory();
		} catch (Exception exception) {
			Log.e(TAG, null, exception);
			
			notifyAboutError(context.getString(R.string.error_while_creating_directory_for_music));
			done();
			
			return;
		}

		long[] remoteToLocalSyncableTrackIds = RemoteToLocalSyncableTrackMapper.getInstance()
				.getAllIdsByOwner(this.owner);
		vkGateway = VkGatewayHelper.getGateway();
		Track[] remoteTracks = null;
		
		try {
			remoteTracks = vkGateway.getTracks(this.owner.getId(), this.owner.isGroup(),
					remoteToLocalSyncableTrackIds);
		} catch (Exception exception) {
			Log.e(TAG, null, exception);
			Analytics.logException(exception);
			
			notifyAboutError(context.getString(R.string.error_while_communicating_with_vkontake));
			done();

			return;
		}
		
		remoteToLocalSyncableTrackIds = null;
		
		if (stopped) {
			done();
			return;
		}
		
		List<Album> remoteAlbums = null;
		
		try {
			remoteAlbums = vkGateway.getAlbums(this.owner.getId(), this.owner.isGroup());
		} catch (Exception exception) {
			Log.e(TAG, null, exception);
			Analytics.logException(exception);
			
			notifyAboutError(context.getString(R.string.error_while_communicating_with_vkontake));
			done();

			return;
		}
		
		syncedTracks = SyncedTrackMapper.getInstance().getAllByOwner(this.owner);
		syncedAlbums = SyncedAlbumMapper.getInstance().getAllByOwner(this.owner);
		DomainModelCollection<LocalToRemoteSyncableTrack> localToRemoteSyncableTracks = 
				LocalToRemoteSyncableTrackMapper.getInstance().getAllByOwner(this.owner);
		
		searchDifferences(remoteTracks, remoteAlbums, localToRemoteSyncableTracks);
		
		remoteTracks = null;
		remoteAlbums = null;
		localToRemoteSyncableTracks = null;
		
		Log.d(TAG, "remoteTracksToAddLocally=" + remoteTracksToAddLocally.size() + 
				"; remoteTracksToUpdateLocally=" + remoteTracksToUpdateLocally.size() + 
				"; localTracksToAddRemotely=" + localTracksToAddRemotely.size() +
				"; remoteAlbumsToAddLocally=" + remoteAlbumsToAddLocally.size() + 
				"; remoteAlbumsToUpdateLocally=" + remoteAlbumsToUpdateLocally.size() + 
				"; localAlbumsToAddRemotely=" + localAlbumsToAddRemotely.size());
		
		Analytics.logSync(
				remoteTracksToAddLocally.size(), 
				remoteTracksToUpdateLocally.size(), 
				localTracksToAddRemotely.size(), 
				remoteAlbumsToAddLocally.size(), 
				remoteAlbumsToUpdateLocally.size(), 
				localAlbumsToAddRemotely.size(), 
				!this.owner.isGroup() && this.owner.getId() != Preferences.getInstance().getUserId(),
				this.owner.isGroup() && this.owner.getId() != Preferences.getInstance().getUserId()
		);
		
		if (stopped || (countOfTracksToProcess + countOfAlbumsToProcess == 0)) {
			done();
			return;
		}

		anyChanges = true;
		lockWifi();
		sync();
		done();
	}

	private void searchDifferences(
			Track[] remoteTracks,
			List<Album> remoteAlbums,
			DomainModelCollection<LocalToRemoteSyncableTrack> localToRemoteSyncableTracks) 
	{
		searchingDifferences = true;
		notifyAboutSearchingDifferences();
		
		remoteTracksToAddLocally = getRemoteTracksToAddLocally(remoteTracks, syncedTracks);
		remoteTracksToUpdateLocally = getRemoteTracksToUpdateLocally(remoteTracks, syncedTracks);
		localTracksToAddRemotely = getLocalTracksToAddRemotely(localToRemoteSyncableTracks);
		countOfTracksToProcess = remoteTracksToAddLocally.size() + 
				remoteTracksToUpdateLocally.size() + localTracksToAddRemotely.size();
		
		Map<Long, Album> remoteAlbumsHashedById = new HashMap<Long, Album>(remoteAlbums.size());
		
		for (Album remoteAlbum : remoteAlbums) {
			remoteAlbumsHashedById.put(remoteAlbum.id, remoteAlbum);
		}
		
		remoteAlbumsToAddLocally = getRemoteAlbumsToAddLocally(remoteTracksToAddLocally, 
				remoteAlbumsHashedById, syncedAlbums);
		remoteAlbumsToUpdateLocally = 
				getRemoteAlbumsToUpdateLocally(syncedAlbums, remoteAlbumsHashedById);
		remoteAlbumsHashedById = null;
		localAlbumsToAddRemotely = 
				getLocalAlbumsToAddRemotely(localTracksToAddRemotely, remoteAlbums);
		countOfAlbumsToProcess = remoteAlbumsToAddLocally.size() + 
				remoteAlbumsToUpdateLocally.size() + localAlbumsToAddRemotely.size();
		
		searchingDifferences = false;
	}

	private void sync() {
		new AddRemoteAlbumsLocally(this).process(remoteAlbumsToAddLocally);
		remoteAlbumsToAddLocally = null;
		
		if (stopped) {
			return;
		}
		
		new AddLocalAlbumsRemotelyProcessor(this).process(localAlbumsToAddRemotely);
		localAlbumsToAddRemotely = null;
		
		if (stopped) {
			return;
		}
		
		new AddRemoteTracksLocallyProcessor(this).process(remoteTracksToAddLocally);
		remoteTracksToAddLocally = null;
		
		if (stopped) {
			return;
		}

		new AddLocalTracksRemotelyProcessor(this).process(localTracksToAddRemotely);
		localTracksToAddRemotely = null;
		
		if (stopped) {
			return;
		}
		
		new UpdateLocalTracksProcessor(this).process(remoteTracksToUpdateLocally);
		syncedTracks = null;
		remoteTracksToUpdateLocally = null;
		
		if (stopped) {
			return;
		}
		
		new UpdateLocalAlbumsProcessor(this).process(remoteAlbumsToUpdateLocally);
	}

	private LinkedList<NotSyncedLocalAlbum> getLocalAlbumsToAddRemotely(
			List<NotSyncedLocalTrack> localTracksToAddRemotely,
			List<Album> remoteAlbums) 
	{
		Log.d(TAG, "getLocalAlbumsToAddRemotely()");
		
		Map<String, Album> remoteAlbumsHashedByTitle = 
				new HashMap<String, Album>(remoteAlbums.size());
		
		for (Album remoteAlbum : remoteAlbums) {
			remoteAlbumsHashedByTitle.put(generateAlbumDirName(remoteAlbum.title), remoteAlbum);
		}
		
		Set<NotSyncedLocalAlbum> localAlbumsToAdd = new HashSet<NotSyncedLocalAlbum>();
		
		for (NotSyncedLocalTrack localTrackToAddRemotely : localTracksToAddRemotely) {
			File localTrackFile = localTrackToAddRemotely.getFile();
			
			if (!localTrackFile.exists()) {
				continue;
			}
			
			File localAlbumDir = localTrackFile.getParentFile();
			String localAlbumTitle = localAlbumDir.equals(musicDir) ? null : 
				localAlbumDir.getName();
			
			// if track not in album or album already exists remotely
			if (localAlbumTitle == null || remoteAlbumsHashedByTitle.containsKey(localAlbumTitle)) {
				continue;
			}
			
			NotSyncedLocalAlbum notSyncedLocalAlbum = new NotSyncedLocalAlbum(localAlbumDir);
			localAlbumsToAdd.add(notSyncedLocalAlbum);
		}
		
		return new LinkedList<NotSyncedLocalAlbum>(localAlbumsToAdd);
	}

	private LinkedList<Album> getRemoteAlbumsToUpdateLocally(
			DomainModelCollection<SyncedAlbum> syncedAlbums, Map<Long, Album> remoteAlbums) 
	{
		Log.d(TAG, "getRemoteAlbumsToUpdateLocally()");
		
		LinkedList<Album> remoteAlbumsToUpdate = new LinkedList<Album>();
		
		for (SyncedAlbum syncedAlbum : syncedAlbums) {
			Album correspondingRemoteAlbum = remoteAlbums.get(syncedAlbum.getId());
			
			if (correspondingRemoteAlbum != null 
				&& !TextUtils.equals(syncedAlbum.getTitle(), correspondingRemoteAlbum.title)) 
			{
				remoteAlbumsToUpdate.add(correspondingRemoteAlbum);
			}
		}
		
		return remoteAlbumsToUpdate;
	}

	private LinkedList<Album> getRemoteAlbumsToAddLocally(List<Track> remoteTracksToAddLocally, 
			Map<Long, Album> remoteAlbums, DomainModelCollection<SyncedAlbum> syncedAlbums) 
	{
		Log.d(TAG, "getRemoteAlbumsToAddLocally()");
		
		Set<Album> remoteAlbumsToAdd = new HashSet<Album>();
		
		for (Track remoteTrack : remoteTracksToAddLocally) {
			
			if (remoteTrack.albumId == null) {
				continue;
			}
			
			Album remoteAlbum = remoteAlbums.get(remoteTrack.albumId);
			SyncedAlbum correspondingSyncedAlbum = syncedAlbums.get(remoteAlbum.id);
			
			if (correspondingSyncedAlbum == null || !correspondingSyncedAlbum.getDir().exists()) {
				remoteAlbumsToAdd.add(remoteAlbum);
			}
		}
		
		return new LinkedList<Album>(remoteAlbumsToAdd);
	}

	private void unlockWifi() {
		
		if (wifiLock != null) {
			wifiLock.release();
		}
	}

	private void lockWifi() {
		WifiManager wifiManager = ((WifiManager) context.getSystemService(Context.WIFI_SERVICE));
		
		if (wifiManager != null) {
			wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
			wifiLock.acquire();
		}
	}

	private LinkedList<NotSyncedLocalTrack> getLocalTracksToAddRemotely(
			DomainModelCollection<LocalToRemoteSyncableTrack> localToRemoteSyncableTracks) 
	{
		Log.d(TAG, "getLocalTracksToAddRemotely()");
		
		LinkedList<NotSyncedLocalTrack> localTracksToAdd = new LinkedList<NotSyncedLocalTrack>();
		
		for (LocalToRemoteSyncableTrack localToRemoteSyncableTrack : localToRemoteSyncableTracks) {
			File localToRemoteSyncableTrackFile = localToRemoteSyncableTrack.getFile();
			
			if (!localToRemoteSyncableTrackFile.exists()) {
				continue;
			}
			
			NotSyncedLocalTrack notSyncedLocalTrack = new NotSyncedLocalTrack(
					localToRemoteSyncableTrackFile);
			localTracksToAdd.add(notSyncedLocalTrack);
		}
		
		return localTracksToAdd;
	}

	private LinkedList<Track> getRemoteTracksToAddLocally(Track[] syncableRemoteTracks, 
			DomainModelCollection<SyncedTrack> syncedTracks) 
	{
		Log.d(TAG, "getRemoteTracksToAddLocally()");
		
		LinkedList<Track> remoteTracksToAdd = new LinkedList<Track>();
		
		for (Track remoteTrack : syncableRemoteTracks) {
			SyncedTrack correspondingLocalTrack = syncedTracks.get(remoteTrack.id);
			
			if (correspondingLocalTrack == null 
				|| !correspondingLocalTrack.getFile().exists()) 
			{
				remoteTracksToAdd.add(remoteTrack);
			}
		}
		
		return remoteTracksToAdd;
	}

	private LinkedList<Track> getRemoteTracksToUpdateLocally(Track[] syncableRemoteTracks, 
			DomainModelCollection<SyncedTrack> syncedTracks) 
	{
		Log.d(TAG, "getRemoteTracksToUpdateLocally()");
		
		LinkedList<Track> remoteTracksToUpdate = new LinkedList<Track>();
		
		for (Track remoteTrack : syncableRemoteTracks) {
			SyncedTrack correspondingLocalTrack = syncedTracks.get(remoteTrack.id);
			
			if (correspondingLocalTrack != null 
				&& correspondingLocalTrack.getFile().exists()
				&& isLocalTrackNeedsUpdate(remoteTrack, correspondingLocalTrack)) 
			{
				remoteTracksToUpdate.add(remoteTrack);
			}
		}
		
		return remoteTracksToUpdate;
	}

	private void notifyAboutRun() {
		Log.d(TAG, "notifyAboutRun()");
		
		synchronized (onRunListeners) {
			for (OnRunListener onRunListener : onRunListeners) {
				onRunListener.onRun();
			}
		}
	}

	private boolean isLocalTrackNeedsUpdate(Track remoteTrack, SyncedTrack localTrack) {
		return localTrack.isDiffersFrom(remoteTrack);
	}
	
	private void notifyAboutSearchingDifferences() {
		
		synchronized (onSearchingDifferenesListeners) {
			
			for (OnSearchingDifferenesListener listener : onSearchingDifferenesListeners) {
				listener.onSearchingDifferences();
			}
		}
	}

	private void notifyAboutError(String errorMsg) {
		
		if (onErrorListener != null) {
			onErrorListener.onError(errorMsg);
		}
	}

	private void notifyAboutDone() {
		Log.d(TAG, "notifyAboutDone()");

		synchronized (onDoneListeners) {
			
			for (OnDoneListener listener : onDoneListeners) {
				listener.onDone(anyChanges);
			}
		}
	}
	
	private void notifyAboutStop() {
		Log.d(TAG, "notifyAboutStop()");
		
		if (onStopListener != null) {
			onStopListener.onStop();
		}
	}
	
	private void notifyAboutStartProcessingTrack(String trackName) {
		Log.d(TAG, "notifyAboutStartProcessingTrack()");
		
		synchronized (onStartProcessingTrackListeners) {
			
			for (OnStartProcessingTrackListener listener : onStartProcessingTrackListeners) {
				listener.onStartProcessingTrack(trackName);
			}
		}
	}

	private void notifyAboutStartProcessingAlbum(String albumTitle) {
		Log.d(TAG, "notifyAboutStartProcessingAlbum()");
		
		synchronized (onStartProcessingAlbumListeners) {
			
			for (OnStartProcessingAlbumListener listener : onStartProcessingAlbumListeners) {
				listener.onStartProcessingAlbum(albumTitle);
			}
		}
	}
	
	private void notifyAboutProcessTrackProgressUpdate(String trackTitle, 
			long processedBytesCount, long totalBytesCount) 
	{
		synchronized (onProcessTrackProgressUpdateListeners) {
			
			for (OnProcessTrackProgressUpdateListener listener : onProcessTrackProgressUpdateListeners) {
				listener.onProcessTrackProgressUpdate(trackTitle, processedBytesCount, 
						totalBytesCount);
			}
		}
	}

	private void done() {
		callerThread = null;
		musicDir = null;
		vkGateway = null;
		searchingDifferences = false;
		unlockWifi();
		
		if (countOfProcessedTracks + countOfProcessedAlbums > 0) {
			CommonUtils.scanMediaFiles(context);
		}

		context = null;
		status = Status.DONE;
		notifyAboutDone();
	}

}
