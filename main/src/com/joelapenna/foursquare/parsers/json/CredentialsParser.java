/**
 * Copyright 2010 Mark Wyszomierski
 */
package com.joelapenna.foursquare.parsers.json;

import com.joelapenna.foursquare.types.Credentials;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * @date July 13, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 *
 */
public class CredentialsParser extends AbstractParser<Credentials> {
    
    @Override
    public Credentials parse(JSONObject json) throws JSONException {
        Credentials obj = new Credentials();
        if (json.has("oauth_token")) {
            obj.setOauthToken(json.getString("oauth_token"));
        } 
        if (json.has("oauth_token_secret")) {
            obj.setOauthTokenSecret(json.getString("oauth_token_secret"));
        } 
        
        return obj;
    }
}