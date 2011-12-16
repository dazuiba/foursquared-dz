/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared.widget;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.types.Category;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.R;
import com.joelapenna.foursquared.util.RemoteResourceManager;
import com.joelapenna.foursquared.util.StringFormatters;
import com.joelapenna.foursquared.util.TipUtils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * @date August 31, 2010
 * @author Mark Wyszomierski (markww@gmail.com), foursquare.
 */
public class TipsListAdapter extends BaseGroupAdapter<Tip> 
    implements ObservableAdapter {

    private LayoutInflater mInflater;
    private int mLayoutToInflate;
    private Resources mResources;
    private RemoteResourceManager mRrm;
    private RemoteResourceManagerObserver mResourcesObserver;
    private Handler mHandler = new Handler();
    private int mLoadedPhotoIndex;
    private boolean mDisplayTipVenueTitles;
    private Map<String, String> mCachedTimestamps;

    
    public TipsListAdapter(Context context, RemoteResourceManager rrm, int layout) {
        super(context);
        mInflater = LayoutInflater.from(context);
        mLayoutToInflate = layout;
        mResources = context.getResources();
        mRrm = rrm;
        mResourcesObserver = new RemoteResourceManagerObserver();
        mLoadedPhotoIndex = 0;
        mDisplayTipVenueTitles = true;
        mCachedTimestamps = new HashMap<String, String>();
        
        mRrm.addObserver(mResourcesObserver);
    }
    
    public void removeObserver() {
        mHandler.removeCallbacks(mUpdatePhotos);
        mHandler.removeCallbacks(mRunnableLoadPhotos);
        mRrm.deleteObserver(mResourcesObserver);
    }

    public TipsListAdapter(Context context, int layoutResource) {
        super(context);
        mInflater = LayoutInflater.from(context);
        mLayoutToInflate = layoutResource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // A ViewHolder keeps references to children views to avoid unnecessary
        // calls to findViewById() on each row.
        ViewHolder holder;

        // When convertView is not null, we can reuse it directly, there is no
        // need to re-inflate it. We only inflate a new View when the
        // convertView supplied by ListView is null.
        if (convertView == null) {
            convertView = mInflater.inflate(mLayoutToInflate, null);

            // Creates a ViewHolder and store references to the two children
            // views we want to bind data to.
            holder = new ViewHolder();
            holder.photo = (ImageView) convertView.findViewById(R.id.icon);
            holder.title = (TextView) convertView.findViewById(R.id.tvTitle);
            holder.body = (TextView) convertView.findViewById(R.id.tvBody);
            holder.dateAndAuthor = (TextView) convertView.findViewById(R.id.tvDateAndAuthor);
            //holder.friendCountTodoImg = (ImageView) convertView.findViewById(R.id.ivFriendCountAsTodo);
            //holder.friendCountTodo = (TextView) convertView.findViewById(R.id.tvFriendCountAsTodo);
            holder.friendCountCompletedImg = (ImageView) convertView.findViewById(R.id.ivFriendCountCompleted);
            holder.friendCountCompleted = (TextView) convertView.findViewById(R.id.tvFriendCountCompleted);
            holder.corner = (ImageView) convertView.findViewById(R.id.ivTipCorner);

            convertView.setTag(holder);
        } else {
            // Get the ViewHolder back to get fast access to the TextView
            // and the ImageView.
            holder = (ViewHolder) convertView.getTag();
        }

        Tip tip = (Tip) getItem(position);
        User user = tip.getUser();
        if (user != null) {
            Uri photoUri = Uri.parse(user.getPhoto());
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(mRrm.getInputStream(photoUri));
                holder.photo.setImageBitmap(bitmap);
            } catch (IOException e) {
                if (Foursquare.MALE.equals(user.getGender())) {
                    holder.photo.setImageResource(R.drawable.blank_boy);
                } else {
                    holder.photo.setImageResource(R.drawable.blank_girl);
                }
            }
        } else {
            Venue venue = tip.getVenue();
            Category category = venue.getCategory();
            if (category != null) {
                holder.photo.setBackgroundDrawable(null);
                
                Uri photoUri = Uri.parse(category.getIconUrl());
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(mRrm.getInputStream(photoUri));
                    holder.photo.setImageBitmap(bitmap);
                } catch (IOException e) {
                    holder.photo.setImageResource(R.drawable.category_none);
                }
            } else {
                // If there is no category for this venue, fall back to the original
                // method of the
                // blue/grey pin depending on if the user has been there or not.
                holder.photo.setImageResource(R.drawable.category_none);
            }
        }

        if (mDisplayTipVenueTitles && tip.getVenue() != null) {
            holder.title.setText("@ " + tip.getVenue().getName());
            holder.title.setVisibility(View.VISIBLE);
        } else {
            holder.title.setVisibility(View.GONE);
            
            holder.body.setPadding(
        		 holder.body.getPaddingLeft(), holder.title.getPaddingTop(),
        		 holder.body.getPaddingRight(), holder.title.getPaddingBottom());
        }
        
        holder.body.setText(tip.getText());
        holder.dateAndAuthor.setText(mCachedTimestamps.get(tip.getId()));
        if (user != null) {
            holder.dateAndAuthor.setText(
                    holder.dateAndAuthor.getText() +
                    mResources.getString(
                            R.string.tip_age_via, 
                            StringFormatters.getUserFullName(user)));
        }
        /*
        if (tip.getStats().getTodoCount() > 0) {
            holder.friendCountTodoImg.setVisibility(View.VISIBLE);
            holder.friendCountTodo.setVisibility(View.VISIBLE);
            holder.friendCountTodo.setText(String.valueOf(tip.getStats().getTodoCount()));
        } else {
            holder.friendCountTodoImg.setVisibility(View.GONE);
            holder.friendCountTodo.setVisibility(View.GONE);
        }
        */
        if (tip.getStats().getDoneCount() > 0) {
            holder.friendCountCompletedImg.setVisibility(View.VISIBLE);
            holder.friendCountCompleted.setVisibility(View.VISIBLE);
            holder.friendCountCompleted.setText(String.valueOf(tip.getStats().getDoneCount()));
        } else {
            holder.friendCountCompletedImg.setVisibility(View.GONE);
            holder.friendCountCompleted.setVisibility(View.GONE);
        }

        if (TipUtils.isDone(tip)) {
            holder.corner.setVisibility(View.VISIBLE);
            holder.corner.setImageResource(R.drawable.tip_list_item_corner_done);
        } else if (TipUtils.isTodo(tip)) {
            holder.corner.setVisibility(View.VISIBLE);
            holder.corner.setImageResource(R.drawable.tip_list_item_corner_todo);
        } else {
            holder.corner.setVisibility(View.GONE);
        }
        
        return convertView;
    }

    public void removeItem(int position) throws IndexOutOfBoundsException {
        group.remove(position);
        notifyDataSetInvalidated();
    }
    
    @Override
    public void setGroup(Group<Tip> g) {
        super.setGroup(g);
        mLoadedPhotoIndex = 0;
        
        mHandler.postDelayed(mRunnableLoadPhotos, 10L);
        
        mCachedTimestamps.clear();
        for (Tip it : g) {
            String formatted = StringFormatters.getTipAge(mResources, it.getCreated()); 
            mCachedTimestamps.put(it.getId(), formatted);
        }
    }
    
    public void setDisplayTipVenueTitles(boolean displayTipVenueTitles) {
        mDisplayTipVenueTitles = displayTipVenueTitles;
    }
    
    private class RemoteResourceManagerObserver implements Observer {
        @Override
        public void update(Observable observable, Object data) {
            mHandler.post(mUpdatePhotos);
        }
    }
    
    private Runnable mUpdatePhotos = new Runnable() {
        @Override
        public void run() {
            notifyDataSetChanged();
        }
    };
    
    private Runnable mRunnableLoadPhotos = new Runnable() {
        @Override
        public void run() {
            if (mLoadedPhotoIndex < getCount()) {
                Tip tip = (Tip)getItem(mLoadedPhotoIndex++);
                if (tip.getUser() != null) {
                    Uri photoUri = Uri.parse(tip.getUser().getPhoto());
                    if (!mRrm.exists(photoUri)) {
                        mRrm.request(photoUri); 
                    }
                    mHandler.postDelayed(mRunnableLoadPhotos, 200L);
                }
            }
        }
    };

    static class ViewHolder {
        ImageView photo;
        TextView title;
        TextView body;
        TextView dateAndAuthor;
        //ImageView friendCountTodoImg;
        //TextView friendCountTodo;
        ImageView friendCountCompletedImg;
        TextView friendCountCompleted;
        ImageView corner;
    }
}
