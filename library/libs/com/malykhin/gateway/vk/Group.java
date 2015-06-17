package com.malykhin.gateway.vk;

import android.text.Html;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class Group {
	public final long id;
	public final String name;
	
	public Group(long id, String name) {
		this.id = id;
		this.name = Html.fromHtml(name).toString();
	}
}
