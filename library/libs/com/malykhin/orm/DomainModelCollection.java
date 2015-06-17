package com.malykhin.orm;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Backed by {@link LinkedHashMap}.
 * 
 * @author Andrey Malykhin
 * 
 * @param <T> {@link AbstractDomainModel}
 */
public class DomainModelCollection<T extends AbstractDomainModel> implements Iterable<T> {
	protected LinkedHashMap<Object, T> items;
	
	public DomainModelCollection(int capacity) {
		items = new LinkedHashMap<Object, T>(capacity);
	}
	
	public DomainModelCollection() {
		items = new LinkedHashMap<Object, T>();
	}
	
	public void add(T model) {
		items.put(model.getIdentityField(), model);
	}
	
	/**
	 * 
	 * @return Null if not found
	 */
	public T get(Object identityField) {
		return items.get(identityField);
	}

	public void delete(Object identityField) {
		items.remove(identityField);
	}
	
	public void delete(T model) {
		items.remove(model.getIdentityField());
	}
	
	public void clear() {
		items.clear();
	}
	
	public boolean has(Object identityField) {
		return items.containsKey(identityField);
	}
	
	public boolean has(T model) {
		return items.containsKey(model.getIdentityField());
	}
	
	@Override
	public Iterator<T> iterator() {
		return items.values().iterator();
	}
	
	public int getCount() {
		return items.size();
	}
	
	public Collection<T> getItems() {
		return items.values();
	}

}
