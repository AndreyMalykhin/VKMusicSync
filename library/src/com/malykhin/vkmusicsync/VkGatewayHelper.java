package com.malykhin.vkmusicsync;

import com.malykhin.gateway.vk.VkGateway;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class VkGatewayHelper {
	
	public static synchronized VkGateway getGateway() {
		VkGateway vkGateway = VkGateway.getInstance();

		if (vkGateway.getAccessToken() == null) {
			String accessToken = Preferences.getInstance().getSharedPreferences().getString(
					Preferences.ACCESS_TOKEN, null);
			
			if (accessToken != null) {
				vkGateway.setAccessToken(accessToken);
			}
		}
		
		if (vkGateway.getLoggedUserId() == null) {
			long userId = Preferences.getInstance().getUserId();
			
			if (userId != 0) {
				vkGateway.setLoggedUserId(userId);
			}
		}
		
		return vkGateway;
	}
}
