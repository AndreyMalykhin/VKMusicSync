package com.malykhin.vkmusicsync.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.malykhin.orm.AbstractMapper;
import com.malykhin.vkmusicsync.util.Db;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class MusicDirectoryMapper extends AbstractMapper<MusicDirectory> {

	private static final String TAG = MusicDirectoryMapper.class.getSimpleName();
	
	private static MusicDirectoryMapper instance = new MusicDirectoryMapper();

	public static MusicDirectoryMapper getInstance() {
		return instance;
	}
	
	/**
	 * @throws NullPointerException
	 */
	public MusicDirectory getOneByOwner(MusicOwner owner) {
		
		if (owner == null) {
			throw new NullPointerException();
		}

		return getOneByIdentityField(new Object[]{owner.getId(), owner.isGroup() ? 1 : 0});
	}
	
	@Override
	protected ContentValues modelToRow(MusicDirectory model) {
		ContentValues row = new ContentValues();
		row.put("user_id", model.getOwner().getId());
		row.put("is_user_group", model.getOwner().isGroup() ? 1 : 0);
		row.put("path", model.getDirectory().getAbsolutePath());
		return row;
	}

	@Override
	protected void fillModelFromRow(MusicDirectory model, Cursor row) {
		MusicOwner owner = new MusicOwner();
		owner.setId(row.getLong(row.getColumnIndex("user_id")))
			.setGroup(row.getInt(row.getColumnIndex("is_user_group")) == 1 ? true : false);
		
		model.setOwner(owner)
			.setDirectory(row.getString(row.getColumnIndex("path")));
	}

	@Override
	protected String getTableName() {
		return "music_directories";
	}

	@Override
	protected SQLiteDatabase getDb() {
		return Db.get();
	}

	@Override
	protected String[] getPrimaryKey() {
		return new String[]{"user_id", "is_user_group"};
	}

	@Override
	protected Class<MusicDirectory> getModelClass() {
		return MusicDirectory.class;
	}

}
