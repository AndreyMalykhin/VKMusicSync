package com.malykhin.vkmusicsync.model;

import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class MusicOwner {
	private long id;
	private boolean isGroup;
	
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
	public MusicOwner setId(long id) {
		
		if (id == 0) {
			throw new IllegalArgumentException("ID cant be 0");
		}
		
		this.id = id;
		return this;
	}
	
	public boolean isGroup() {
		return isGroup;
	}
	
	public MusicOwner setGroup(boolean isGroup) {
		this.isGroup = isGroup;
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
		
		MusicOwner owner = (MusicOwner) o;
		
		return id == owner.id && isGroup == owner.isGroup;
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
			.append(id)
			.append(isGroup)
			.toHashCode();
	}
}
