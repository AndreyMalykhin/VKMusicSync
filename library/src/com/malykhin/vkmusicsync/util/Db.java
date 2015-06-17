package com.malykhin.vkmusicsync.util;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Application;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.model.MusicDirectory;


/**
 * 
 * @author Andrey Malykhin
 *
 */
public class Db {
	
	private static class DbHelper extends SQLiteOpenHelper {

		public DbHelper() {
			super(Application.getContext(), DB_NAME, null, DB_VERSION);
		}

		@Override
		public void onOpen(SQLiteDatabase db) {
			super.onOpen(db);
			
			if (!db.isReadOnly()) {
				db.execSQL("PRAGMA foreign_keys=ON");
			}
		}
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.d(TAG, "onCreate()");
			
			db.execSQL("CREATE TABLE synced_albums (" +
					"id INTEGER PRIMARY KEY NOT NULL, " +
					"owner_id INTEGER NOT NULL, " +
					"title VARCHAR NOT NULL, " +
					"dir_name VARCHAR NOT NULL, " +
					"is_owner_group BOOLEAN NOT NULL DEFAULT 0)"
			);
			db.execSQL("CREATE UNIQUE INDEX synced_albums_owner_id_is_owner_group_dir_name " + 
					"ON synced_albums (owner_id ASC, is_owner_group ASC, dir_name ASC)");
			
			db.execSQL("CREATE TABLE synced_tracks (" +
					"_id INTEGER PRIMARY KEY NOT NULL , " +
					"album_id INTEGER REFERENCES synced_albums(id) ON DELETE SET NULL, " +
					"artist VARCHAR, " +
					"title VARCHAR, " +
					"filename VARCHAR NOT NULL, " +
					"user_id INTEGER NOT NULL, " +
					"duration INTEGER NOT NULL, " +
					"is_user_group BOOLEAN NOT NULL DEFAULT 0)"
			);
			db.execSQL("CREATE INDEX synced_tracks_artist_title " + 
					"ON synced_tracks (artist ASC, title ASC)");
			db.execSQL("CREATE INDEX synced_tracks_user_id_is_user_group " + 
					"ON synced_tracks (user_id ASC, is_user_group ASC)");
			
			db.execSQL("CREATE TABLE remote_to_local_syncable_tracks (" + 
					"id INTEGER PRIMARY KEY NOT NULL, " +
					"user_id INTEGER NOT NULL, " + 
					"is_user_group BOOLEAN NOT NULL DEFAULT 0)"
			);
			db.execSQL("CREATE INDEX remote_to_local_syncable_tracks_user_id_is_user_group " + 
					"ON remote_to_local_syncable_tracks (user_id ASC, is_user_group ASC)");
			
			db.execSQL("CREATE TABLE local_to_remote_syncable_tracks (" + 
					"id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
					"user_id INTEGER NOT NULL, " + 
					"filename VARCHAR NOT NULL, " +
					"album VARCHAR, " +
					"is_user_group BOOLEAN NOT NULL DEFAULT 0)"
			);
			db.execSQL("CREATE UNIQUE INDEX local_to_remote_syncable_tracks_user_id_is_user_group_album_filename " 
					+ "ON local_to_remote_syncable_tracks (user_id ASC, is_user_group ASC, album ASC, filename ASC)");
			
			db.execSQL("CREATE TABLE music_directories (" + 
					"user_id INTEGER NOT NULL, " +
					"path VARCHAR NOT NULL, " +
					"is_user_group BOOLEAN NOT NULL DEFAULT 0, " +
					"PRIMARY KEY (user_id, is_user_group))"
			);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.d(TAG, "onUpgrade(); oldVersion=" + oldVersion + "; newVersion=" + newVersion);

