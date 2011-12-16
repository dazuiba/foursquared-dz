/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquare.types;

import java.util.ArrayList;
import java.util.List;


/**
 * @author Joe LaPenna (joe@joelapenna.com)
 */
public class Tags extends ArrayList<String> implements FoursquareType {

    private static final long serialVersionUID = 1L;
    
    public Tags() {
        super();
    }
    
    public Tags(List<String> values) {
        super();
        addAll(values);
    }
}