/**
 * Copyright 2010 Mark Wyszomierski
 */
package com.joelapenna.foursquare.parsers.json;

import com.joelapenna.foursquare.types.Mayor;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * @date July 13, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 *
 */
public class MayorParser extends AbstractParser<Mayor> {
    
    @Override
    public Mayor parse(JSONObject json) throws JSONException {
        
        Mayor obj = new Mayor();
        if (json.has("checkins")) {
            obj.setCheckins(json.getString("checkins"));
        } 
        if (json.has("count")) {
            obj.setCount(json.getString("count"));
        } 
        if (json.has("message")) {
            obj.setMessage(json.getString("message"));
        } 
        if (json.has("type")) {
            obj.setType(json.getString("type"));
        } 
        if (json.has("user")) {
            obj.setUser(new UserParser().parse(json.getJSONObject("user")));
        }
                    
        return obj;
    }
}