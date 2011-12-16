/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared.util;

import android.os.Parcel;
import android.text.TextUtils;

import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;
import com.joelapenna.foursquare.types.Venue;

/**
 * @date September 16, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class VenueUtils {

	
	public static void handleTipChange(Venue venue, Tip tip, Todo todo) {
		// Update the tip in the tips group, if it exists.
		updateTip(venue, tip);
		
		// If it is a to-do, then make sure a to-do exists for it
		// in the to-do group.
		if (TipUtils.isTodo(tip)) {
			addTodo(venue, tip, todo);
		} else {
			// If it is not a to-do, make sure it does not exist in the
			// to-do group.
			removeTodo(venue, tip);
		}
	}
	
	private static void updateTip(Venue venue, Tip tip) {
		if (venue.getTips() != null) {
	        for (Tip it : venue.getTips()) {
	            if (it.getId().equals(tip.getId())) {
	                it.setStatus(tip.getStatus());
	                break;
	            }
	        }
		}
    }
    
    public static void addTodo(Venue venue, Tip tip, Todo todo) {
    	venue.setHasTodo(true);
    	
    	if (venue.getTodos() == null) {
			venue.setTodos(new Group<Todo>());
		}
    	
    	// If found a to-do linked to the tip ID, then overwrite to-do attributes
    	// with newer to-do object.
    	for (Todo it : venue.getTodos()) {
    		if (it.getTip().getId().equals(tip.getId())) {
    			it.setId(todo.getId());
    			it.setCreated(todo.getCreated());
    			return;
    		}
    	}
    	
    	venue.getTodos().add(todo);
    }
    
    public static void addTip(Venue venue, Tip tip) {
		if (venue.getTips() == null) {
			venue.setTips(new Group<Tip>());
		}
		
        for (Tip it : venue.getTips()) {
            if (it.getId().equals(tip.getId())) {
            	return;
            }
        }

        venue.getTips().add(tip);
    }
    
    private static void removeTodo(Venue venue, Tip tip) {
    	for (Todo it : venue.getTodos()) {
    		if (it.getTip().getId().equals(tip.getId())) {
    			venue.getTodos().remove(it);
    			break;
    		}
    	}

    	if (venue.getTodos().size() > 0) {
    		venue.setHasTodo(true);
    	} else {
    		venue.setHasTodo(false);
    	}
    }
    
    public static void replaceTipsAndTodos(Venue venueTarget, Venue venueSource) {
    	if (venueTarget.getTips() == null) {
    		venueTarget.setTips(new Group<Tip>());
    	}
    	if (venueTarget.getTodos() == null) {
    		venueTarget.setTodos(new Group<Todo>());
    	}
    	if (venueSource.getTips() == null) {
    		venueSource.setTips(new Group<Tip>());
    	}
    	if (venueSource.getTodos() == null) {
    		venueSource.setTodos(new Group<Todo>());
    	}
    	
    	venueTarget.getTips().clear();
    	venueTarget.getTips().addAll(venueSource.getTips());
    	venueTarget.getTodos().clear();
    	venueTarget.getTodos().addAll(venueSource.getTodos());
    	if (venueTarget.getTodos().size() > 0) {
    		venueTarget.setHasTodo(true);
    	} else {
    		venueTarget.setHasTodo(false);
    	}
    }
    
    public static boolean getSpecialHere(Venue venue) {
	    if (venue != null && venue.getSpecials() != null && venue.getSpecials().size() > 0) {
			Venue specialVenue = venue.getSpecials().get(0).getVenue();
	        if (specialVenue == null || specialVenue.getId().equals(venue.getId())) {
	        	return true;
	        }
		}
	    
	    return false;
    }
    
    /**
     * Creates a copy of the passed venue. This should really be implemented
     * as a copy constructor.
     */
    public static Venue cloneVenue(Venue venue) {
    	Parcel p1 = Parcel.obtain();
    	Parcel p2 = Parcel.obtain();
    	byte[] bytes = null;
    	
    	p1.writeValue(venue);
    	bytes = p1.marshall();
    	
    	p2.unmarshall(bytes, 0, bytes.length);
        p2.setDataPosition(0);
        Venue venueNew = (Venue)p2.readValue(Venue.class.getClassLoader());
    	
        p1.recycle();
        p2.recycle();
        
        return venueNew;
    }
    
    public static String toStringVenueShare(Venue venue) {
        StringBuilder sb = new StringBuilder();
        sb.append(venue.getName()); sb.append("\n");
        sb.append(StringFormatters.getVenueLocationFull(venue));
        if (!TextUtils.isEmpty(venue.getPhone())) {
            sb.append("\n");
            sb.append(venue.getPhone());
        }
        return sb.toString();
    }
    
    /**
     * Dumps some info about a venue. This can be moved into the Venue class.
     */
    public static String toString(Venue venue) {
    	StringBuilder sb = new StringBuilder();
    	sb.append(venue.toString()); sb.append(":\n");
    	sb.append("  id:       "); sb.append(venue.getId()); sb.append("\n");
    	sb.append("  name:     "); sb.append(venue.getName()); sb.append("\n");
    	sb.append("  address:  "); sb.append(venue.getAddress()); sb.append("\n");
    	sb.append("  cross:    "); sb.append(venue.getCrossstreet()); sb.append("\n");
    	sb.append("  hastodo:  "); sb.append(venue.getHasTodo()); sb.append("\n");
    	sb.append("  tips:     "); sb.append(venue.getTips() == null ? "(null)" : venue.getTips().size()); sb.append("\n");
    	sb.append("  todos:    "); sb.append(venue.getTodos() == null ? "(null)" : venue.getTodos().size()); sb.append("\n");
    	sb.append("  specials: "); sb.append(venue.getSpecials() == null ? "(null)" : venue.getSpecials().size()); sb.append("\n");
    	
    	return sb.toString();
    }
}
