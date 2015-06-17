package com.malykhin.vkmusicsync.model.track;

import java.io.File;

import org.apache.commons.io.FileUtils;

import com.malykhin.orm.AbstractDomainModel;
import com.malykhin.vkmusicsync.model.MusicDirectoryMapper;
import com.malykhin.vkmusicsync.model.MusicOwner;

/**
 * Track that needs to be added to VK.
 * 
 * @author Andrey Malykhin
 *
 */
public class LocalToRemoteSyncableTrack extends AbstractDomainModel {

	private long id;
	private String filename;
	private String album;
	private MusicOwner owner;
	
	/**
	 * 
	 * @return Null if not set
	 */
	public String getAlbum() {
		return album;
	}

	/**
	 * 
	 * @param album Can be null
	 */
	public LocalToRemoteSyncableTrack setAlbum(String album) {
		this.album = album;
		return this;
	}
	
	/**
	 * 
	 * @return Null if not set
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * 
	 * @throws NullPointerException
	 */
	public LocalToRemoteSyncableTrack setFilename(String filename) {
		
		if (filename == null) {
			throw new NullPointerException();
		}
		
		this.filename = filename;
		return this;
	}

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
	public LocalToRemoteSyncableTrack setOwner(MusicOwner owner) {
		
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
	public AbstractDomainModel setIdentityField(Object identityField) {
		return setId((Long) identityField);
	}
	
	/**
	 * 
	 * @return 0 if not set
	 */
	public long getId() {
		return id;
	}

	/**
	 * 
	 * @throws IllegalArgumentException
	 */
	public LocalToRemoteSyncableTrack setId(long id) {
		
		if (id == 0) {
			throw new IllegalArgumentException("ID cant be 0");
		}
		
		this.id = id;
		return this;
	}

	/**
	 * @throws IllegalStateException If owner is not set
	 */
	public File getFile() {
		
		if (owner == null) {
			throw new IllegalStateException("Owner is not set. See setOwner()");
		}

		if (album == null) {
			return new File(
					MusicDirectoryMapper.getInstance().getOneByOwner(owner).getDirectory(), 
					filename
			);
		}
		
		return FileUtils.getFile(
				MusicDirectoryMapper.getInstance().getOneByOwner(owner).getDirectory(), 
				album, 
				filename
		);
	}
}
