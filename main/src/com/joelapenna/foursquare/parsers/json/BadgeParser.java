/**
 * Copyright 2010 Mark Wyszomierski
 */
package com.joelapenna.foursquare.parsers.json;

import com.joelapenna.foursquare.types.Badge;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * @date July 13, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 *
 */
public class BadgeParser extends AbstractParser<Badge> {

    @Override
    public Badge parse(JSONObject json) throws JSONException {
        
        Badge obj = new Badge();
        if (json.has("description")) {
            obj.setDescription(json.getString("description"));
        } 
        if (json.has("icon")) {
            obj.setIcon(json.getString("icon"));
        } 
        if (json.has("id")) {
            obj.setId(json.getString("id"));
        } 
        if (json.has("name")) {
            obj.setName(json.getString("name"));
        }
        
        return obj;
    }
}