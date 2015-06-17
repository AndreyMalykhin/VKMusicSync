package com.malykhin.gateway.vk;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.net.http.AndroidHttpClient;
import android.text.TextUtils;
import android.webkit.WebView;

import com.malykhin.gateway.vk.Auth.AccessScope;
import com.malykhin.gateway.vk.Auth.OnCancelListener;
import com.malykhin.gateway.vk.Auth.OnErrorListener;
import com.malykhin.gateway.vk.Auth.OnFinishLoadingListener;
import com.malykhin.gateway.vk.Auth.OnStartLoadingListener;
import com.malykhin.gateway.vk.Auth.OnSuccessListener;
import com.malykhin.http.MultipartEntityWithProgress;
import com.malykhin.io.OutputStreamWithProgress.ProgressListener;
import com.malykhin.util.Log;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class VkGateway {

	private static final String TAG = VkGateway.class.getSimpleName();
	private static final VkGateway instance = new VkGateway();
	private Map<Thread, List<HttpClient>> httpClients = Collections.synchronizedMap(
			new WeakHashMap<Thread, List<HttpClient>>());
	
	public static VkGateway getInstance() {
		return instance;
	}
	
	/**
	 * Moves tracks of currently logged user to album.
	 * 
	 * @return True on success
	 * @throws IOException
	 * @throws JSONException
	 * @throws VkException
	 */
	public boolean moveTracksToAlbum(long[] trackIds, long albumId) throws IOException, 
		JSONException, VkException  
	{
		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		params.add(new BasicNameValuePair("aids", TextUtils.join(",", 
				ArrayUtils.toObject(trackIds))));
		params.add(new BasicNameValuePair("album_id", String.valueOf(albumId)));
		
		return sendRequest(new HttpPost(getRequestUrl("audio.moveToAlbum", params)))
				.getInt("response") == 1;
	}
	
	/**
	 * Adds album to the currently logged user.
	 * 
	 * @return album ID
	 * @throws IOException
	 * @throws JSONException
	 * @throws VkException
	 */
	public long addAlbum(String title) throws IOException, JSONException, VkException {
		List<NameValuePair> params = new ArrayList<NameValuePair>(1);
		params.add(new BasicNameValuePair("title", title));
		
		return sendRequest("audio.addAlbum", params).getJSONObject("response").getLong("album_id");
	}
	
	/**
	 * @return True if successfully
	 * @throws IOException
	 * @throws JSONException
	 * @throws VkException
	 * @throws IllegalArgumentException
	 */
	public boolean deleteTrack(long userId, long trackId) throws IOException, JSONException, 
		VkException 
	{
		if (userId == 0) {
			throw new IllegalArgumentException("User ID cant be 0");
		}
		
		if (trackId == 0) {
			throw new IllegalArgumentException("track ID cant be 0");
		}
		
		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		params.add(new BasicNameValuePair("aid", String.valueOf(trackId)));
		params.add(new BasicNameValuePair("oid", String.valueOf(userId)));
		
		return sendRequest("audio.delete", params).getInt("response") == 1;
	}
	
	/**
	 * @return new ID of the track
	 * @throws IOException
	 * @throws JSONException
	 * @throws VkException
	 * @throws IllegalArgumentException
	 */
	public long copyTrack(long trackId, long ownerId, boolean isOwnerGroup) 
			throws IOException, JSONException, VkException 
	{
		if (trackId == 0) {
			throw new IllegalArgumentException("Track ID cant be 0");
		}
		
		if (ownerId == 0) {
			throw new IllegalArgumentException("Owner ID cant be 0");
		}
		
		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		params.add(new BasicNameValuePair("aid", String.valueOf(trackId)));
		params.add(new BasicNameValuePair("oid", String.valueOf(isOwnerGroup ? -ownerId : ownerId)));
		
		return sendRequest("audio.add", params).getLong("response");
	}
	
	/**
	 * 
	 * @throws FileNotFoundException
	 * @throws IOException 
	 * @throws JSONException 
	 * @throws VkException 
	 */
	public Track addTrack(File file, String artist, String title, ProgressListener progressListener) 
			throws JSONException, IOException, VkException 
	{
		if (!file.exists()) {
			throw new FileNotFoundException(file.toString());
		}
		
		String uploadUrl = sendRequest("audio.getUploadServer").getJSONObject("response")
				.getString("upload_url");

		HttpPost postRequest = new HttpPost(uploadUrl);
		MultipartEntity entity = new MultipartEntityWithProgress(
				HttpMultipartMode.BROWSER_COMPATIBLE, progressListener);
		entity.addPart("file", new FileBody(file, "audio/mpeg"));
		postRequest.setEntity(entity);
		JSONObject uploadResponse = sendRequest(postRequest);
		
		List<NameValuePair> params = new ArrayList<NameValuePair>(5);
		params.add(new BasicNameValuePair("server", uploadResponse.getString("server")));
		params.add(new BasicNameValuePair("audio", uploadResponse.getString("audio")));
		params.add(new BasicNameValuePair("hash", uploadResponse.getString("hash")));
		params.add(new BasicNameValuePair("artist", artist));
		params.add(new BasicNameValuePair("title", title));
		JSONObject jsonTrack = (JSONObject) sendRequest("audio.save", params).getJSONObject(
				"response");
		
		return new Track(
				jsonTrack.getLong("aid"), 
				jsonTrack.getString("artist"), 
				jsonTrack.getString("title"), 
				jsonTrack.getInt("duration"), 
				jsonTrack.getString("url"), 
				null
		);
	}
	
	/**
	 * 
	 * @throws IOException 
	 * @throws JSONException 
	 * @throws VkException 
	 */
	public ArrayList<Album> getAlbums(long ownerId, boolean isOwnerGroup) 
			throws JSONException, IOException, VkException 
	{
		ArrayList<Album> albums = new ArrayList<Album>();
		int albumsCountInResponse = 0;
		int maxAlbumsCountInResponse = 100;
		int offset = 0;
		
		do {
			List<NameValuePair> params = new ArrayList<NameValuePair>(3);
			params.add(new BasicNameValuePair(isOwnerGroup ? "gid" : "uid", 
					String.valueOf(ownerId)));
			params.add(new BasicNameValuePair("count", String.valueOf(maxAlbumsCountInResponse)));
			params.add(new BasicNameValuePair("offset", String.valueOf(offset)));
			HttpPost request = new HttpPost(getRequestUrl("audio.getAlbums", null));
			request.setEntity(new UrlEncodedFormEntity(params));
			JSONArray response = sendRequest(request).getJSONArray("response");
			albumsCountInResponse = response.length() - 1;
			
			for (int i = 0; i < albumsCountInResponse; i++) {
				JSONObject jsonAlbum = response.getJSONObject(i + 1);
				Album album = new Album(
						jsonAlbum.getLong("album_id"), 
						Math.abs(jsonAlbum.getLong("owner_id")), 
						jsonAlbum.getString("title"), 
						isOwnerGroup
				);
				albums.add(album);
			}
			
			offset += albumsCountInResponse;
		} while (albumsCountInResponse == maxAlbumsCountInResponse);
		
		return albums;
	}
	
	/**
	 * 
	 * @throws IOException 
	 * @throws JSONException 
	 * @throws VkException 
	 */
	public ArrayList<Group> getGroups(long userId) 
			throws JSONException, IOException, VkException 
	{
		ArrayList<Group> albums = new ArrayList<Group>();
		int groupsCountInResponse = 0;
		int maxGroupsCountInResponse = 1000;
		int offset = 0;
		
		do {
			List<NameValuePair> params = new ArrayList<NameValuePair>(4);
			params.add(new BasicNameValuePair("uid", String.valueOf(userId)));
			params.add(new BasicNameValuePair("count", 
					String.valueOf(maxGroupsCountInResponse)));
			params.add(new BasicNameValuePair("offset", String.valueOf(offset)));
			params.add(new BasicNameValuePair("extended", "1"));
			HttpPost request = new HttpPost(getRequestUrl("groups.get", null));
			request.setEntity(new UrlEncodedFormEntity(params));
			JSONArray response = sendRequest(request).getJSONArray("response");
			groupsCountInResponse = response.length() - 1;
			
			for (int i = 0; i < groupsCountInResponse; i++) {
				JSONObject jsonGroup = response.getJSONObject(i + 1);
				Group group = new Group(
						jsonGroup.getLong("gid"), 
						jsonGroup.getString("name")
				);
				albums.add(group);
			}
			
			offset += groupsCountInResponse;
		} while (groupsCountInResponse == maxGroupsCountInResponse);
		
		return albums;
	}
	
	/**
	 * 
	 * @throws IOException 
	 * @throws JSONException 
	 * @throws VkException 
	 */
	public Track[] getTracks(long ownerId, boolean isOwnerGroup) 
			throws JSONException, IOException, VkException 
	{
		return getTracksInternal(ownerId, isOwnerGroup, null);
	}
	
	/**
	 * 
	 * @throws IOException 
	 * @throws JSONException 
	 * @throws VkException 
	 */
	public Track[] getTracks(long ownerId, boolean isOwnerGroup, long[] trackIds) 
			throws JSONException, IOException, VkException 
	{
		return getTracksInternal(ownerId, isOwnerGroup, trackIds);
	}

	/**
	 * @throws IOException 
	 * @throws JSONException 
	 * @throws VkException 
	 */
	public User getUserById(long id) throws IOException, JSONException, VkException {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>(1);
		params.add(new BasicNameValuePair("uids", String.valueOf(id)));
		
		JSONArray response = sendRequest("getProfiles", params).getJSONArray("response");
		JSONObject jsonUser = response.getJSONObject(0);
		
		return new User(jsonUser.getLong("uid"), jsonUser.getString("first_name"), 
			jsonUser.getString("last_name"));
	}
	
	/**
	 * 
	 * @throws JSONException
	 * @throws IOException
	 * @throws VkException 
	 */
	public int getTracksCount(long ownerId, boolean isOwnerGroup) 
			throws JSONException, IOException, VkException 
	{
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>(1);
		params.add(new BasicNameValuePair("oid", String.valueOf(isOwnerGroup ? -ownerId : ownerId)));
		
		return (int) sendRequest("audio.getCount", params).getLong("response");
	}
	
	/**
	 * 
	 * @throws JSONException
	 * @throws IOException
	 * @throws VkException 
	 */
	public User[] getUserFriends(long userId) throws JSONException, IOException, VkException {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>(2);
		params.add(new BasicNameValuePair("uid", String.valueOf(userId)));
		params.add(new BasicNameValuePair("fields", "uid,first_name,last_name"));
		
		JSONArray response = sendRequest("friends.get", params).getJSONArray("response");
		User[] friends = new User[response.length()];
		
		for (int i = 0; i < response.length(); i++) {
			JSONObject friendJson = response.getJSONObject(i);
			friends[i] = new User(friendJson.getLong("uid"), friendJson.getString("first_name"), 
					friendJson.getString("last_name")); 
		}
		
		return friends;
	}
	
	/**
	 * 
	 * @throws JSONException
	 * @throws IOException
	 * @throws VkException 
	 */
	public boolean isUserAllowedAccess(long userId, AccessScope accessScope) throws JSONException, 
		IOException, VkException 
	{
		List<NameValuePair> params = new ArrayList<NameValuePair>(1);
		params.add(new BasicNameValuePair("uid", String.valueOf(userId)));
		int accessBitMask = sendRequest("getUserSettings", params).getJSONObject("response").getInt(
				"settings");

		return accessScope.has(accessBitMask);
	}
	
	/**
	 * 
	 * @return Null if no logged user
	 */
	public Long getLoggedUserId() {
		return Auth.getInstance().getUserId();
	}
	
	/**
	 * 
	 * @throws IllegalArgumentException
	 */
	public void setLoggedUserId(Long userId) {
		Auth.getInstance().setUserId(userId);
	}

	/**
	 * 
	 * @return Null if no access token
	 */
	public String getAccessToken() {
		return Auth.getInstance().getAccessToken();
	}
	
	/**
	 * 
	 * @throws IllegalArgumentException
	 */
	public void setAccessToken(String token) {
		Auth.getInstance().setAccessToken(token);
	}
	
	public void removeListeners() {
		Auth.getInstance().removeListeners();
	}
	
	/**
	 * 
	 * @param webView
	 * @param appId Vkontakte application ID
	 * @param accessScope
	 * @param onSuccessListener Can be null
	 * @param onErrorListener Can be null
	 * @param onCancelListener Can be null
	 * @param onStartLoadingListener Can be null
	 * @param onFinishLoadingListener Can be null
	 */
	public void auth(
			WebView webView, 
			long appId, 
			AccessScope accessScope, 
			OnSuccessListener onSuccessListener, 
			OnErrorListener onErrorListener, 
			OnCancelListener onCancelListener,
			OnStartLoadingListener onStartLoadingListener,
			OnFinishLoadingListener onFinishLoadingListener
	) {
		Auth.getInstance().auth(webView, appId, accessScope, onSuccessListener, onErrorListener, 
				onCancelListener, onStartLoadingListener, onFinishLoadingListener);
	}

	/**
	 * 
	 * @param thread Thread, requests of which needs to be aborted
	 */
	public void abortAllRequests(Thread thread) {
		List<HttpClient> httpClientsOfThread = httpClients.get(thread);
		
		if (httpClientsOfThread == null) {
			return;
		}
		
		synchronized (httpClientsOfThread) {
			
			for (HttpClient httpClient : httpClientsOfThread) {
				httpClient.getConnectionManager().shutdown();
			}
			
			httpClientsOfThread.clear();
		}
	}
	
	/**
	 * 
	 * @param ownerId
	 * @param isOwnerGroup
	 * @param trackIds Can be null
	 * @throws IOException 
	 * @throws JSONException 
	 * @throws VkException 
	 */
	private Track[] getTracksInternal(long ownerId, boolean isOwnerGroup, long[] trackIds) 
			throws JSONException, IOException, VkException
	{
		int overallTracksCount = getTracksCount(ownerId, isOwnerGroup);
		
		if (overallTracksCount == 0) {
			return new Track[0];
		}

		int overallTracksCountToGet = 0;
		
		if (trackIds == null) {
			overallTracksCountToGet = overallTracksCount;
		} else {
			overallTracksCountToGet = trackIds.length;
		}
		
		if (overallTracksCountToGet == 0) {
			return new Track[0];
		}
		
		int tracksCountInResponse = 0;
		int startOffset = 0;
		LinkedList<Track> tracks = new LinkedList<Track>();
		
		do {
			List<NameValuePair> params = new ArrayList<NameValuePair>(2);
			params.add(new BasicNameValuePair(isOwnerGroup ? "gid" : "uid", 
					String.valueOf(ownerId)));
			int tracksCountToGet = 0;
			
			if (trackIds == null) {
				tracksCountToGet = overallTracksCount;
				params.add(new BasicNameValuePair("count", String.valueOf(tracksCountToGet)));
				params.add(new BasicNameValuePair("offset", String.valueOf(startOffset)));
			} else {
				tracksCountToGet = Math.min(
						tracksCountInResponse == 0 ? overallTracksCount : tracksCountInResponse, 
						trackIds.length - startOffset
				);
				
				int endOffset = startOffset + tracksCountToGet;
				StringBuilder trackIdsAsString = new StringBuilder();
				
				for (int i = startOffset; i < endOffset; i++) {
					trackIdsAsString.append(trackIds[i]).append(",");
				}
				
				params.add(new BasicNameValuePair("aids", trackIdsAsString.toString()));
				trackIdsAsString = null;
			}
			
			HttpPost request = new HttpPost(getRequestUrl("audio.get", null));
			request.setEntity(new UrlEncodedFormEntity(params));
			JSONArray response = sendRequest(request).getJSONArray("response");
			request = null;
			tracksCountInResponse = response.length();
			
			for (int i = 0; i < tracksCountInResponse; i++) {
				JSONObject jsonTrack = response.getJSONObject(i);
				
				tracks.add(new Track(
						jsonTrack.getLong("aid"), 
						jsonTrack.getString("artist"), 
						jsonTrack.getString("title"),
						jsonTrack.getInt("duration"),
						jsonTrack.getString("url"),
						jsonTrack.has("album") ? jsonTrack.getLong("album") : null
				));
			}

			response = null;
			startOffset += tracksCountInResponse;
		} while (tracks.size() < overallTracksCountToGet && tracksCountInResponse > 0);
		
		return tracks.toArray(new Track[tracks.size()]);
	}
	
	/**
	 * Same as {@link #sendRequest(String, List)}, but with no params. 
	 */
	private JSONObject sendRequest(String methodName) throws IOException, JSONException, 
		VkException  
	{
		return sendRequest(new HttpGet(getRequestUrl(methodName, null)));
	}
	
	/**
	 * Same as {@link #sendRequest(HttpUriRequest)}, but uses GET request.
	 * 
	 * @param methodName VK method name
	 * @param params
	 */
	private JSONObject sendRequest(String methodName, List<NameValuePair> params) 
			throws IOException, JSONException, VkException 
	{
		return sendRequest(new HttpGet(getRequestUrl(methodName, params)));
	}

	/**
	 * 
	 * @throws IOException
	 * @throws JSONException
	 * @throws VkException 
	 */
	private JSONObject sendRequest(HttpUriRequest request) throws IOException, 
		JSONException, VkException
	{
//		Log.d(TAG, "Request: " + request.getURI().toString());
		
		Thread currentThread = Thread.currentThread();
		List<HttpClient> httpClientsOfCurrentThread = httpClients.get(currentThread);
		
		if (httpClientsOfCurrentThread == null) {
			httpClientsOfCurrentThread = Collections.synchronizedList(new LinkedList<HttpClient>());
			httpClients.put(currentThread, httpClientsOfCurrentThread);
		}
		
		AndroidHttpClient httpClient = AndroidHttpClient.newInstance("Android");
		httpClientsOfCurrentThread.add(httpClient);

		try {
			HttpResponse response = httpClient.execute(request);
			HttpEntity entity = response.getEntity();
			String responseBody = EntityUtils.toString(entity);
			
			try {
				entity.consumeContent();
			} catch (IOException exception) {
			}
			
//			Log.d(TAG, "Response: " + responseBody);
			
			JSONObject jsonResponse = (JSONObject) new JSONTokener(responseBody).nextValue();

			if (jsonResponse.has("error")) {
				JSONObject error = jsonResponse.getJSONObject("error");
				String errorMsg = error.getString("error_msg");
				
				if (error.getInt("error_code") == 5) {
					throw new InvalidAccessTokenException(errorMsg);
				}
				
				throw new VkException(errorMsg);
			}
			
			return jsonResponse;
		} finally {
			httpClient.close();
			httpClientsOfCurrentThread.remove(httpClient);
			httpClient = null;
		}
	}

	/**
	 * 
	 * @param methodName VK method name
	 * @param params GET params. Can be null
	 */
	private String getRequestUrl(String methodName, List<NameValuePair> params) {
		String url = "https://api.vk.com/method/" + methodName + "?access_token=" + 
				getAccessToken();
		
		if (params != null) {
			url += "&" + URLEncodedUtils.format(params, "utf-8");
		}
		
		return url;
	}
	
	private VkGateway(){ }

}
