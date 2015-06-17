package com.malykhin.vkmusicsync.model.track;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;

import android.text.Html;
import android.text.TextUtils;

import com.malykhin.gateway.vk.Track;
import com.malykhin.orm.AbstractDomainModel;
import com.malykhin.vkmusicsync.model.MusicDirectoryMapper;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.model.SyncedAlbum;
import com.malykhin.vkmusicsync.model.SyncedAlbumMapper;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class SyncedTrack extends AbstractDomainModel {
	
	private static final String TAG = SyncedTrack.class.getSimpleName();
	
	private long id;
	private MusicOwner owner;
	private Long albumId;
	private String artist;
	private String title;
	private int duration;
	private String filename;

	@Override
	public Long getIdentityField() {
		return getId();
	}
	
	@Override
	public SyncedTrack setIdentityField(Object identityField) {
		return setId((Long) identityField);
	}
	
	/**
	 * 
	 * @return Null if not set
	 */
	public MusicOwner getOwner() {
		return owner;
	}
	
	/**
	 * 
	 * @throws NullPointerException
	 */
	public SyncedTrack setOwner(MusicOwner owner) {
		
		if (owner == null) {
			throw new NullPointerException();
		}
		
		this.owner = owner;
		return this;
	}
	
	/**
	 * 
	 * @return 0 if not set
	 */
	public long getId() {
		return id;
	}
	
	/**
	 * 
	 * @throws IllegalArgumentException
	 */
	public SyncedTrack setId(long id) {
		
		if (id == 0) {
			throw new IllegalArgumentException("ID cant be 0");
		}
		
		this.id = id;
		return this;
	}
	
	/**
	 * 
	 * @return Null if not set
	 */
	public Long getAlbumId() {
		return albumId;
	}
	
	/**
	 * 
	 * @param albumId Can be null
	 */
	public SyncedTrack setAlbumId(Long albumId) {
		this.albumId = (albumId == null || albumId.equals(0L)) ? null : albumId;
		return this;
	}
	
	/**
	 * 
	 * @return Null if not set
	 */
	public String getArtist() {
		return artist;
	}
	
	/**
	 * 
	 * @throws NullPointerException
	 */
	public SyncedTrack setArtist(String artist) {
		
		if (artist == null) {
			throw new NullPointerException();
		}
		
		this.artist = Html.fromHtml(artist).toString();
		return this;
	}
	
	/**
	 * 
	 * @return Null if not set
	 */
	public String getTitle() {
		return title;
	}
	
	/**
	 * 
	 * @throws NullPointerException
	 */
	public SyncedTrack setTitle(String title) {
		
		if (title == null) {
			throw new NullPointerException();
		}
		
		this.title = Html.fromHtml(title).toString();
		return this;
	}
	
	public int getDuration() {
		return duration;
	}
	
	public SyncedTrack setDuration(int duration) {
		this.duration = duration;
		return this;
	}
	
	/**
	 * 
	 * @return Null if not set
	 */
	public String getFilename() {
		return filename;
	}
	
	/**
	 * 
	 * @throws NullPointerException
	 */
	public SyncedTrack setFilename(String filename) {
		
		if (filename == null) {
			throw new NullPointerException();
		}
		
		this.filename = filename;
		return this;
	}
	
	/**
	 * 
	 * @return artist + title
	 */
	public String getFullTitle() {
		return artist + " - " + title;
	}

	/**
	 * @throws IllegalStateException If filename is not set
	 * @throws IllegalStateException If owner is not set
	 */
	public File getFile() {
		
		if (filename == null) {
			throw new IllegalStateException("Filename is not set. See setFilename()");
		}
		
		if (owner == null) {
			throw new IllegalStateException("Owner is not set. See setOwner()");
		}
		
		if (albumId == null) {
			return FileUtils.getFile(
					MusicDirectoryMapper.getInstance().getOneByOwner(owner).getDirectory(), 
					filename
			);
		}
		
		return FileUtils.getFile(
				SyncedAlbumMapper.getInstance().getOneByIdentityField(albumId).getDir(), filename
		);
	}

	/**
	 * 
	 * @throws InvalidAudioFrameException 
	 * @throws ReadOnlyFileException 
	 * @throws TagException 
	 * @throws IOException 
	 * @throws CannotReadException 
	 */
	public boolean isTagsDiffersFrom(Track remoteTrack) throws CannotReadException, IOException, 
			TagException, ReadOnlyFileException, InvalidAudioFrameException 
	{
		MP3File mp3File = (MP3File) AudioFileIO.read(getFile());
		Tag tags = mp3File.getTagOrCreateAndSetDefault();
		String artistTag = tags.getFirst(FieldKey.ARTIST);
		String titleTag = tags.getFirst(FieldKey.TITLE);
			
		return !TextUtils.equals(artistTag, remoteTrack.artist) || 
				!TextUtils.equals(titleTag, remoteTrack.title);
	}
	
	public boolean isDiffersFrom(Track remoteTrack) {
		return !(
			((remoteTrack.albumId != null && remoteTrack.albumId.equals(albumId))
			|| (remoteTrack.albumId == null && albumId == null))
			&& remoteTrack.artist.equals(artist)
			&& remoteTrack.title.equals(title)
		);
	}

	/**
	 * 
	 * @return Null if no album
	 */
	public SyncedAlbum getAlbum() {
		return albumId == null ? null : 
			SyncedAlbumMapper.getInstance().getOneByIdentityField(albumId);
	}

}
