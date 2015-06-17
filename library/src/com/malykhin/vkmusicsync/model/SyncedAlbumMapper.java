package com.malykhin.vkmusicsync.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.malykhin.orm.AbstractMapper;
import com.malykhin.orm.DomainModelCollection;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.util.Db;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class SyncedAlbumMapper extends AbstractMapper<SyncedAlbum> {

	private static final String TAG = SyncedAlbumMapper.class.getSimpleName();
	
	private static final SyncedAlbumMapper instance = new SyncedAlbumMapper();

	public static SyncedAlbumMapper getInstance() {
		return instance;
	}

	/**
	 * 
	 * @return Null if not found
	 * @throws NullPointerException
	 */
	public SyncedAlbum getOneByOwnerAndDirName(MusicOwner owner, String dirName) {
		Log.d(TAG, "getOneByOwnerAndDirName()");
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		if (TextUtils.isEmpty(dirName)) {
			throw new IllegalArgumentException("Dir name cant be empty");
		}
		
		String sqlWhere = getSqlWhereClauseForOwner(owner) + " AND dir_name = " + 
				DatabaseUtils.sqlEscapeString(dirName);
		Cursor cursor = db.query(tableName, null, sqlWhere, null, null, null, null, "1");
		cursor.moveToFirst();
		
		return createModel(cursor);
	}
	
	/**
	 * @throws NullPointerException
	 */
	public DomainModelCollection<SyncedAlbum> getAllByOwner(MusicOwner owner) {
		Log.d(TAG, "getAllByOwner()");
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		Cursor cursor = db.query(tableName, null, getSqlWhereClauseForOwner(owner), null, null, 
				null, "title ASC");
		return createModels(cursor);
	}

	protected String getSqlWhereClauseForOwner(MusicOwner owner) {
		return "owner_id = " + String.valueOf(owner.getId()) + " AND is_owner_group = " + 
				(owner.isGroup() ? 1 : 0);
	}
	
	@Override
	protected ContentValues modelToRow(SyncedAlbum model) {
		ContentValues row = new ContentValues(); 
		row.put("id", model.getId());
		row.put("owner_id", model.getOwner().getId());
		row.put("is_owner_group", model.getOwner().isGroup() ? 1 : 0);
		row.put("title", model.getTitle());
		row.put("dir_name", model.getDirName());
		
		return row;
	}

	@Override
	protected void fillModelFromRow(SyncedAlbum model, Cursor row) {
		MusicOwner owner = new MusicOwner();
		owner.setId(row.getLong(row.getColumnIndex("owner_id")))
			.setGroup(row.getInt(row.getColumnIndex("is_owner_group")) == 1 ? true : false);
		
		model.setOwner(owner)
			.setId(row.getLong(row.getColumnIndex("id")))
			.setTitle(row.getString(row.getColumnIndex("title")))
			.setDirName(row.getString(row.getColumnIndex("dir_name")));
	}

	@Override
	protected String getTableName() {
		return "synced_albums";
	}

	@Override
	protected SQLiteDatabase getDb() {
		return Db.get();
	}

	@Override
	protected String getPrimaryKey() {
		return "id";
	}

	@Override
	protected Class<SyncedAlbum> getModelClass() {
		return SyncedAlbum.class;
	}

}
