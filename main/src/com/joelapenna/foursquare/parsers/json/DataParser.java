/**
 * Copyright 2010 Mark Wyszomierski
 */
package com.joelapenna.foursquare.parsers.json;

import com.joelapenna.foursquare.types.Data;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * @date July 13, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 *
 */
public class DataParser extends AbstractParser<Data> {
    
    @Override
    public Data parse(JSONObject json) throws JSONException {
        Data obj = new Data();
        if (json.has("cityid")) {
            obj.setCityid(json.getString("cityid"));
        } 
        if (json.has("message")) {
            obj.setMessage(json.getString("message"));
        } 
        if (json.has("status")) {
            obj.setStatus("1".equals(json.getString("status")));
        }
        
        return obj;
    }
}