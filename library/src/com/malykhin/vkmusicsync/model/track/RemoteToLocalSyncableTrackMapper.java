package com.malykhin.vkmusicsync.model.track;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.malykhin.orm.AbstractMapper;
import com.malykhin.orm.DomainModelCollection;
import com.malykhin.orm.IdentityMap;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.util.Db;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class RemoteToLocalSyncableTrackMapper extends AbstractMapper<RemoteToLocalSyncableTrack> {

	private static final String TAG = RemoteToLocalSyncableTrackMapper.class.getSimpleName();
	
	private static RemoteToLocalSyncableTrackMapper instance = 
			new RemoteToLocalSyncableTrackMapper();

	public static RemoteToLocalSyncableTrackMapper getInstance() {
		return instance;
	}

	/**
	 * @throws NullPointerException 
	 */
	public long[] getAllIdsByOwner(MusicOwner owner) {
		Log.d(TAG, "getAllIdsByOwner()");
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		Cursor cursor = db.query(tableName, new String[]{"id"}, getSqlWhereClauseForOwner(owner), 
				null, null, null, null);
		long[] ids = new long[cursor.getCount()];
		
		while (cursor.moveToNext()) {
			ids[cursor.getPosition()] = cursor.getLong(cursor.getColumnIndex("id"));
		}

		cursor.close();
		
		return ids;
	}
	
	/**
	 * @throws NullPointerException 
	 */
	public DomainModelCollection<RemoteToLocalSyncableTrack> getAllByOwner(MusicOwner owner) {
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
	protected IdentityMap<RemoteToLocalSyncableTrack> createIdentityMap() {
		return null;
	}
	
	@Override
	protected ContentValues modelToRow(RemoteToLocalSyncableTrack model) {
		ContentValues row = new ContentValues(); 
		row.put("id", model.getId());
		row.put("user_id", model.getOwner().getId());
		row.put("is_user_group", model.getOwner().isGroup() ? 1 : 0);
		return row;
	}

	@Override
	protected void fillModelFromRow(RemoteToLocalSyncableTrack model, Cursor row) {
		MusicOwner owner = new MusicOwner();
		owner.setId(row.getLong(row.getColumnIndex("user_id")))
			.setGroup(row.getInt(row.getColumnIndex("is_user_group")) == 1 ? true : false);
		
		model.setId(row.getLong(row.getColumnIndex("id")))
			.setOwner(owner);

	}

	@Override
	protected String getTableName() {
		return "remote_to_local_syncable_tracks";
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
	protected Class<RemoteToLocalSyncableTrack> getModelClass() {
		return RemoteToLocalSyncableTrack.class;
	}

}
