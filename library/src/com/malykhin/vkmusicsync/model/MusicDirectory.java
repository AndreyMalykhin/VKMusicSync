package com.malykhin.vkmusicsync.model;

import java.io.File;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import android.os.Environment;

import com.malykhin.orm.AbstractDomainModel;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class MusicDirectory extends AbstractDomainModel {

	private MusicOwner owner;
	private File directory;
	
	public static File getDefaultDirectory() {
		return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
			.getAbsolutePath() + "/VkMusic");
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
	public MusicDirectory setOwner(MusicOwner owner) {
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		this.owner = owner;
		return this;
	}

	/**
	 * 
	 * @return Null if not set
	 */
	public File getDirectory() {
		return directory;
	}

	/**
	 * 
	 * @throws NullPointerException
	 */
	public MusicDirectory setDirectory(String dirPath) {
		return setDirectory(new File(dirPath));
	}
	
	/**
	 * 
	 * @throws NullPointerException
	 */
	public MusicDirectory setDirectory(File directory) {
		
		if (directory == null) {
			throw new NullPointerException();
		}

		this.directory = directory;
		return this;
	}

	@Override
	public Object[] getIdentityField() {
		return new Object[]{this.owner.getId(), this.owner.isGroup() ? 1 : 0};
	}

	@Override
	public MusicDirectory setIdentityField(Object identityField) {
		if (owner == null) {
			owner = new MusicOwner();
		}
		
		Object[] identityFields = (Object[]) identityField;
		
		owner.setId((Long) identityFields[0])
			.setGroup(((Integer) identityFields[1]).equals(1) ? true : false);
		
		return this;
	}

	@Override
	public boolean equals(Object o) {
		
		if (o == null) {
			return false;
		}
		
		if (o == this) {
			return true;
		}

		MusicDirectory musicDir = (MusicDirectory) o;
		
		return musicDir.directory.equals(directory) 
				&& ((owner == null && musicDir.owner == null) || musicDir.owner.equals(owner));
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
			.append(owner)
			.append(directory)
			.toHashCode();
	}
}
