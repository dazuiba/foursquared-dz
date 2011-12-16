/**
 * Copyright 2010 Mark Wyszomierski
 */
package com.joelapenna.foursquare.parsers.json;

import com.joelapenna.foursquare.types.Todo;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * @date September 2, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class TodoParser extends AbstractParser<Todo> {
  
    @Override
    public Todo parse(JSONObject json) throws JSONException {
  
        Todo obj = new Todo();
        if (json.has("created")) {
            obj.setCreated(json.getString("created"));
        }
        if (json.has("tip")) {
            obj.setTip(new TipParser().parse(json.getJSONObject("tip")));
        }
        if (json.has("todoid")) {
            obj.setId(json.getString("todoid"));
        }
        
        return obj;
    }
}
