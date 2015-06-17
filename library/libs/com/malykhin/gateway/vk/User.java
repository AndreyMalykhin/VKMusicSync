package com.malykhin.gateway.vk;

import android.text.Html;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class User {
	public final long id;
	public final String firstName;
	public final String lastName;
	
	public User(long id, String firstName, String lastName) {
		this.id = id;
		this.firstName = Html.fromHtml(firstName).toString();
		this.lastName = Html.fromHtml(lastName).toString();
	}

	/**
	 * 
	 * @return First name + last name
	 */
	public String getFullName() {
		return firstName + " " + lastName;
	}
}
