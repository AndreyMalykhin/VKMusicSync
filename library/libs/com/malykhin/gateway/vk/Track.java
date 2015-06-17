package com.malykhin.gateway.vk;

import android.text.Html;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class Track {
	public final long id;
	public final Long albumId;
	public final String artist;
	public final String title;
	public final int duration;
	public final String url;
	
	/**
	 * 
	 * @param id
	 * @param artist
	 * @param title
	 * @param duration In seconds
	 * @param url
	 * @param albumId Can be null
	 */
	public Track(long id, String artist, String title, int duration, String url, Long albumId) {
		this.id = id;
		this.albumId = albumId;
		this.artist = Html.fromHtml(artist).toString();
		this.title = Html.fromHtml(title).toString();
		this.duration = duration;
		this.url = url;
	}
	
	@Override
	public String toString() {
		return new StringBuilder()
			.append("id=")
			.append(id)
			.append("; albumId=")
			.append(albumId)
			.append("; artist=")
			.append(artist)
			.append("; title=")
			.append(title)
			.append("; duration=")
			.append(duration)
			.append("; url=")
			.append(url)
			.toString();
	}

	public String getFullTitle() {
		return artist + " - " + title;
	}
}
