package com.malykhin.orm;

import java.sql.SQLException;

/**
 * 
 * @author Andrey Malykhin
 *
 * @param <T> {@link AbstractDomainModel}
 */
public interface DomainModelMapper<T extends AbstractDomainModel> {

	public abstract void beginTransaction();

	public abstract void setTransactionSuccessful();

	public abstract void endTransaction();

	/**
	 * 
	 * @throws SQLException
	 */
	public abstract void add(DomainModelCollection<T> models) throws SQLException;

	/**
	 * 
	 * @throws SQLException
	 */
	public abstract void add(T model) throws SQLException;

	public abstract boolean delete(T model);

	public abstract boolean delete(Object identityField);

	public abstract boolean update(T model);

	public abstract void refresh(DomainModelCollection<T> models);

	/**
	 * 
	 * @return Null if there are no model for such identity field
	 */
	public abstract T getOneByIdentityField(Object identityField);

	public abstract DomainModelCollection<T> getAll();

}