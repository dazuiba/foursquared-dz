/**
 * Copyright 2010 Mark Wyszomierski
 */
package com.joelapenna.foursquare.parsers.json;

import com.joelapenna.foursquare.parsers.json.CategoryParser;
import com.joelapenna.foursquare.parsers.json.GroupParser;
import com.joelapenna.foursquare.types.Category;
import com.joelapenna.foursquare.util.IconUtils;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * @date July 13, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 *
 */
public class CategoryParser extends AbstractParser<Category> {
    
    @Override
    public Category parse(JSONObject json) throws JSONException {
        Category obj = new Category();
        if (json.has("id")) {
            obj.setId(json.getString("id"));
        } 
        if (json.has("fullpathname")) {
            obj.setFullPathName(json.getString("fullpathname"));
        } 
        if (json.has("nodename")) {
            obj.setNodeName(json.getString("nodename"));
        } 
        if (json.has("iconurl")) {
            // TODO: Remove this once api v2 allows icon request.
            String iconUrl = json.getString("iconurl");
            if (IconUtils.get().getRequestHighDensityIcons()) {
                iconUrl = iconUrl.replace(".png", "_64.png");
            }
            obj.setIconUrl(iconUrl);
        } 
        if (json.has("categories")) {
            obj.setChildCategories(
                new GroupParser(
                    new CategoryParser()).parse(json.getJSONArray("categories")));
        }
        
        return obj;
    }
}