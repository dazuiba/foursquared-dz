/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquare.types;

import com.joelapenna.foursquare.util.ParcelUtils;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @date September 2, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class Todo implements FoursquareType, Parcelable {

    private String mCreated;
    private String mId;
    private Tip mTip;

    public Todo() {
    }

    private Todo(Parcel in) {
        mCreated = ParcelUtils.readStringFromParcel(in);
        mId = ParcelUtils.readStringFromParcel(in);
        if (in.readInt() == 1) {
            mTip = in.readParcelable(Tip.class.getClassLoader());
        }
    }
    
    public static final Parcelable.Creator<Todo> CREATOR = new Parcelable.Creator<Todo>() {
        public Todo createFromParcel(Parcel in) {
            return new Todo(in);
        }

        @Override
        public Todo[] newArray(int size) {
            return new Todo[size];
        }
    };
    
    public String getCreated() {
        return mCreated;
    }
    
    public void setCreated(String created) {
        mCreated = created;
    }
    
    public String getId() {
        return mId;
    }
    
    public void setId(String id) {
        mId = id;
    }

    public Tip getTip() {
        return mTip;
    }

    public void setTip(Tip tip) {
        mTip = tip;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        ParcelUtils.writeStringToParcel(out, mCreated);
        ParcelUtils.writeStringToParcel(out, mId);
        
        if (mTip != null) {
            out.writeInt(1);
            out.writeParcelable(mTip, flags);
        } else {
            out.writeInt(0);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
