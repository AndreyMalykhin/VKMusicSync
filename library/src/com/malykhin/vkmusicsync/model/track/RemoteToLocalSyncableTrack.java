package com.malykhin.vkmusicsync.model.track;

import com.malykhin.orm.AbstractDomainModel;
import com.malykhin.vkmusicsync.model.MusicOwner;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class RemoteToLocalSyncableTrack extends AbstractDomainModel {

	private MusicOwner owner;
	private long id;
	
	/**
	 * 
	 * @return Null if not set
	 */
	public MusicOwner getOwner() {
		return owner;
	}

	/**
	 * 
	 * @throws NullPointerException
	 */
	public RemoteToLocalSyncableTrack setOwner(MusicOwner owner) {
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		this.owner = owner;
		return this;
	}

	@Override
	public Long getIdentityField() {
		return getId();
	}

	@Override
	public RemoteToLocalSyncableTrack setIdentityField(Object identityField) {
		return setId((Long) identityField);
	}

	/**
	 * 
	 * @return Track ID. 0 if not set
	 */
	public long getId() {
		return id;
	}

	/**
	 * 
	 * @throws IllegalArgumentException
	 */
	public RemoteToLocalSyncableTrack setId(long trackId) {
		
		if (trackId == 0) {
			throw new IllegalArgumentException("Track ID cant be 0");
		}
		
		this.id = trackId;
		return this;
	}
}
