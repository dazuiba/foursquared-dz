/**
 * Copyright 2010 Mark Wyszomierski
 */
package com.joelapenna.foursquare.parsers.json;

import com.joelapenna.foursquare.types.Settings;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * @date July 13, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 *
 */
public class SettingsParser extends AbstractParser<Settings> {
    
    @Override
    public Settings parse(JSONObject json) throws JSONException {
        Settings obj = new Settings();
        if (json.has("feeds_key")) {
            obj.setFeedsKey(json.getString("feeds_key"));
        } 
        if (json.has("get_pings")) {
            obj.setGetPings(json.getBoolean("get_pings"));
        } 
        if (json.has("pings")) {
            obj.setPings(json.getString("pings"));
        } 
        if (json.has("sendtofacebook")) {
            obj.setSendtofacebook(json.getBoolean("sendtofacebook"));
        } 
        if (json.has("sendtotwitter")) {
            obj.setSendtotwitter(json.getBoolean("sendtotwitter"));
        }
        
        return obj;
    }
}