/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared.widget;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquared.R;
import com.joelapenna.foursquared.util.RemoteResourceManager;
import com.joelapenna.foursquared.util.UserUtils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

/**
 * @date September 25, 2010
 * @author Mark Wyszomierski (markww@gmail.com), foursquare.
 */
public class FriendActionableListAdapter extends BaseGroupAdapter<User> 
    implements ObservableAdapter {

    private LayoutInflater mInflater;
    private int mLayoutToInflate;
    private ButtonRowClickHandler mClickListener;
    private RemoteResourceManager mRrm;
    private RemoteResourceManagerObserver mResourcesObserver;
    private Handler mHandler = new Handler();
    private int mLoadedPhotoIndex;
    

    public FriendActionableListAdapter(Context context, ButtonRowClickHandler clickListener,
            RemoteResourceManager rrm) {
        super(context);
        mInflater = LayoutInflater.from(context);
        mLayoutToInflate = R.layout.friend_actionable_list_item;
        mClickListener = clickListener;
        mRrm = rrm;
        mResourcesObserver = new RemoteResourceManagerObserver();
        mLoadedPhotoIndex = 0;

        mRrm.addObserver(mResourcesObserver);
    }

    public FriendActionableListAdapter(Context context, int layoutResource) {
        super(context);
        mInflater = LayoutInflater.from(context);
        mLayoutToInflate = layoutResource;
    }

    public void removeObserver() {
        mHandler.removeCallbacks(mRunnableLoadPhotos);
        mHandler.removeCallbacks(mUpdatePhotos);
        mRrm.deleteObserver(mResourcesObserver);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = mInflater.inflate(mLayoutToInflate, null);

            holder = new ViewHolder();
            holder.photo = (ImageView) convertView.findViewById(R.id.photo);
            holder.name = (TextView) convertView.findViewById(R.id.name);
            holder.btn1 = (Button) convertView.findViewById(R.id.btn1);
            holder.btn2 = (Button) convertView.findViewById(R.id.btn2);
            
            convertView.setTag(holder);

            holder.btn1.setOnClickListener(mOnClickListenerBtn1);
            holder.btn2.setOnClickListener(mOnClickListenerBtn2);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        User user = (User) getItem(position);

        final Uri photoUri = Uri.parse(user.getPhoto());
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

        holder.name.setText(user.getFirstname() + " "
                + (user.getLastname() != null ? user.getLastname() : ""));
        if (UserUtils.isFollower(user)) {
            holder.btn1.setVisibility(View.VISIBLE);
            holder.btn2.setVisibility(View.VISIBLE);
            holder.btn1.setTag(user);
            holder.btn2.setTag(user);
        } else {
            // Eventually we may have items for this case, like 'delete friend'.
            holder.btn1.setVisibility(View.GONE);
            holder.btn2.setVisibility(View.GONE);
        }
        
        return convertView;
    }

    private OnClickListener mOnClickListenerBtn1 = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mClickListener != null) {
                User user = (User) v.getTag();
                mClickListener.onBtnClickBtn1(user);
            }
        }
    };

    private OnClickListener mOnClickListenerBtn2 = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mClickListener != null) {
                User user = (User) v.getTag();
                mClickListener.onBtnClickBtn2(user);
            }
        }
    };

    public void removeItem(int position) throws IndexOutOfBoundsException {
        group.remove(position);
        notifyDataSetInvalidated();
    }

    @Override
    public void setGroup(Group<User> g) {
        super.setGroup(g);
        mLoadedPhotoIndex = 0;
        
        mHandler.postDelayed(mRunnableLoadPhotos, 10L);
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
                User user = (User)getItem(mLoadedPhotoIndex++);
                Uri photoUri = Uri.parse(user.getPhoto());
                if (!mRrm.exists(photoUri)) {
                    mRrm.request(photoUri);
                } 
                mHandler.postDelayed(mRunnableLoadPhotos, 200L);
            }
        }
    };

    static class ViewHolder {
        ImageView photo;
        TextView name;
        Button btn1;
        Button btn2;
    }

    public interface ButtonRowClickHandler {
        public void onBtnClickBtn1(User user);
        public void onBtnClickBtn2(User user);
    }
}
