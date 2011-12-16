/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared.util;

import android.text.TextUtils;

import com.joelapenna.foursquare.types.FoursquareType;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;

/**
 * @date September 2, 2010.
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class TipUtils {

    public static final String TIP_STATUS_TODO = "todo";
    public static final String TIP_STATUS_DONE = "done";

    public static boolean isTodo(Tip tip) {
        if (tip != null) {
            if (!TextUtils.isEmpty(tip.getStatus())) {
                return tip.getStatus().equals(TIP_STATUS_TODO);
            }
        }
        
        return false;
    }
    
    public static boolean isDone(Tip tip) {
        if (tip != null) {
            if (!TextUtils.isEmpty(tip.getStatus())) {
                return tip.getStatus().equals(TIP_STATUS_DONE);
            }
        }
        
        return false;
    }
    
    public static boolean areEqual(FoursquareType tipOrTodo1, FoursquareType tipOrTodo2) {
    	if (tipOrTodo1 instanceof Tip) {
    		if (tipOrTodo2 instanceof Todo) {
    			return false;
    		}
    		
    		Tip tip1 = (Tip)tipOrTodo1;
    		Tip tip2 = (Tip)tipOrTodo2;
    		if (!tip1.getId().equals(tip2.getId())) {
    			return false;
    		}
    		
    		if (!TextUtils.isEmpty(tip1.getStatus()) && !TextUtils.isEmpty(tip2.getStatus())) {
    		    return tip1.getStatus().equals(tip2.getStatus());
    		} else if (TextUtils.isEmpty(tip1.getStatus()) && TextUtils.isEmpty(tip2.getStatus())) {
    			return true;
    	    } else {
    			return false;
    		}
    		
    	} else if (tipOrTodo1 instanceof Todo) {
    		if (tipOrTodo2 instanceof Tip) {
    			return false;
    		}

    		Todo todo1 = (Todo)tipOrTodo1;
    		Todo todo2 = (Todo)tipOrTodo2;
    		if (!todo1.getId().equals(todo2.getId())) {
    			return false;
    		}
    		
    		if (todo1.getTip().getId().equals(todo2.getId())) {
    			return true;
    		}
    	}
    	
    	return false;
    }
}
