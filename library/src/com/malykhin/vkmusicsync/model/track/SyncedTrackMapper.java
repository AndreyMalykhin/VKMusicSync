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
public class SyncedTrackMapper extends AbstractMapper<SyncedTrack> {

	private static final String TAG = SyncedTrackMapper.class.getSimpleName();
	
	private static SyncedTrackMapper instance = new SyncedTrackMapper();

	public static SyncedTrackMapper getInstance() {
		return instance;
	}
	
	/**
	 * @throws NullPointerException
	 */
	public DomainModelCollection<SyncedTrack> getAllByOwner(MusicOwner owner) {
		Log.d(TAG, "getAllByOwner()");
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		String sqlWhere = "user_id = " + String.valueOf(owner.getId()) + " AND is_user_group = " + 
				(owner.isGroup() ? 1 : 0);
		Cursor cursor = db.query(tableName, null, sqlWhere, null, null, null, 
				"artist ASC, title ASC");
		return createModels(cursor);
	}
	
	@Override
	protected IdentityMap<SyncedTrack> createIdentityMap() {
		return null;
	}
	
	@Override
	protected ContentValues modelToRow(SyncedTrack model) {
		ContentValues row = new ContentValues(); 
		row.put("_id", model.getId());
		row.put("user_id", model.getOwner().getId());
		row.put("is_user_group", model.getOwner().isGroup() ? 1 : 0);
		row.put("album_id", model.getAlbumId());
		row.put("artist", model.getArtist());
		row.put("duration", model.getDuration());
		row.put("title", model.getTitle());
		row.put("filename", model.getFilename());
		
		return row;
	}

	@Override
	protected void fillModelFromRow(SyncedTrack model, Cursor row) {
		MusicOwner owner = new MusicOwner();
		owner.setId(row.getLong(row.getColumnIndex("user_id")))
			.setGroup(row.getInt(row.getColumnIndex("is_user_group")) == 1 ? true : false);
		
		model.setAlbumId(row.getLong(row.getColumnIndex("album_id")))
			.setOwner(owner)
			.setArtist(row.getString(row.getColumnIndex("artist")))
			.setDuration(row.getInt(row.getColumnIndex("duration")))
			.setFilename(row.getString(row.getColumnIndex("filename")))
			.setId(row.getLong(row.getColumnIndex("_id")))
			.setTitle(row.getString(row.getColumnIndex("title")));
	}

	@Override
	protected String getTableName() {
		return "synced_tracks";
	}

	@Override
	protected SQLiteDatabase getDb() {
		return Db.get();
	}

	@Override
	protected String getPrimaryKey() {
		return "_id";
	}

	@Override
	protected Class<SyncedTrack> getModelClass() {
		return SyncedTrack.class;
	}

}
