/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquare.parsers.json;

import com.joelapenna.foursquare.types.Response;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @date April 28, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class ResponseParser extends AbstractParser<Response> {

    @Override
    public Response parse(JSONObject json) throws JSONException {
        Response response = new Response();
        response.setValue(json.getString("response"));
        return response;
    }
}