			switch (oldVersion) {
				case 9:
					db.execSQL("CREATE TABLE syncable_tracks (id INTEGER PRIMARY KEY NOT NULL)");
					oldVersion++;
				case 10:
					db.execSQL("CREATE TABLE music_directories (" + 
							"user_id INTEGER PRIMARY KEY NOT NULL, " +
							"path VARCHAR NOT NULL)"
					);
					ContentValues row = new ContentValues();
					long userId = preferences.getUserId();
					row.put("user_id", userId);
					String musicDir = preferences.getSharedPreferences().getString(
							Preferences.MUSIC_DIRECTORY, 
							MusicDirectory.getDefaultDirectory().getAbsolutePath()
					);
					row.put("path", musicDir);
					db.insert("music_directories", null, row);
					
					db.execSQL("ALTER TABLE tracks ADD COLUMN user_id INTEGER NOT NULL DEFAULT 0");
					db.execSQL("CREATE INDEX tracks_user_id ON tracks (user_id ASC)");
					
					db.execSQL("ALTER TABLE syncable_tracks " + 
							"ADD COLUMN user_id INTEGER NOT NULL DEFAULT 0");
					db.execSQL("CREATE INDEX syncable_tracks_user_id ON " + 
							"syncable_tracks (user_id ASC)");
					
					row = new ContentValues();
					row.put("user_id", userId);
					db.update("tracks", row, "user_id = 0", null);
					db.update("syncable_tracks", row, "user_id = 0", null);
					
					oldVersion++;
				case 11:
					db.execSQL("ALTER TABLE tracks RENAME TO synced_tracks");
					db.execSQL("DROP INDEX artist_title");
					db.execSQL("DROP INDEX tracks_user_id");
					db.execSQL("CREATE INDEX synced_tracks_artist_title " + 
							"ON synced_tracks (artist ASC, title ASC)");
					db.execSQL("CREATE INDEX synced_tracks_user_id " + 
							"ON synced_tracks (user_id ASC)");
					
					db.execSQL("ALTER TABLE syncable_tracks " + 
							"RENAME TO remote_to_local_syncable_tracks");
					db.execSQL("DROP INDEX syncable_tracks_user_id");
					db.execSQL("CREATE INDEX remote_to_local_syncable_tracks_user_id " + 
							"ON remote_to_local_syncable_tracks (user_id ASC)");
					
					db.execSQL("CREATE TABLE local_to_remote_syncable_tracks (" + 
							"id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
							"user_id INTEGER NOT NULL, " + 
							"filename VARCHAR NOT NULL)"
					);
					db.execSQL("CREATE UNIQUE INDEX local_to_remote_syncable_tracks_user_id_filename" 
							+ " ON local_to_remote_syncable_tracks (user_id ASC, filename ASC)");
					
					oldVersion++;
				case 12:
					db.execSQL("CREATE TABLE synced_albums (" +
							"id INTEGER PRIMARY KEY NOT NULL, " +
							"owner_id INTEGER NOT NULL, " +
							"title VARCHAR NOT NULL, " +
							"dir_name VARCHAR NOT NULL)"
					);
					db.execSQL("CREATE UNIQUE INDEX synced_albums_owner_id_dir_name " + 
							"ON synced_albums (owner_id ASC, dir_name ASC)");
					
					db.execSQL("UPDATE synced_tracks SET album_id = NULL");
					db.execSQL("ALTER TABLE synced_tracks RENAME TO synced_tracks_old");
					db.execSQL("CREATE TABLE synced_tracks (" +
							"_id INTEGER PRIMARY KEY NOT NULL , " +
							"album_id INTEGER REFERENCES synced_albums(id) ON DELETE SET NULL, " +
							"artist VARCHAR, " +
							"title VARCHAR, " +
							"filename VARCHAR NOT NULL, " +
							"user_id INTEGER NOT NULL, " +
							"duration INTEGER NOT NULL)"
					);
					db.execSQL("INSERT INTO synced_tracks SELECT * FROM synced_tracks_old");
					db.execSQL("DROP TABLE synced_tracks_old");
					db.execSQL("CREATE INDEX synced_tracks_artist_title " + 
							"ON synced_tracks (artist ASC, title ASC)");
					db.execSQL("CREATE INDEX synced_tracks_user_id " + 
							"ON synced_tracks (user_id ASC)");
					
					db.execSQL("DROP INDEX local_to_remote_syncable_tracks_user_id_filename");
					db.execSQL(
							"ALTER TABLE local_to_remote_syncable_tracks ADD COLUMN album VARCHAR");
					db.execSQL("CREATE UNIQUE INDEX " + 
							"local_to_remote_syncable_tracks_user_id_album_filename ON " + 
							"local_to_remote_syncable_tracks (user_id ASC, album ASC, filename ASC)");
					
					oldVersion++;
				case 13:
					db.execSQL("ALTER TABLE synced_albums ADD COLUMN " + 
							"is_owner_group BOOLEAN NOT NULL DEFAULT 0");
					db.execSQL("DROP INDEX synced_albums_owner_id_dir_name");
					db.execSQL("CREATE UNIQUE INDEX synced_albums_owner_id_is_owner_group_dir_name " 
							+ "ON synced_albums (owner_id ASC, is_owner_group ASC, dir_name ASC)");
					
					db.execSQL("ALTER TABLE synced_tracks ADD COLUMN " + 
							"is_user_group BOOLEAN NOT NULL DEFAULT 0");
					db.execSQL("DROP INDEX synced_tracks_user_id");
					db.execSQL("CREATE INDEX synced_tracks_user_id_is_user_group " + 
							"ON synced_tracks (user_id ASC, is_user_group ASC)");
					
					db.execSQL("ALTER TABLE remote_to_local_syncable_tracks ADD COLUMN " + 
							"is_user_group BOOLEAN NOT NULL DEFAULT 0");
					db.execSQL("DROP INDEX remote_to_local_syncable_tracks_user_id");
					db.execSQL("CREATE INDEX remote_to_local_syncable_tracks_user_id_is_user_group " 
							+ "ON remote_to_local_syncable_tracks (user_id ASC, is_user_group ASC)");
					
					db.execSQL("ALTER TABLE local_to_remote_syncable_tracks ADD COLUMN " + 
							"is_user_group BOOLEAN NOT NULL DEFAULT 0");
					db.execSQL("DROP INDEX local_to_remote_syncable_tracks_user_id_album_filename");
					db.execSQL("CREATE UNIQUE INDEX local_to_remote_syncable_tracks_user_id_is_user_group_album_filename " 
							+ "ON local_to_remote_syncable_tracks (user_id ASC, is_user_group ASC, album ASC, filename ASC)");
					
					db.execSQL("ALTER TABLE music_directories RENAME TO music_directories_old");
					db.execSQL("CREATE TABLE music_directories (" + 
							"user_id INTEGER NOT NULL, " +
							"path VARCHAR NOT NULL, " +
							"is_user_group BOOLEAN NOT NULL DEFAULT 0, " +
							"PRIMARY KEY (user_id, is_user_group))"
					);
					db.execSQL("INSERT INTO music_directories " + 
							"SELECT user_id, path, 0 FROM music_directories_old");
					db.execSQL("DROP TABLE music_directories_old");
					
					oldVersion++;
			}
		}
		
	}
	
	private static final String TAG = Db.class.getSimpleName();
	private static final int DB_VERSION = 14;
	private static final String DB_NAME = "vk_music_sync";
	private static SQLiteDatabase db;
	private static Preferences preferences;
	
	/**
	 * 
	 * @throws SQLiteException If db cant be opened
	 */
	public static void init(Preferences preference) {
		preferences = preference;
		get();
	}
	
	/**
	 * 
	 * @throws SQLiteException If db cant be opened
	 */
	public static synchronized SQLiteDatabase get() {
		
		if (db == null) {
			DbHelper dbHelper = new DbHelper();
			
			try {
				db = dbHelper.getWritableDatabase();
			}
			catch (SQLiteException exception) {
				db = dbHelper.getReadableDatabase();
			}
		}
		
		return db;
	}
	
	private Db() {}
}
