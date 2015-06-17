package com.malykhin.vkmusicsync.model.track;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.malykhin.orm.AbstractMapper;
import com.malykhin.orm.IdentityMap;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.util.Db;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class LocalToRemoteSyncableTrackMapper extends AbstractMapper<LocalToRemoteSyncableTrack> {

	private static final String TAG = LocalToRemoteSyncableTrackMapper.class.getSimpleName();
	
	private static LocalToRemoteSyncableTrackMapper instance = 
			new LocalToRemoteSyncableTrackMapper();

	public static LocalToRemoteSyncableTrackMapper getInstance() {
		return instance;
	}

	/**
	 * @param owner
	 * @param album Can be null
	 * @param filename
	 * @throws IllegalArgumentException 
	 * @throws NullPointerException
	 */
	public boolean deleteByOwnerAndAlbumAndFilename(MusicOwner owner, String album, String filename) {
		Log.d(TAG, "deleteByOwnerAndAlbumAndFilename(); album=" + album + "; filename=" + filename);
		
		if (TextUtils.isEmpty(filename)) {
			throw new IllegalArgumentException("Filename cant be empty");
		}

		if (owner == null) {
			throw new NullPointerException();
		}

		String sqlWhere = getSqlWhereClauseForOwner(owner) + " AND ";
		
		if (album == null) {
			sqlWhere += "album IS NULL";
		} else {
			sqlWhere += "album = " + DatabaseUtils.sqlEscapeString(album);
		}
		
		sqlWhere += " AND filename = " + DatabaseUtils.sqlEscapeString(filename);
		
		return db.delete(tableName, sqlWhere, null) > 0;
	}
	
	/**
	 * @throws NullPointerException 
	 */
	public LocalToRemoteSyncableTrackCollection getAllByOwner(MusicOwner owner) {
		Log.d(TAG, "getAllByOwner()");
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		Cursor cursor = db.query(tableName, null, getSqlWhereClauseForOwner(owner), null, null, 
				null, null);
		
		return createModels(cursor);
	}
	
	/**
	 * @throws NullPointerException 
	 */
	public int getCountByOwner(MusicOwner owner) {
		Log.d(TAG, "getCountByOwner()");
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		Cursor cursor = db.query(tableName, new String[]{"COUNT(*) as c"}, 
				getSqlWhereClauseForOwner(owner), null, null, null, null);
		
		int result = 0;
		
		if (cursor.moveToNext()) {
			result = cursor.getInt(cursor.getColumnIndex("c"));
		}
		
		cursor.close();
		
		return result;
	}
	
	protected String getSqlWhereClauseForOwner(MusicOwner owner) {
		return "user_id = " + String.valueOf(owner.getId()) + " AND is_user_group = " + 
				(owner.isGroup() ? 1 : 0);
	}
	
	@Override
	protected LocalToRemoteSyncableTrackCollection createModels(Cursor rows) {
		return (LocalToRemoteSyncableTrackCollection) super.createModels(rows);
	}
	
	@Override
	protected LocalToRemoteSyncableTrackCollection createModelCollectionInstance(int capacity) {
		return new LocalToRemoteSyncableTrackCollection(capacity);
	}
	
	@Override
	protected IdentityMap<LocalToRemoteSyncableTrack> createIdentityMap() {
		return null;
	}
	
	@Override
	protected ContentValues modelToRow(LocalToRemoteSyncableTrack model) {
		ContentValues row = new ContentValues(); 
		row.put("id", model.getId() == 0 ? null : model.getId());
		row.put("user_id", model.getOwner().getId());
		row.put("is_user_group", model.getOwner().isGroup() ? 1 : 0);
		row.put("filename", model.getFilename());
		row.put("album", model.getAlbum());
		return row;
	}

	@Override
	protected void fillModelFromRow(LocalToRemoteSyncableTrack model, Cursor row) {
		MusicOwner owner = new MusicOwner();
		owner.setId(row.getLong(row.getColumnIndex("user_id")))
			.setGroup(row.getInt(row.getColumnIndex("is_user_group")) == 1 ? true : false);
		
		model.setId(row.getLong(row.getColumnIndex("id")))
			.setOwner(owner)
			.setFilename(row.getString(row.getColumnIndex("filename")))
			.setAlbum(row.getString(row.getColumnIndex("album")));

	}

	@Override
	protected String getTableName() {
		return "local_to_remote_syncable_tracks";
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
	protected Class<LocalToRemoteSyncableTrack> getModelClass() {
		return LocalToRemoteSyncableTrack.class;
	}

}
