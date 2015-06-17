package com.malykhin.orm;

import java.util.HashMap;

/**
 * 
 * @author Andrey Malykhin
 * 
 * @param <T> {@link AbstractDomainModel}
 */
public class IdentityMap<T extends AbstractDomainModel> extends HashMap<Object, T>{

	private static final long serialVersionUID = -3911951497285736372L;
	
	/**
	 * 
	 * @return The value of any previous mapping with the specified key or {@code null} if there was 
	 * 		no such mapping.
	 */
	public T put(T model) {
		T previousModel = get(model.getIdentityField());
		put(model.getIdentityField(), model);
		
		return previousModel;
	}
}
