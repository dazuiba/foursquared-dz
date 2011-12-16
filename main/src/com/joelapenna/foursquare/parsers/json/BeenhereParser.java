/**
 * Copyright 2010 Mark Wyszomierski
 */
package com.joelapenna.foursquare.parsers.json;

import com.joelapenna.foursquare.types.Beenhere;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * @date July 13, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 *
 */
public class BeenhereParser extends AbstractParser<Beenhere> {
    
    @Override
    public Beenhere parse(JSONObject json) throws JSONException {
        Beenhere obj = new Beenhere();
        if (json.has("friends")) {
            obj.setFriends(json.getBoolean("friends"));
        } 
        if (json.has("me")) {
            obj.setMe(json.getBoolean("me"));
        }
        
        return obj;
    }
}