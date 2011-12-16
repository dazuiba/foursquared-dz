/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared.widget;

import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquared.R;
import com.joelapenna.foursquared.util.UserUtils;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * 
 * @date September 23, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 *
 */
public class UserContactAdapter extends BaseAdapter {
    
    private LayoutInflater mInflater;
    private int mLayoutToInflate;
    private User mUser;
    private ArrayList<Action> mActions;

    public UserContactAdapter(Context context, User user) {
        super();
        
        mInflater = LayoutInflater.from(context);
        mLayoutToInflate = R.layout.user_actions_list_item;
        mUser = user;

        mActions = new ArrayList<Action>();
        if (user != null) {
            if (UserUtils.isFriend(user)) {
                if (TextUtils.isEmpty(mUser.getPhone()) == false) {
                    mActions.add(new Action(context.getResources().getString(
                            R.string.user_actions_activity_action_sms),
                            R.drawable.user_action_text, Action.ACTION_ID_SMS, false));
                }
                if (TextUtils.isEmpty(mUser.getEmail()) == false) {
                    mActions.add(new Action(context.getResources().getString(
                            R.string.user_actions_activity_action_email),
                            R.drawable.user_action_email, Action.ACTION_ID_EMAIL, false));
                }
                if (TextUtils.isEmpty(mUser.getEmail()) == false) {
                    mActions.add(new Action(context.getResources().getString(
                            R.string.user_actions_activity_action_phone),
                            R.drawable.user_action_phone, Action.ACTION_ID_PHONE, false));
                }
            } 

            if (TextUtils.isEmpty(mUser.getTwitter()) == false) {
                mActions.add(new Action(context.getResources().getString(
                        R.string.user_actions_activity_action_twitter),
                        R.drawable.user_action_twitter, Action.ACTION_ID_TWITTER, true));
            }
            if (TextUtils.isEmpty(mUser.getFacebook()) == false) {
                mActions.add(new Action(context.getResources().getString(
                        R.string.user_actions_activity_action_facebook),
                        R.drawable.user_action_facebook, Action.ACTION_ID_FACEBOOK, true));
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = mInflater.inflate(mLayoutToInflate, null);
        }

        ImageView iv = (ImageView) convertView.findViewById(R.id.userActionsListItemIcon);
        TextView tv = (TextView) convertView.findViewById(R.id.userActionsListItemLabel);

        Action action = (Action) getItem(position);
        iv.setImageResource(action.getIconId());
        tv.setText(action.getLabel());

        return convertView;
    }

    @Override
    public int getCount() {
        return mActions.size();
    }

    @Override
    public Object getItem(int position) {
        return mActions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
    
    
    public static class Action {
        public static final int ACTION_ID_SMS      = 0;
        public static final int ACTION_ID_EMAIL    = 1;
        public static final int ACTION_ID_PHONE    = 2;
        public static final int ACTION_ID_TWITTER  = 3;
        public static final int ACTION_ID_FACEBOOK = 4;
        
        private String mLabel;
        private int mIconId;
        private int mActionId;
        private boolean mIsExternalAction;

        public Action(String label, int iconId, int actionId, boolean isExternalAction) {
            mLabel = label;
            mIconId = iconId;
            mActionId = actionId;
            mIsExternalAction = isExternalAction;
        }

        public String getLabel() {
            return mLabel;
        }

        public int getIconId() {
            return mIconId;
        }

        public int getActionId() {
            return mActionId;
        }

        public boolean getIsExternalAction() {
            return mIsExternalAction;
        }
    }
}