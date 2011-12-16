package com.joelapenna.foursquared.widget;

import com.joelapenna.foursquare.types.Checkin;
import com.joelapenna.foursquare.types.FoursquareType;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquared.R;
import com.joelapenna.foursquared.util.RemoteResourceManager;
import com.joelapenna.foursquared.util.UserUtils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * A single horizontal strip of user photo views. Expected to be used from 
 * xml resource, needs more work to make this a robust and generic control.
 * 
 * @date September 15, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class PhotoStrip extends View
    implements ObservableAdapter {
    	
    private int mPhotoSize;
    private int mPhotoSpacing;
    private int mPhotoBorder;
    private int mPhotoBorderStroke;
    private int mPhotoBorderColor;
    private int mPhotoBorderStrokeColor;
    
    private Group<User> mTypes;
    private Map<String, Bitmap> mCachedBitmaps = new HashMap<String, Bitmap>();

    private RemoteResourceManager mRrm;
    private RemoteResourceManagerObserver mResourcesObserver;
    
    
    public PhotoStrip(Context context) {
        super(context);
    }
    
    public PhotoStrip(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PhotoStrip, 0, 0);

        mPhotoSize = a.getDimensionPixelSize(R.styleable.PhotoStrip_photoSize, 44);
        mPhotoSpacing = a.getDimensionPixelSize(R.styleable.PhotoStrip_photoSpacing, 10);
        mPhotoBorder = a.getDimensionPixelSize(R.styleable.PhotoStrip_photoBorder, 2);
        mPhotoBorderStroke = a.getDimensionPixelSize(R.styleable.PhotoStrip_photoBorderStroke, 0);
        mPhotoBorderColor = a.getColor(R.styleable.PhotoStrip_photoBorderColor, 0xFFFFFFFF);
        mPhotoBorderStrokeColor = a.getColor(R.styleable.PhotoStrip_photoBorderStrokeColor, 0xFFD0D0D);
        
        a.recycle();
    }
    
    public void setUsersAndRemoteResourcesManager(Group<User> users, RemoteResourceManager rrm) {
    	mTypes = users;
        mRrm = rrm;
    	mResourcesObserver = new RemoteResourceManagerObserver();
    	mRrm.addObserver(mResourcesObserver);
    	invalidate();
    }
    
    public void setCheckinsAndRemoteResourcesManager(Group<Checkin> checkins, RemoteResourceManager rrm) {
        Group<User> users = new Group<User>();
        for (Checkin it : checkins) {
            if (it.getUser() != null) {
                users.add(it.getUser());
            }
        }
        setUsersAndRemoteResourcesManager(users, rrm);
    }

    public void removeObserver() {
        mRrm.deleteObserver(mResourcesObserver);
    }
    
    @Override
    protected void onDetachedFromWindow() {
    	super.onDetachedFromWindow();
    	
    	if (mRrm != null && mResourcesObserver != null) {
    	    mRrm.deleteObserver(mResourcesObserver);
    	}
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
    	super.onDraw(canvas);

    	Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    	paint.setAntiAlias(true);
    	
    	
    	int width = getWidth();
        int sum = mPhotoSize + mPhotoSpacing;
        int index = 0;
        
        while (sum < width) {
        	if (mTypes == null || index >= mTypes.size()) {
        		break;
        	}
        	
        	Rect rcDst = new Rect(
                    index * (mPhotoSize + mPhotoSpacing),
                    0,
                    index * (mPhotoSize + mPhotoSpacing) + mPhotoSize,
                    mPhotoSize);
        	paint.setColor(mPhotoBorderStrokeColor);
            canvas.drawRect(rcDst, paint);
            rcDst.inset(mPhotoBorderStroke, mPhotoBorderStroke);
            paint.setColor(mPhotoBorderColor);
            canvas.drawRect(rcDst, paint);
            rcDst.inset(mPhotoBorder, mPhotoBorder);

            FoursquareType type = mTypes.get(index);
            Bitmap bmp = fetchBitmapForUser(type);
       	    if (bmp != null) {
	            Rect rcSrc = new Rect(0, 0, bmp.getWidth(), bmp.getHeight());
	            canvas.drawBitmap(bmp, rcSrc, rcDst, paint);
            }
            sum += (mPhotoSize + mPhotoSpacing);
            index++;
        }
    }
    
    private Bitmap fetchBitmapForUser(FoursquareType type) {
        User user = null;
        if (type instanceof User) {
            user = (User)type;
        } else if (type instanceof Checkin) {
            Checkin checkin = (Checkin)type;
            user = checkin.getUser();
            if (user == null) {
                return null;
            }
        } else {
            throw new RuntimeException("PhotoStrip can only accept Users or Checkins.");
        }
        
    	String photoUrl = user.getPhoto();
    	if (mCachedBitmaps.containsKey(photoUrl)) {
    	    return mCachedBitmaps.get(photoUrl);
    	}
    	
    	Uri uriPhoto = Uri.parse(photoUrl);
        if (mRrm.exists(uriPhoto)) {
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(mRrm.getInputStream(Uri.parse(photoUrl)));
                mCachedBitmaps.put(photoUrl, bitmap);
                return bitmap;
            } catch (IOException e) {
            }
        } else {
            mRrm.request(uriPhoto);
        }
        
        return BitmapFactory.decodeResource(getResources(), UserUtils.getDrawableByGenderForUserThumbnail(user));
    }
    
    /**
     * @see android.view.View#measure(int, int)
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
            measureWidth(widthMeasureSpec),
            measureHeight(heightMeasureSpec));
    }

    /**
     * Determines the width of this view
     * @param measureSpec A measureSpec packed into an int
     * @return The width of the view, honoring constraints from measureSpec
     */
    private int measureWidth(int measureSpec) {
        int result = 0;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            // We were told how big to be.
            result = specSize;
        }
        else {
            if (specMode == MeasureSpec.AT_MOST) {
                // Use result.
            }
            else {
                // Use result.
            }
        }

        return result;
    }

    private int measureHeight(int measureSpec) {
        // We should be exactly as high as the specified photo size.
    	// An exception would be if we have zero photos to display,
    	// we're not dealing with that at the moment.
        return mPhotoSize + getPaddingTop() + getPaddingBottom();
    }
    
    private class RemoteResourceManagerObserver implements Observer {
        @Override
        public void update(Observable observable, Object data) {
        	postInvalidate();
        }
    }
}
