/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared.widget;

import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.R;
import com.joelapenna.foursquared.util.RemoteResourceManager;
import com.joelapenna.foursquared.util.StringFormatters;
import com.joelapenna.foursquared.util.TipUtils;
import com.joelapenna.foursquared.util.UiUtil;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
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
 * @date September 12, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class TodosListAdapter extends BaseGroupAdapter<Todo> 
    implements ObservableAdapter {

    private LayoutInflater mInflater;
    private int mLayoutToInflate;
    private Resources mResources;
    private RemoteResourceManager mRrm;
    private RemoteResourceManagerObserver mResourcesObserver;
    private Handler mHandler = new Handler();
    private int mLoadedPhotoIndex;
    private Map<String, String> mCachedTimestamps;
    private boolean mDisplayVenueTitles;
    private int mSdk;

    
    public TodosListAdapter(Context context, RemoteResourceManager rrm) {
        super(context);
        mInflater = LayoutInflater.from(context);
        mLayoutToInflate = R.layout.todo_list_item;
        mResources = context.getResources();
        mRrm = rrm;
        mResourcesObserver = new RemoteResourceManagerObserver();
        mLoadedPhotoIndex = 0;
        mCachedTimestamps = new HashMap<String, String>();
        mDisplayVenueTitles = true;
        mSdk = UiUtil.sdkVersion();
        
        mRrm.addObserver(mResourcesObserver);
    }
    
    public void removeObserver() {
        mHandler.removeCallbacks(mUpdatePhotos);
        mHandler.removeCallbacks(mRunnableLoadPhotos);
        mRrm.deleteObserver(mResourcesObserver);
    }

    public TodosListAdapter(Context context, int layoutResource) {
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
            holder.photo = (ImageView) convertView.findViewById(R.id.ivVenueCategory);
            holder.title = (TextView) convertView.findViewById(R.id.tvTitle);
            holder.body = (TextView) convertView.findViewById(R.id.tvBody);
            holder.dateAndAuthor = (TextView) convertView.findViewById(R.id.tvDateAndAuthor);
            holder.corner = (ImageView) convertView.findViewById(R.id.ivTipCorner);

            convertView.setTag(holder);
        } else {
            // Get the ViewHolder back to get fast access to the TextView
            // and the ImageView.
            holder = (ViewHolder) convertView.getTag();
        }

        Todo todo = (Todo)getItem(position);
        Tip tip = todo.getTip();
        if (tip != null) {
            if (mDisplayVenueTitles && tip.getVenue() != null) {
                holder.title.setText("@ " + tip.getVenue().getName());
                holder.title.setVisibility(View.VISIBLE);
            } else {
                holder.title.setVisibility(View.GONE);
                
                holder.body.setPadding(
                	holder.body.getPaddingLeft(), holder.title.getPaddingTop(),
                	holder.body.getPaddingRight(), holder.body.getPaddingBottom());
            }
    
            if (tip.getVenue() != null && tip.getVenue().getCategory() != null) {
                Uri photoUri = Uri.parse(tip.getVenue().getCategory().getIconUrl());
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(mRrm.getInputStream(photoUri));
                    holder.photo.setImageBitmap(bitmap);
                } catch (IOException e) {
                    holder.photo.setImageResource(R.drawable.category_none);
                }
            } else {
                holder.photo.setImageResource(R.drawable.category_none);
            }
            
            if (!TextUtils.isEmpty(tip.getText())) {
                holder.body.setText(tip.getText());
                holder.body.setVisibility(View.VISIBLE);
            } else {
                if (mSdk > 3) {
                    holder.body.setVisibility(View.GONE);
                } else {
                    holder.body.setText("");
                    holder.body.setVisibility(View.INVISIBLE);
                }
            }
            
            if (tip.getUser() != null) {
                holder.dateAndAuthor.setText(
                    holder.dateAndAuthor.getText() +
                        mResources.getString(
                            R.string.tip_age_via, 
                            StringFormatters.getUserFullName(tip.getUser())));
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
        } else {
            holder.title.setText("");
            holder.body.setText("");
            holder.corner.setVisibility(View.VISIBLE);
            holder.corner.setImageResource(R.drawable.tip_list_item_corner_todo);
            holder.photo.setImageResource(R.drawable.category_none);
        }
        
        holder.dateAndAuthor.setText(mResources.getString(
                R.string.todo_added_date,
                mCachedTimestamps.get(todo.getId())));
        
        return convertView;
    }

    public void removeItem(int position) throws IndexOutOfBoundsException {
        group.remove(position);
        notifyDataSetInvalidated();
    }
    
    @Override
    public void setGroup(Group<Todo> g) {
        super.setGroup(g);
        mLoadedPhotoIndex = 0;
        
        mHandler.postDelayed(mRunnableLoadPhotos, 10L);
        
        mCachedTimestamps.clear();
        for (Todo it : g) {
            String formatted = StringFormatters.getTipAge(mResources, it.getCreated()); 
            mCachedTimestamps.put(it.getId(), formatted);
        }
    }
    
    public void setDisplayTodoVenueTitles(boolean displayTodoVenueTitles) {
    	mDisplayVenueTitles = displayTodoVenueTitles;
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
                Todo todo = (Todo)getItem(mLoadedPhotoIndex++);
                if (todo.getTip() != null && todo.getTip().getVenue() != null) {
                    Venue venue = todo.getTip().getVenue();
                    if (venue.getCategory() != null) {
                        Uri photoUri = Uri.parse(venue.getCategory().getIconUrl());
                        if (!mRrm.exists(photoUri)) {
                            mRrm.request(photoUri); 
                        }
                        mHandler.postDelayed(mRunnableLoadPhotos, 200L);
                    }
                }
            }
        }
    };

    static class ViewHolder {
        ImageView photo;
        TextView title;
        TextView body;
        TextView dateAndAuthor;
        ImageView corner;
    }
}
