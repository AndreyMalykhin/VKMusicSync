package com.malykhin.gateway.vk;

import android.text.Html;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class Album {
	
	public final long id;
	public final String title;
	public final long ownerId;
	public boolean isOwnerGroup;

	public Album(long id, long ownerId, String title, boolean isOwnerGroup) {
		this.id = id;
		this.isOwnerGroup = isOwnerGroup;
		this.ownerId = ownerId;
		this.title = Html.fromHtml(title).toString();
	}
	
	@Override
	public String toString() {
		return new StringBuilder()
			.append("id=")
			.append(id)
			.append("; ownerId=")
			.append(ownerId)
			.append("; isOwnerGroup=")
			.append(isOwnerGroup)
			.append("; title=")
			.append(title)
			.toString();
	}
}
