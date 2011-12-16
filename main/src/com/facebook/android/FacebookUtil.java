/*
 * Copyright 2010 Mark Wyszomierski
 * Portions Copyright (c) 2008-2010 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.android;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;


/**
 * Utility class supporting the Facebook Object.
 * 
 * @author ssoneff@facebook.com
 *    -original author.
 * @author Mark Wyszomierski (markww@gmail.com):
 *    -just removed alert dialog method which can be handled by managed
 *     dialogs in third party apps.
 */
public final class FacebookUtil {

    public static String encodeUrl(Bundle parameters) {
        if (parameters == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String key : parameters.keySet()) {
            if (first) first = false; else sb.append("&");
            sb.append(key + "=" + parameters.getString(key));
        }
        return sb.toString();
    }

    public static Bundle decodeUrl(String s) {
        Bundle params = new Bundle();
        if (s != null) {
            String array[] = s.split("&");
            for (String parameter : array) {
                String v[] = parameter.split("=");
                params.putString(v[0], v[1]);
            }
        }
        return params;
    }

    /**
     * Parse a URL query and fragment parameters into a key-value bundle.
     * 
     * @param url the URL to parse
     * @return a dictionary bundle of keys and values
     */
    public static Bundle parseUrl(String url) {
        // hack to prevent MalformedURLException
        url = url.replace("fbconnect", "http"); 
        try {
            URL u = new URL(url);
            Bundle b = decodeUrl(u.getQuery());
            b.putAll(decodeUrl(u.getRef()));
            return b;
        } catch (MalformedURLException e) {
            return new Bundle();
        }
    }

    
    /**
     * Connect to an HTTP URL and return the response as a string.
     * 
     * Note that the HTTP method override is used on non-GET requests. (i.e.
     * requests are made as "POST" with method specified in the body).
     * 
     * @param url - the resource to open: must be a welformed URL
     * @param method - the HTTP method to use ("GET", "POST", etc.)
     * @param params - the query parameter for the URL (e.g. access_token=foo)
     * @return the URL contents as a String
     * @throws MalformedURLException - if the URL format is invalid
     * @throws IOException - if a network problem occurs
     */
    public static String openUrl(String url, String method, Bundle params) 
          throws MalformedURLException, IOException {
        if (method.equals("GET")) {
            url = url + "?" + encodeUrl(params);
        }

        HttpURLConnection conn = 
            (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", System.getProperties().
                getProperty("http.agent") + " FacebookAndroidSDK");
        if (!method.equals("GET")) {
            // use method override
            params.putString("method", method);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.getOutputStream().write(
                    encodeUrl(params).getBytes("UTF-8"));
        }
        
        String response = "";
        try {
            response = read(conn.getInputStream());
        } catch (FileNotFoundException e) {
            // Error Stream contains JSON that we can parse to a FB error
            response = read(conn.getErrorStream());
        }
        
        return response;
    }

    private static String read(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(in), 1000);
        for (String line = r.readLine(); line != null; line = r.readLine()) {
            sb.append(line);
        }
        in.close();
        return sb.toString();
    }

    /**
     * Parse a server response into a JSON Object.  This is a basic
     * implementation using org.json.JSONObject representation.  More
     * sophisticated applications may wish to do their own parsing.
     * 
     * The parsed JSON is checked for a variety of error fields and
     * a FacebookException is thrown if an error condition is set, 
     * populated with the error message and error type or code if
     * available. 
     * 
     * @param response - string representation of the response
     * @return the response as a JSON Object
     * @throws JSONException - if the response is not valid JSON
     * @throws FacebookError - if an error condition is set
     */
    public static JSONObject parseJson(String response) 
          throws JSONException, FacebookError {
        // Edge case: when sending a POST request to /[post_id]/likes
        // the return value is 'true' or 'false'. Unfortunately
        // these values cause the JSONObject constructor to throw
        // an exception.
        if (response.equals("false")) {
            throw new FacebookError("request failed");
        }
        if (response.equals("true")) {
            response = "{value : true}";
        }
        JSONObject json = new JSONObject(response);
        
        // errors set by the server are not consistent
        // they depend on the method and endpoint
        if (json.has("error")) {
            JSONObject error = json.getJSONObject("error");
            throw new FacebookError(
                    error.getString("message"), error.getString("type"), 0);
        }
        if (json.has("error_code") && json.has("error_msg")) {
            throw new FacebookError(json.getString("error_msg"), "",
                    Integer.parseInt(json.getString("error_code")));
        }
        if (json.has("error_code")) {
            throw new FacebookError("request failed", "",
                    Integer.parseInt(json.getString("error_code")));
        }
        if (json.has("error_msg")) {
            throw new FacebookError(json.getString("error_msg"));
        }
        if (json.has("error_reason")) {
            throw new FacebookError(json.getString("error_reason"));
        }
        return json;
    }
    
    public static String printBundle(Bundle bundle) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bundle: "); 
        if (bundle != null) {
            sb.append(bundle.toString()); sb.append("\n");
            Set<String> keys = bundle.keySet();
            for (String it : keys) {
                sb.append("   ");
                sb.append(it);
                sb.append(": ");
                sb.append(bundle.get(it).toString());
                sb.append("\n");
            }
        } else {
            sb.append("(null)");
        }
        return sb.toString();
    }
}
