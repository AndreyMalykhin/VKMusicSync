package com.malykhin.orm;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

/**
 * 
 * @author Andrey Malykhin
 *
 * @param <T> {@link AbstractDomainModel}
 */
abstract public class AbstractMapper<T extends AbstractDomainModel> implements DomainModelMapper<T> {
	protected String tableName;
	protected SQLiteDatabase db;
	protected IdentityMap<T> identityMap;
	protected Object primaryKey;
	
	@Override
	public void beginTransaction() {
		db.beginTransaction();
	}
	
	@Override
	public void setTransactionSuccessful() {
		db.setTransactionSuccessful();
	}
	
	@Override
	public void endTransaction() {
		db.endTransaction();
	}
	
	/**
	 * 
	 * @throws SQLException
	 */
	@Override
	public void add(DomainModelCollection<T> models) throws SQLException {
		
		for (T model : models) {
			add(model);
		}
	}
	
	/**
	 * 
	 * @throws SQLException
	 */
	@Override
	public void add(T model) throws SQLException {
		long id = db.insert(tableName, null, modelToRow(model));
		
		if (id == -1) {
			throw new SQLException("Failed to insert record");
		}
		
		if (!isPrimaryKeyComposite()) {
			model.setIdentityField(id);
		}
		
		if (identityMap != null) {
			identityMap.put(model.getIdentityField(), model);
		}
	}
	
	@Override
	public boolean delete(T model) {
		return delete(model.getIdentityField());
	}
	
	@Override
	public boolean delete(Object identityField) {
		
		if (identityMap != null) {
			identityMap.remove(identityField);
		}

		return db.delete(tableName, getSqlWhereClauseForPrimaryKey(identityField), null) > 0;
	}
	
	@Override
	public boolean update(T model) {
		return db.update(tableName, modelToRow(model), 
				getSqlWhereClauseForPrimaryKey(model.getIdentityField()), null) == 1;
	}
	
	// TODO test
	@Override
	public void refresh(DomainModelCollection<T> models) {
		Set<Object> identityFields = new HashSet<Object>();
		
		if (isPrimaryKeyComposite()) {
			ArrayList<String> sqlWhereClauses = new ArrayList<String>(models.getCount());
			
			for (AbstractDomainModel model : models) {
				String[] primaryKeys = (String[]) primaryKey;
				String[] primaryKeyClauses = new String[primaryKeys.length];
				
				for (int i = 0; i < primaryKeys.length; i++) {
					primaryKeyClauses[i] = primaryKeys[i] + " = " + 
							String.valueOf(((Object[]) model.getIdentityField())[i]);
				}
				
				sqlWhereClauses.add(TextUtils.join(" AND ", primaryKeyClauses));
			}
			
			String sqlWhere = "(" + TextUtils.join(") OR (", sqlWhereClauses) + ")";
			Cursor cursor = db.query(tableName, null, sqlWhere, null, null, null, null);
			
			while (cursor.moveToNext()) {
				String[] primaryKeys = (String[]) primaryKey;
				String[] primaryKeyValues = new String[primaryKeys.length];
				
				for (int i = 0; i < primaryKeys.length; i++) {
					primaryKeyValues[i] = cursor.getString(
							cursor.getColumnIndex(primaryKeys[i]));
				}
				
				if (!models.has(primaryKeyValues)) {
					continue;
				}
				
				T model = (T) models.get(primaryKeyValues);
				fillModelFromRow(model, cursor);
			}
		} else {
			for (AbstractDomainModel model : models) {
				identityFields.add(String.valueOf(model.getIdentityField()));
			}
			
			String sqlWhere = primaryKey + " IN(" + TextUtils.join(",", identityFields) + ")";
			Cursor cursor = db.query(tableName, null, sqlWhere, null, null, null, null);
			
			while (cursor.moveToNext()) {
				String identityField = cursor.getString(
						cursor.getColumnIndex(String.valueOf(primaryKey)));
				
				if (!models.has(identityField)) {
					continue;
				}
				
				T model = (T) models.get(identityField);
				fillModelFromRow(model, cursor);
			}
		}
	}

	/**
	 * 
	 * @return Null if there are no model for such identity field
	 */
	@Override
	public T getOneByIdentityField(Object identityField) {

		if (identityMap != null) {
			T cachedModel = identityMap.get(identityField);
			
			if (cachedModel != null) {
				return cachedModel;
			}
		}

		Cursor row = db.query(tableName, null, getSqlWhereClauseForPrimaryKey(identityField), null, 
				null, null, null, "1");
		row.moveToFirst();

		return createModel(row);
	}
	
	@Override
	public DomainModelCollection<T> getAll() {
		Cursor cursor = db.query(tableName, null, null, null, null, null, null);
		return createModels(cursor);
	}
	
	abstract protected ContentValues modelToRow(T model);
	
	abstract protected void fillModelFromRow(T model, Cursor row);
	
	abstract protected String getTableName();
	
	abstract protected SQLiteDatabase getDb();
	
	abstract protected Object getPrimaryKey();

	abstract protected Class<T> getModelClass();
	
	protected boolean isPrimaryKeyComposite() {
		return primaryKey instanceof String[];
	}
	
	protected String getSqlWhereClauseForPrimaryKey(Object valueOfPrimaryKey) {
		
		if (isPrimaryKeyComposite()) {
			String[] primaryKeys = (String[]) primaryKey;
			String[] primaryKeyClauses = new String[primaryKeys.length]; 
			
			for (int i = 0; i < primaryKeys.length; i++) {
				primaryKeyClauses[i] = primaryKeys[i] + " = " + 
						String.valueOf(((Object[]) valueOfPrimaryKey)[i]);
			}
			
			return TextUtils.join(" AND ", primaryKeyClauses);
		}
		
		return primaryKey + " = " + String.valueOf(valueOfPrimaryKey);
	}

	/**
	 * 
	 * @return Null if identity map is not needed
	 */
	protected IdentityMap<T> createIdentityMap() {
		return new IdentityMap<T>();
	}
	
	protected DomainModelCollection<T> createModelCollectionInstance(int capacity) {
		return new DomainModelCollection<T>(capacity);
	}
	
	protected AbstractMapper() {
		tableName = getTableName();
		db = getDb();
		primaryKey = getPrimaryKey();
		identityMap = createIdentityMap();
	}
	
	/**
	 * 
	 * @return Null if cursor is empty
	 */
	protected T createModel(Cursor row, boolean closeCursor) {
		
		if (row.getCount() == 0) {
			
			if (closeCursor) {
				row.close();
			}
			
			return null;
		}
		
		T model = null;
		
		try {
			model = getModelClass().newInstance();
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
		
		fillModelFromRow(model, row);
		
		if (closeCursor) {
			row.close();
		}
		
		if (identityMap != null) {
			identityMap.put(model);
		}
		
		return model;
	}
	
	/**
	 * Same as {@link #createModel(Cursor, boolean)}, but always closes cursor.
	 */
	protected T createModel(Cursor row) {
		return createModel(row, true);
	}
	
	protected DomainModelCollection<T> createModels(Cursor rows) {
		DomainModelCollection<T> models;
		
		try {
			models = createModelCollectionInstance(rows.getCount());
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
		
		while (rows.moveToNext()) {
			models.add(createModel(rows, false));
		}
		
		rows.close();
		
		return models;
	}
}
