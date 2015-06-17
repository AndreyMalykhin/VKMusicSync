package com.malykhin.vkmusicsync.model;

import java.io.File;

import org.apache.commons.io.FileUtils;

import android.text.Html;
import android.text.TextUtils;

import com.malykhin.orm.AbstractDomainModel;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class SyncedAlbum extends AbstractDomainModel {
	
	private static final String TAG = SyncedAlbum.class.getSimpleName();
	
	private long id;
	private MusicOwner owner;
	private String title;
	private String dirName;

	/**
	 * 
	 * @return Null if not set
	 */
	public String getDirName() {
		return dirName;
	}

	/**
	 * 
	 * @throws IllegalArgumentException
	 */
	public SyncedAlbum setDirName(String dirName) {
		
		if (TextUtils.isEmpty(dirName)) {
			throw new IllegalArgumentException("Dir name cant be empty");
		}
		
		this.dirName = dirName;
		return this;
	}

	@Override
	public Long getIdentityField() {
		return getId();
	}
	
	@Override
	public SyncedAlbum setIdentityField(Object identityField) {
		return setId((Long) identityField);
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
	public SyncedAlbum setOwner(MusicOwner owner) {
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		this.owner = owner;
		return this;
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
	public SyncedAlbum setId(long id) {
		
		if (id == 0) {
			throw new IllegalArgumentException("ID cant be 0");
		}
		
		this.id = id;
		return this;
	}

	/**
	 * 
	 * @return Null if not set
	 */
	public String getTitle() {
		return title;
	}
	
	/**
	 * 
	 * @throws IllegalArgumentException
	 */
	public SyncedAlbum setTitle(String title) {
		
		if (TextUtils.isEmpty(title)) {
			throw new IllegalArgumentException("Title cant be empty");
		}
		
		this.title = Html.fromHtml(title).toString();
		return this;
	}

	/**
	 * 
	 * @throws IllegalStateException If title is not set
	 * @throws IllegalStateException If owner is not set
	 */
	public File getDir() {
		
		if (owner == null) {
			throw new IllegalStateException("Owner is not set. See setOwner()");
		}
		
		if (title == null) {
			throw new IllegalStateException("Title is not set. See setTitle()");
		}
		
		return FileUtils.getFile(
				MusicDirectoryMapper.getInstance().getOneByOwner(owner).getDirectory(), dirName
		);
	}
	
}
