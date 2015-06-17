package com.malykhin.vkmusicsync.model.scanner;

import java.io.File;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.malykhin.gateway.vk.VkGatewayException;
import com.malykhin.orm.AbstractDomainModel;
import com.malykhin.orm.AbstractMapper;
import com.malykhin.orm.DomainModelCollection;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.model.MusicDirectory;
import com.malykhin.vkmusicsync.model.MusicOwner;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public abstract class AbstractMusicEntityScanner<NotSyncedLocalEntity, 
	SyncedEntity extends AbstractDomainModel, RemoteEntity> 
{
	public static class MusicEntityScannerResult<NotSyncedLocalEntity, 
		SyncedEntity extends AbstractDomainModel, RemoteEntity>  
	{
		public final Set<NotSyncedLocalEntity> notSyncedLocalEntities;
		public final DomainModelCollection<SyncedEntity> syncedEntities;
		public final Collection<RemoteEntity> remoteEntities;
		
		MusicEntityScannerResult(
				Set<NotSyncedLocalEntity> notSyncedLocalEntities, 
				DomainModelCollection<SyncedEntity> syncedEntities, 
				Collection<RemoteEntity> remoteEntities) 
		{
			this.notSyncedLocalEntities = notSyncedLocalEntities;
			this.syncedEntities = syncedEntities;
			this.remoteEntities = remoteEntities;
		}
	}

	protected AbstractMapper<SyncedEntity> syncedEntityMapper;
	
	private final String TAG = getLogTag();
	
	private Map<String, RemoteEntity> remoteEntitiesHashedByFilename;
	private Map<Long, RemoteEntity> remoteEntitiesHashedById;
	private Map<String, SyncedEntity> hashedSyncedEntities;

	/**
	 * Finds local entities that are exists remotely and marks them as synced.
	 *  
	 * If music dir belongs to logged user, finds local entities that are not exists remotely, and 
	 * marks them as not synced.
	 *  
	 * Finds synced entities for which local file is missed and marks them as not synced. 
     * 
     * @throws VkGatewayException
	 */
	public synchronized MusicEntityScannerResult<NotSyncedLocalEntity, SyncedEntity, RemoteEntity> 
			scan(MusicDirectory musicDir) throws VkGatewayException 
	{
		Log.d(TAG, "scan()");

		syncedEntityMapper = getSyncedEntityMapper();
		loadRemoteEntities(musicDir.getOwner());
		loadSyncedEntities(musicDir.getOwner());
		
		Comparator<NotSyncedLocalEntity> notSyncedLocalEntityComparator = 
				getNotSyncedLocalEntityComparator();

		MusicEntityScannerResult<NotSyncedLocalEntity, SyncedEntity, RemoteEntity> result = 
				createResult(
						new TreeSet<NotSyncedLocalEntity>(notSyncedLocalEntityComparator), 
						new DomainModelCollection<SyncedEntity>(),
						remoteEntitiesHashedById.values()
				);
		boolean isScanningLoggedUser = !musicDir.getOwner().isGroup() 
				&& musicDir.getOwner().getId() == Preferences.getInstance().getUserId();
		syncedEntityMapper.beginTransaction();
		
		try {
			scanLocalEntities(musicDir, result, isScanningLoggedUser);
			scanSyncedEntities(musicDir, result, isScanningLoggedUser);
			
			Log.d(TAG, "scan(); result: syncedEntitiesCount=" + result.syncedEntities.getCount() + 
					"; notSyncedLocalEntitiesCount=" + result.notSyncedLocalEntities.size() + 
					"; remoteEntitiesCount=" + result.remoteEntities.size());
		} finally {
			syncedEntityMapper.setTransactionSuccessful();
			syncedEntityMapper.endTransaction();

			remoteEntitiesHashedByFilename = null;
			remoteEntitiesHashedById = null;
			hashedSyncedEntities = null;
			syncedEntityMapper = null;
		}
		
		return result;
	}

	protected abstract String getLogTag();
	
	protected abstract File getSyncedEntityFile(SyncedEntity syncedEntity);

	protected abstract NotSyncedLocalEntity createNotSyncedLocalEntity(File localEntityFile);
	
	protected abstract String getSyncedEntityFilename(SyncedEntity syncedEntity);

	protected abstract DomainModelCollection<SyncedEntity> getSyncedEntities(MusicOwner owner);
	
	protected abstract Comparator<NotSyncedLocalEntity> getNotSyncedLocalEntityComparator();

	protected abstract AbstractMapper<SyncedEntity> getSyncedEntityMapper();
	
	protected abstract List<RemoteEntity> getRemoteEntities(MusicOwner owner) 
			throws VkGatewayException;

	protected abstract Long getRemoteEntityId(RemoteEntity remoteEntity);

	protected abstract String getRemoteEntityFilename(RemoteEntity remoteEntity);
	
	protected abstract SyncedEntity createSyncedEntity(MusicOwner owner, String entityFilename,
			RemoteEntity remoteEntity);
	
	protected abstract Collection<File> getLocalEntityFiles(File musicDir);
	
	protected boolean isEntityCanBeMarkedAsSynced(File localEntityFile,
			RemoteEntity correspondingRemoteEntity, File musicDir) 
	{
		return true;
	}
	
	protected MusicEntityScannerResult<NotSyncedLocalEntity, SyncedEntity, RemoteEntity> 
		createResult(
				TreeSet<NotSyncedLocalEntity> notSyncedLocalEntities,
				DomainModelCollection<SyncedEntity> syncedEntities,
				Collection<RemoteEntity> remoteEntities
		) 
	{
		return new MusicEntityScannerResult<NotSyncedLocalEntity, SyncedEntity, RemoteEntity>(
				notSyncedLocalEntities, syncedEntities, remoteEntities);
	}
	
	private void loadSyncedEntities(MusicOwner owner) {
		DomainModelCollection<SyncedEntity> syncedEntities = getSyncedEntities(owner);
		hashedSyncedEntities = new HashMap<String, SyncedEntity>(syncedEntities.getCount());
		
		for (SyncedEntity syncedEntity : syncedEntities) {
			hashedSyncedEntities.put(getSyncedEntityFilename(syncedEntity), syncedEntity);
		}
	}
	
	private void scanLocalEntities(
			MusicDirectory musicDir,
			MusicEntityScannerResult<NotSyncedLocalEntity, SyncedEntity, RemoteEntity> result, 
			boolean isScanningLoggedUser) 
	{
		Collection<File> localEntityFiles = getLocalEntityFiles(musicDir.getDirectory());
		
		for (File localEntityFile : localEntityFiles) {
			String localEntityFilename = localEntityFile.getName();
			SyncedEntity correspondingSyncedEntity = hashedSyncedEntities.get(localEntityFilename);
			RemoteEntity correspondingRemoteEntity = null;
			
			if (correspondingSyncedEntity == null) {
				correspondingRemoteEntity = remoteEntitiesHashedByFilename.get(localEntityFilename);
			} else {
				correspondingRemoteEntity = 
						remoteEntitiesHashedById.get(correspondingSyncedEntity.getIdentityField());
			}

			// if local entity doesnt exists remotely
			if (correspondingRemoteEntity == null) {
				
				if (isScanningLoggedUser) {
					result.notSyncedLocalEntities.add(createNotSyncedLocalEntity(localEntityFile));
				}
				
			} else {

				boolean isEntityCanBeMarkedAsSynced = isEntityCanBeMarkedAsSynced(localEntityFile, 
						correspondingRemoteEntity, musicDir.getDirectory()); 
				
				if (isEntityCanBeMarkedAsSynced) {
					
					// if entity is not synced
					if (correspondingSyncedEntity == null) {
						
						try {
							correspondingSyncedEntity = markEntityAsSynced(
									musicDir.getOwner(), 
									localEntityFilename, 
									correspondingRemoteEntity
							);
						} catch (SQLException exception) {
							Log.e(TAG, null, exception);
						}
					}
					
					if (correspondingSyncedEntity != null) {
						result.syncedEntities.add(correspondingSyncedEntity);
					}
				}
			}
		}
	}
	
	private void scanSyncedEntities(
			MusicDirectory musicDir,
			MusicEntityScannerResult<NotSyncedLocalEntity, SyncedEntity, RemoteEntity> result, 
			boolean isScanningLoggedUser) 
	{
		for (SyncedEntity syncedEntity : hashedSyncedEntities.values()) {
			RemoteEntity correspondingRemoteEntity = 
					remoteEntitiesHashedById.get(syncedEntity.getIdentityField()); 
			
			// if local entity doesnt exists remotely
			if (correspondingRemoteEntity == null) {
				
				if (isScanningLoggedUser) {
					File syncedEntityFile = getSyncedEntityFile(syncedEntity);
					
					if (syncedEntityFile.exists()) {
						result.notSyncedLocalEntities.add(
								createNotSyncedLocalEntity(syncedEntityFile));
					}
				}

				markEntityAsNotSynced(syncedEntity);
			} else {
				
				if (getSyncedEntityFile(syncedEntity).exists() 
					&& isEntityCanBeMarkedAsSynced(
							getSyncedEntityFile(syncedEntity), 
							correspondingRemoteEntity, 
							musicDir.getDirectory())
					) 
				{
					result.syncedEntities.add(syncedEntity);
				} else {
					markEntityAsNotSynced(syncedEntity);
				}
			}
		}
	}
	
	private void markEntityAsNotSynced(SyncedEntity syncedEntity) {
		syncedEntityMapper.delete(syncedEntity);
	}

	private void loadRemoteEntities(MusicOwner owner) throws VkGatewayException {
		List<RemoteEntity> remoteEntities = getRemoteEntities(owner);
		remoteEntitiesHashedByFilename = new HashMap<String, RemoteEntity>(remoteEntities.size());
		remoteEntitiesHashedById = new LinkedHashMap<Long, RemoteEntity>(remoteEntities.size());
		
		for (RemoteEntity remoteEntity : remoteEntities) {
			remoteEntitiesHashedByFilename.put(getRemoteEntityFilename(remoteEntity), remoteEntity);
			remoteEntitiesHashedById.put(getRemoteEntityId(remoteEntity), remoteEntity);
		}
	}

	private SyncedEntity markEntityAsSynced(MusicOwner owner, String entityFilename, 
			RemoteEntity remoteEntity) throws SQLException 
	{
		SyncedEntity syncedEntity = createSyncedEntity(owner, entityFilename, remoteEntity);
		syncedEntityMapper.add(syncedEntity);
		
		return syncedEntity;
	}

}
