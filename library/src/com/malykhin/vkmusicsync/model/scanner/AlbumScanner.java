package com.malykhin.vkmusicsync.model.scanner;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.malykhin.gateway.vk.Album;
import com.malykhin.gateway.vk.VkException;
import com.malykhin.gateway.vk.VkGatewayException;
import com.malykhin.orm.DomainModelCollection;
import com.malykhin.vkmusicsync.VkGatewayHelper;
import com.malykhin.vkmusicsync.model.MusicDirectory;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.model.NotSyncedLocalAlbum;
import com.malykhin.vkmusicsync.model.SyncedAlbum;
import com.malykhin.vkmusicsync.model.SyncedAlbumMapper;
import com.malykhin.vkmusicsync.model.Synchronizer;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class AlbumScanner 
	extends AbstractMusicEntityScanner<NotSyncedLocalAlbum, SyncedAlbum, Album> 
{
	public static class AlbumScannerResult 
		extends MusicEntityScannerResult<NotSyncedLocalAlbum, SyncedAlbum, Album> 
	{
		AlbumScannerResult(Set<NotSyncedLocalAlbum> notSyncedLocalEntities,
				DomainModelCollection<SyncedAlbum> syncedEntities, Collection<Album> remoteEntities) 
		{
			super(notSyncedLocalEntities, syncedEntities, remoteEntities);
		}
		
	}
	
	@Override
	public synchronized AlbumScannerResult scan(MusicDirectory musicDir) throws VkGatewayException {
		return (AlbumScannerResult) super.scan(musicDir);
	}
	
	@Override
	protected AlbumScannerResult createResult(TreeSet<NotSyncedLocalAlbum> notSyncedLocalEntities,
			DomainModelCollection<SyncedAlbum> syncedEntities, Collection<Album> remoteEntities) 
	{
		return new AlbumScannerResult(notSyncedLocalEntities, syncedEntities, remoteEntities);
	}
	
	@Override
	protected File getSyncedEntityFile(SyncedAlbum syncedEntity) {
		return syncedEntity.getDir();
	}

	@Override
	protected NotSyncedLocalAlbum createNotSyncedLocalEntity(File localEntityFile) {
		return new NotSyncedLocalAlbum(localEntityFile);
	}

	@Override
	protected String getSyncedEntityFilename(SyncedAlbum syncedEntity) {
		return syncedEntity.getDirName();
	}

	@Override
	protected DomainModelCollection<SyncedAlbum> getSyncedEntities(MusicOwner owner) {
		return ((SyncedAlbumMapper) syncedEntityMapper).getAllByOwner(owner);
	}

	@Override
	protected Comparator<NotSyncedLocalAlbum> getNotSyncedLocalEntityComparator() {
		
		return new Comparator<NotSyncedLocalAlbum>() {

			@Override
			public int compare(NotSyncedLocalAlbum album1, NotSyncedLocalAlbum album2) {
				
				if (album1.equals(album2)) {
					return 0;
				}
				
				if (album1.getDir().lastModified() >= album2.getDir().lastModified()) {
					return -1;
				} else {
					return 1;
				}
			}
			
		};
	}

	@Override
	protected SyncedAlbumMapper getSyncedEntityMapper() {
		return SyncedAlbumMapper.getInstance();
	}

	@Override
	protected List<Album> getRemoteEntities(MusicOwner owner) throws VkGatewayException {
		
		try {
			return VkGatewayHelper.getGateway().getAlbums(owner.getId(), owner.isGroup());
		} catch (VkException exception) {
			throw new VkGatewayException(null, exception, exception);
		} catch (Exception exception) {
			throw new VkGatewayException(exception);
		}
	}

	@Override
	protected Long getRemoteEntityId(Album remoteEntity) {
		return remoteEntity.id;
	}

	@Override
	protected String getRemoteEntityFilename(Album remoteEntity) {
		return Synchronizer.generateAlbumDirName(remoteEntity.title);
	}

	@Override
	protected SyncedAlbum createSyncedEntity(MusicOwner owner, String entityFilename, 
			Album remoteEntity) 
	{
		SyncedAlbum syncedAlbum = new SyncedAlbum();
		syncedAlbum.setOwner(owner)
			.setId(remoteEntity.id)
			.setTitle(remoteEntity.title)
			.setDirName(entityFilename);
		
		return syncedAlbum;
	}

	@Override
	protected Collection<File> getLocalEntityFiles(File musicDir) {
		
		return Arrays.asList(musicDir.listFiles(new FileFilter() {
			
			@Override
			public boolean accept(File file) {
				return file.isDirectory();
			}
			
		}));
	}

	@Override
	protected String getLogTag() {
		return AlbumScanner.class.getSimpleName();
	}

}
