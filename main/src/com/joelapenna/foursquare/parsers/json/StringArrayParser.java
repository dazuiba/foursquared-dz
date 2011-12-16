/**
 * Copyright 2010 Mark Wyszomierski
 */
package com.joelapenna.foursquare.parsers.json;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;


/**
 * @date July 13, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 *
 */
public class StringArrayParser {

    public static List<String> parse(JSONArray json) throws JSONException {
        List<String> array = new ArrayList<String>();
        for (int i = 0, m = json.length(); i < m; i++) {
            array.add(json.getString(i));
        }
        
        return array; 
    }
}