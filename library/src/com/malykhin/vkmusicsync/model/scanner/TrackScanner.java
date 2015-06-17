package com.malykhin.vkmusicsync.model.scanner;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import android.text.TextUtils;

import com.malykhin.gateway.vk.Album;
import com.malykhin.gateway.vk.Track;
import com.malykhin.gateway.vk.VkException;
import com.malykhin.gateway.vk.VkGatewayException;
import com.malykhin.orm.DomainModelCollection;
import com.malykhin.vkmusicsync.VkGatewayHelper;
import com.malykhin.vkmusicsync.model.MusicDirectory;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.model.Synchronizer;
import com.malykhin.vkmusicsync.model.track.NotSyncedLocalTrack;
import com.malykhin.vkmusicsync.model.track.SyncedTrack;
import com.malykhin.vkmusicsync.model.track.SyncedTrackMapper;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class TrackScanner 
	extends AbstractMusicEntityScanner<NotSyncedLocalTrack, SyncedTrack, Track> 
{
	public static class TrackScannerResult 
		extends MusicEntityScannerResult<NotSyncedLocalTrack, SyncedTrack, Track> 
	{
		TrackScannerResult(Set<NotSyncedLocalTrack> notSyncedLocalEntities,
				DomainModelCollection<SyncedTrack> syncedEntities,
				Collection<Track> remoteEntities) 
		{
			super(notSyncedLocalEntities, syncedEntities, remoteEntities);
		}
	}

	private Map<Long, Album> remoteAlbums;

	public static Collection<File> getLocalTrackFiles(File musicDir) {
		Collection<File> trackFiles = new LinkedList<File>();
		File[] filesAndDirs = musicDir.listFiles(new FileFilter() {
			
			@Override
			public boolean accept(File file) {
				return file.isDirectory() 
						|| FilenameUtils.getExtension(file.getName()).toLowerCase().equals("mp3");
			}
			
		});
		
		for (File file : filesAndDirs) {
			
			if (!file.isDirectory()) {
				trackFiles.add(file);
				continue;
			}
			
			// add tracks from first level directory
			trackFiles.addAll(FileUtils.listFiles(file, new String[]{"mp3"}, false));
		}
		
		return trackFiles;
	}
	
	public TrackScanner(Collection<Album> remoteAlbums) {
		this.remoteAlbums = new HashMap<Long, Album>(remoteAlbums.size());
		
		for (Album remoteAlbum : remoteAlbums) {
			this.remoteAlbums.put(remoteAlbum.id, remoteAlbum);
		}
	}
	
	@Override
	public synchronized TrackScannerResult scan(MusicDirectory musicDir) throws VkGatewayException {
		return (TrackScannerResult) super.scan(musicDir);
	}
	
	@Override
	protected TrackScannerResult createResult(TreeSet<NotSyncedLocalTrack> notSyncedLocalEntities,
			DomainModelCollection<SyncedTrack> syncedEntities, Collection<Track> remoteEntities) 
	{
		return new TrackScannerResult(notSyncedLocalEntities, syncedEntities,
				remoteEntities);
	}
	
	@Override
	protected File getSyncedEntityFile(SyncedTrack syncedEntity) {
		return syncedEntity.getFile();
	}

	@Override
	protected NotSyncedLocalTrack createNotSyncedLocalEntity(File localEntityFile) {
		return new NotSyncedLocalTrack(localEntityFile);
	}

	@Override
	protected String getSyncedEntityFilename(SyncedTrack syncedEntity) {
		return syncedEntity.getFilename();
	}

	@Override
	protected DomainModelCollection<SyncedTrack> getSyncedEntities(MusicOwner owner) {
		return ((SyncedTrackMapper) syncedEntityMapper).getAllByOwner(owner);
	}

	@Override
	protected Comparator<NotSyncedLocalTrack> getNotSyncedLocalEntityComparator() {
		
		return new Comparator<NotSyncedLocalTrack>() {

			@Override
			public int compare(NotSyncedLocalTrack track1, NotSyncedLocalTrack track2) {
				
				if (track1.equals(track2)) {
					return 0;
				}
				
				if (track1.getFile().lastModified() >= track2.getFile().lastModified()) {
					return -1;
				} else {
					return 1;
				}
			}
			
		};
	}

	@Override
	protected SyncedTrackMapper getSyncedEntityMapper() {
		return SyncedTrackMapper.getInstance();
	}

	@Override
	protected List<Track> getRemoteEntities(MusicOwner owner) throws VkGatewayException {
		
		try {
			return Arrays.asList(VkGatewayHelper.getGateway().getTracks(owner.getId(), 
					owner.isGroup()));
		} catch (VkException exception) {
			throw new VkGatewayException(null, exception, exception);
		} catch (Exception exception) {
			throw new VkGatewayException(exception);
		}
	}

	@Override
	protected Long getRemoteEntityId(Track remoteEntity) {
		return remoteEntity.id;
	}

	@Override
	protected String getRemoteEntityFilename(Track remoteEntity) {
		return Synchronizer.generateTrackFilename(remoteEntity);
	}

	@Override
	protected SyncedTrack createSyncedEntity(MusicOwner owner, String entityFilename, 
			Track remoteEntity) 
	{
		SyncedTrack syncedTrack = new SyncedTrack();
		syncedTrack.setAlbumId(remoteEntity.albumId)
			.setOwner(owner)
			.setArtist(remoteEntity.artist)
			.setDuration(remoteEntity.duration)
			.setFilename(entityFilename)
			.setId(remoteEntity.id)
			.setTitle(remoteEntity.title);
		
		return syncedTrack;
	}

	@Override
	protected Collection<File> getLocalEntityFiles(File musicDir) {
		return getLocalTrackFiles(musicDir);
	}

	@Override
	protected boolean isEntityCanBeMarkedAsSynced(File localTrackFile,
			Track correspondingRemoteTrack, File musicDir) 
	{
		File localTrackAlbumDir = localTrackFile.getParentFile();
		String localTrackAlbumName = localTrackAlbumDir.equals(musicDir) ? null : 
				localTrackAlbumDir.getName();
		Album remoteTrackAlbum = remoteAlbums.get(correspondingRemoteTrack.albumId);
		String remoteTrackAlbumName = remoteTrackAlbum == null ? null : 
				Synchronizer.generateAlbumDirName(remoteTrackAlbum.title);

		return TextUtils.equals(remoteTrackAlbumName, localTrackAlbumName);
	}

	@Override
	protected String getLogTag() {
		return TrackScanner.class.getSimpleName();
	}
}
