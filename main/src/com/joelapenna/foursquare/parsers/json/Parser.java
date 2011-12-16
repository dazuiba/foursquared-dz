/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquare.parsers.json;

import com.joelapenna.foursquare.types.FoursquareType;
import com.joelapenna.foursquare.types.Group;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @date July 13, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public interface Parser<T extends FoursquareType> {

    public abstract T parse(JSONObject json) throws JSONException;
    public Group parse(JSONArray array) throws JSONException;
}
