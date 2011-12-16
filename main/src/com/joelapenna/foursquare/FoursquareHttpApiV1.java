/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquare;

import com.joelapenna.foursquare.error.FoursquareCredentialsException;
import com.joelapenna.foursquare.error.FoursquareError;
import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquare.error.FoursquareParseException;
import com.joelapenna.foursquare.http.AbstractHttpApi;
import com.joelapenna.foursquare.http.HttpApi;
import com.joelapenna.foursquare.http.HttpApiWithBasicAuth;
import com.joelapenna.foursquare.http.HttpApiWithOAuth;
import com.joelapenna.foursquare.parsers.json.CategoryParser;
import com.joelapenna.foursquare.parsers.json.CheckinParser;
import com.joelapenna.foursquare.parsers.json.CheckinResultParser;
import com.joelapenna.foursquare.parsers.json.CityParser;
import com.joelapenna.foursquare.parsers.json.CredentialsParser;
import com.joelapenna.foursquare.parsers.json.FriendInvitesResultParser;
import com.joelapenna.foursquare.parsers.json.GroupParser;
import com.joelapenna.foursquare.parsers.json.ResponseParser;
import com.joelapenna.foursquare.parsers.json.SettingsParser;
import com.joelapenna.foursquare.parsers.json.TipParser;
import com.joelapenna.foursquare.parsers.json.TodoParser;
import com.joelapenna.foursquare.parsers.json.UserParser;
import com.joelapenna.foursquare.parsers.json.VenueParser;
import com.joelapenna.foursquare.types.Category;
import com.joelapenna.foursquare.types.Checkin;
import com.joelapenna.foursquare.types.CheckinResult;
import com.joelapenna.foursquare.types.City;
import com.joelapenna.foursquare.types.Credentials;
import com.joelapenna.foursquare.types.FriendInvitesResult;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Response;
import com.joelapenna.foursquare.types.Settings;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquare.util.JSONUtils;
import com.joelapenna.foursquared.util.Base64Coder;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 */
class FoursquareHttpApiV1 {
    private static final Logger LOG = Logger
            .getLogger(FoursquareHttpApiV1.class.getCanonicalName());
    private static final boolean DEBUG = Foursquare.DEBUG;

    private static final String DATATYPE = ".json";
    
    private static final String URL_API_AUTHEXCHANGE = "/authexchange";

    private static final String URL_API_ADDVENUE = "/addvenue";
    private static final String URL_API_ADDTIP = "/addtip";
    private static final String URL_API_CITIES = "/cities";
    private static final String URL_API_CHECKINS = "/checkins";
    private static final String URL_API_CHECKIN = "/checkin";
    private static final String URL_API_USER = "/user";
    private static final String URL_API_VENUE = "/venue";
    private static final String URL_API_VENUES = "/venues";
    private static final String URL_API_TIPS = "/tips";
    private static final String URL_API_TODOS = "/todos";
    private static final String URL_API_FRIEND_REQUESTS = "/friend/requests";
    private static final String URL_API_FRIEND_APPROVE = "/friend/approve";
    private static final String URL_API_FRIEND_DENY = "/friend/deny";
    private static final String URL_API_FRIEND_SENDREQUEST = "/friend/sendrequest";
    private static final String URL_API_FRIENDS = "/friends";
    private static final String URL_API_FIND_FRIENDS_BY_NAME = "/findfriends/byname";
    private static final String URL_API_FIND_FRIENDS_BY_PHONE = "/findfriends/byphone";
    private static final String URL_API_FIND_FRIENDS_BY_FACEBOOK = "/findfriends/byfacebook";
    private static final String URL_API_FIND_FRIENDS_BY_TWITTER = "/findfriends/bytwitter";
    private static final String URL_API_CATEGORIES = "/categories";
    private static final String URL_API_HISTORY = "/history";
    private static final String URL_API_FIND_FRIENDS_BY_PHONE_OR_EMAIL = "/findfriends/byphoneoremail";
    private static final String URL_API_INVITE_BY_EMAIL = "/invite/byemail";
    private static final String URL_API_SETPINGS = "/settings/setpings";
    private static final String URL_API_VENUE_FLAG_CLOSED = "/venue/flagclosed";
    private static final String URL_API_VENUE_FLAG_MISLOCATED = "/venue/flagmislocated";
    private static final String URL_API_VENUE_FLAG_DUPLICATE = "/venue/flagduplicate";
    private static final String URL_API_VENUE_PROPOSE_EDIT = "/venue/proposeedit";
    private static final String URL_API_USER_UPDATE = "/user/update";
    private static final String URL_API_MARK_TODO = "/mark/todo";
    private static final String URL_API_MARK_IGNORE = "/mark/ignore";
    private static final String URL_API_MARK_DONE = "/mark/done";
    private static final String URL_API_UNMARK_TODO = "/unmark/todo";
    private static final String URL_API_UNMARK_DONE = "/unmark/done";
    private static final String URL_API_TIP_DETAIL = "/tip/detail";
    
    private final DefaultHttpClient mHttpClient = AbstractHttpApi.createHttpClient();
    private HttpApi mHttpApi;

    private final String mApiBaseUrl;
    private final AuthScope mAuthScope;

    public FoursquareHttpApiV1(String domain, String clientVersion, boolean useOAuth) {
        mApiBaseUrl = "http://" + domain + "/v1";
        mAuthScope = new AuthScope(domain, 80);
        
        if (useOAuth) {
            mHttpApi = new HttpApiWithOAuth(mHttpClient, clientVersion);
        } else {
            mHttpApi = new HttpApiWithBasicAuth(mHttpClient, clientVersion);
        }
    }

    void setCredentials(String phone, String password) {
        if (phone == null || phone.length() == 0 || password == null || password.length() == 0) {
            if (DEBUG) LOG.log(Level.FINE, "Clearing Credentials");
            mHttpClient.getCredentialsProvider().clear();
        } else {
            if (DEBUG) LOG.log(Level.FINE, "Setting Phone/Password: " + phone + "/******");
            mHttpClient.getCredentialsProvider().setCredentials(mAuthScope,
                    new UsernamePasswordCredentials(phone, password));
        }
    }

    public boolean hasCredentials() {
        return mHttpClient.getCredentialsProvider().getCredentials(mAuthScope) != null;
    }

    public void setOAuthConsumerCredentials(String oAuthConsumerKey, String oAuthConsumerSecret) {
        if (DEBUG) {
            LOG.log(Level.FINE, "Setting consumer key/secret: " + oAuthConsumerKey + " "
                    + oAuthConsumerSecret);
        }
        ((HttpApiWithOAuth) mHttpApi).setOAuthConsumerCredentials(oAuthConsumerKey,
                oAuthConsumerSecret);
    }

    public void setOAuthTokenWithSecret(String token, String secret) {
        if (DEBUG) LOG.log(Level.FINE, "Setting oauth token/secret: " + token + " " + secret);
        ((HttpApiWithOAuth) mHttpApi).setOAuthTokenWithSecret(token, secret);
    }

    public boolean hasOAuthTokenWithSecret() {
        return ((HttpApiWithOAuth) mHttpApi).hasOAuthTokenWithSecret();
    }

    /*
     * /authexchange?oauth_consumer_key=d123...a1bffb5&oauth_consumer_secret=fec...
     * 18
     */
    public Credentials authExchange(String phone, String password) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        if (((HttpApiWithOAuth) mHttpApi).hasOAuthTokenWithSecret()) {
            throw new IllegalStateException("Cannot do authExchange with OAuthToken already set");
        }
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_AUTHEXCHANGE), //
                new BasicNameValuePair("fs_username", phone), //
                new BasicNameValuePair("fs_password", password));
        return (Credentials) mHttpApi.doHttpRequest(httpPost, new CredentialsParser());
    }

    /*
     * /addtip?vid=1234&text=I%20added%20a%20tip&type=todo (type defaults "tip")
     */
    Tip addtip(String vid, String text, String type, String geolat, String geolong, String geohacc,
            String geovacc, String geoalt) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_ADDTIP), //
                new BasicNameValuePair("vid", vid), //
                new BasicNameValuePair("text", text), //
                new BasicNameValuePair("type", type), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt));
        return (Tip) mHttpApi.doHttpRequest(httpPost, new TipParser());
    }

    /**
     * @param name the name of the venue
     * @param address the address of the venue (e.g., "202 1st Avenue")
     * @param crossstreet the cross streets (e.g., "btw Grand & Broome")
     * @param city the city name where this venue is
     * @param state the state where the city is
     * @param zip (optional) the ZIP code for the venue
     * @param phone (optional) the phone number for the venue
     * @return
     * @throws FoursquareException
     * @throws FoursquareCredentialsException
     * @throws FoursquareError
     * @throws IOException
     */
    Venue addvenue(String name, String address, String crossstreet, String city, String state,
            String zip, String phone, String categoryId, String geolat, String geolong, String geohacc,
            String geovacc, String geoalt) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_ADDVENUE), //
                new BasicNameValuePair("name", name), //
                new BasicNameValuePair("address", address), //
                new BasicNameValuePair("crossstreet", crossstreet), //
                new BasicNameValuePair("city", city), //
                new BasicNameValuePair("state", state), //
                new BasicNameValuePair("zip", zip), //
                new BasicNameValuePair("phone", phone), //
                new BasicNameValuePair("primarycategoryid", categoryId), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt) //
                );
        return (Venue) mHttpApi.doHttpRequest(httpPost, new VenueParser());
    }

    /*
     * /cities
     */
    @SuppressWarnings("unchecked")
    Group<City> cities() throws FoursquareException, FoursquareCredentialsException,
            FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_CITIES));
        return (Group<City>) mHttpApi.doHttpRequest(httpGet, new GroupParser(new CityParser()));
    }

    /*
     * /checkins?
     */
    @SuppressWarnings("unchecked")
    Group<Checkin> checkins(String geolat, String geolong, String geohacc, String geovacc,
            String geoalt) throws FoursquareException, FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_CHECKINS), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt));
        return (Group<Checkin>) mHttpApi.doHttpRequest(httpGet,
                new GroupParser(new CheckinParser()));
    }

    /*
     * /checkin?vid=1234&venue=Noc%20Noc&shout=Come%20here&private=0&twitter=1
     */
    CheckinResult checkin(String vid, String venue, String geolat, String geolong, String geohacc,
            String geovacc, String geoalt, String shout, boolean isPrivate, boolean tellFollowers,
            boolean twitter, boolean facebook) throws FoursquareException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_CHECKIN), //
                new BasicNameValuePair("vid", vid), //
                new BasicNameValuePair("venue", venue), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt), //
                new BasicNameValuePair("shout", shout), //
                new BasicNameValuePair("private", (isPrivate) ? "1" : "0"), //
                new BasicNameValuePair("followers", (tellFollowers) ? "1" : "0"), //
                new BasicNameValuePair("twitter", (twitter) ? "1" : "0"), //
                new BasicNameValuePair("facebook", (facebook) ? "1" : "0"), //
                new BasicNameValuePair("markup", "android")); // used only by android for checkin result 'extras'.
        return (CheckinResult) mHttpApi.doHttpRequest(httpPost, new CheckinResultParser());
    }

    /**
     * /user?uid=9937
     */
    User user(String uid, boolean mayor, boolean badges, boolean stats, String geolat, String geolong,
            String geohacc, String geovacc, String geoalt) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_USER), //
                new BasicNameValuePair("uid", uid), //
                new BasicNameValuePair("mayor", (mayor) ? "1" : "0"), //
                new BasicNameValuePair("badges", (badges) ? "1" : "0"), //
                new BasicNameValuePair("stats", (stats) ? "1" : "0"), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt) //
                );
        return (User) mHttpApi.doHttpRequest(httpGet, new UserParser());
    }

    /**
     * /venues?geolat=37.770900&geolong=-122.43698
     */
    @SuppressWarnings("unchecked")
    Group<Group<Venue>> venues(String geolat, String geolong, String geohacc, String geovacc,
            String geoalt, String query, int limit) throws FoursquareException, FoursquareError,
            IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_VENUES), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt), //
                new BasicNameValuePair("q", query), //
                new BasicNameValuePair("l", String.valueOf(limit)));
        return (Group<Group<Venue>>) mHttpApi.doHttpRequest(httpGet, new GroupParser(
                new GroupParser(new VenueParser())));
    }

    /**
     * /venue?vid=1234
     */
    Venue venue(String vid, String geolat, String geolong, String geohacc, String geovacc,
            String geoalt) throws FoursquareException, FoursquareCredentialsException,
            FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_VENUE), //
                new BasicNameValuePair("vid", vid), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt) //
                );
        return (Venue) mHttpApi.doHttpRequest(httpGet, new VenueParser());
    }
    
    /**
     * /tips?geolat=37.770900&geolong=-122.436987&l=1
     */
    @SuppressWarnings("unchecked")
    Group<Tip> tips(String geolat, String geolong, String geohacc, String geovacc,
            String geoalt, String uid, String filter, String sort, int limit) throws FoursquareException, 
            FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_TIPS), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt), //
                new BasicNameValuePair("uid", uid), //
                new BasicNameValuePair("filter", filter), //
                new BasicNameValuePair("sort", sort), //
                new BasicNameValuePair("l", String.valueOf(limit)) //
                );
        return (Group<Tip>) mHttpApi.doHttpRequest(httpGet, new GroupParser(
                new TipParser()));
    }
    
    /**
     * /todos?geolat=37.770900&geolong=-122.436987&l=1&sort=[recent|nearby]
     */
    @SuppressWarnings("unchecked")
    Group<Todo> todos(String uid, String geolat, String geolong, String geohacc, String geovacc,
            String geoalt, boolean recent, boolean nearby, int limit) 
            throws FoursquareException, FoursquareError, IOException {
        String sort = null;
        if (recent) {
            sort = "recent";
        } else if (nearby) {
            sort = "nearby";
        }
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_TODOS), //
                new BasicNameValuePair("uid", uid), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt), //
                new BasicNameValuePair("sort", sort), //
                new BasicNameValuePair("l", String.valueOf(limit)) //
               );
        return (Group<Todo>) mHttpApi.doHttpRequest(httpGet, new GroupParser(
                new TodoParser()));
    }
    
    /*
     * /friends?uid=9937
     */
    @SuppressWarnings("unchecked")
    Group<User> friends(String uid, String geolat, String geolong, String geohacc, String geovacc,
            String geoalt) throws FoursquareException, FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_FRIENDS), //
                new BasicNameValuePair("uid", uid), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt) //
                );
        return (Group<User>) mHttpApi.doHttpRequest(httpGet, new GroupParser(new UserParser()));
    }

    /*
     * /friend/requests
     */
    @SuppressWarnings("unchecked")
    Group<User> friendRequests() throws FoursquareException, FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_FRIEND_REQUESTS));
        return (Group<User>) mHttpApi.doHttpRequest(httpGet, new GroupParser(new UserParser()));
    }

    /*
     * /friend/approve?uid=9937
     */
    User friendApprove(String uid) throws FoursquareException, FoursquareCredentialsException,
            FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FRIEND_APPROVE), //
                new BasicNameValuePair("uid", uid));
        return (User) mHttpApi.doHttpRequest(httpPost, new UserParser());
    }

    /*
     * /friend/deny?uid=9937
     */
    User friendDeny(String uid) throws FoursquareException, FoursquareCredentialsException,
            FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FRIEND_DENY), //
                new BasicNameValuePair("uid", uid));
        return (User) mHttpApi.doHttpRequest(httpPost, new UserParser());
    }

    /*
     * /friend/sendrequest?uid=9937
     */
    User friendSendrequest(String uid) throws FoursquareException, FoursquareCredentialsException,
            FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FRIEND_SENDREQUEST), //
                new BasicNameValuePair("uid", uid));
        return (User) mHttpApi.doHttpRequest(httpPost, new UserParser());
    }

    /**
     * /findfriends/byname?q=john doe, mary smith
     */
    @SuppressWarnings("unchecked")
    public Group<User> findFriendsByName(String text) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_FIND_FRIENDS_BY_NAME), //
                new BasicNameValuePair("q", text));
        return (Group<User>) mHttpApi.doHttpRequest(httpGet, new GroupParser(new UserParser()));
    }

    /**
     * /findfriends/byphone?q=555-5555,555-5556
     */
    @SuppressWarnings("unchecked")
    public Group<User> findFriendsByPhone(String text) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FIND_FRIENDS_BY_PHONE), //
                new BasicNameValuePair("q", text));
        return (Group<User>) mHttpApi.doHttpRequest(httpPost, new GroupParser(new UserParser()));
    }

    /**
     * /findfriends/byfacebook?q=friendid,friendid,friendid
     */
    @SuppressWarnings("unchecked")
    public Group<User> findFriendsByFacebook(String text) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FIND_FRIENDS_BY_FACEBOOK), //
                new BasicNameValuePair("q", text));
        return (Group<User>) mHttpApi.doHttpRequest(httpPost, new GroupParser(new UserParser()));
    }
    
    /**
     * /findfriends/bytwitter?q=yourtwittername
     */
    @SuppressWarnings("unchecked")
    public Group<User> findFriendsByTwitter(String text) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_FIND_FRIENDS_BY_TWITTER), //
                new BasicNameValuePair("q", text));
        return (Group<User>) mHttpApi.doHttpRequest(httpGet, new GroupParser(new UserParser()));
    }
    
    /**
     * /categories
     */
    @SuppressWarnings("unchecked")
    public Group<Category> categories() throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_CATEGORIES));
        return (Group<Category>) mHttpApi.doHttpRequest(httpGet, new GroupParser(new CategoryParser()));
    }

    /**
     * /history
     */
    @SuppressWarnings("unchecked")
    public Group<Checkin> history(String limit, String sinceid) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_HISTORY),
            new BasicNameValuePair("l", limit),
            new BasicNameValuePair("sinceid", sinceid));
        return (Group<Checkin>) mHttpApi.doHttpRequest(httpGet, new GroupParser(new CheckinParser()));
    }
    
    /**
     * /mark/todo
     */
    public Todo markTodo(String tid) throws FoursquareException,
        FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_MARK_TODO), //
                new BasicNameValuePair("tid", tid));
        return (Todo) mHttpApi.doHttpRequest(httpPost, new TodoParser());
    }
    
    /**
     * This is a hacky special case, hopefully the api will be updated in v2 for this.
     * /mark/todo
     */
    public Todo markTodoVenue(String vid) throws FoursquareException,
        FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_MARK_TODO), //
                new BasicNameValuePair("vid", vid));
        return (Todo) mHttpApi.doHttpRequest(httpPost, new TodoParser());
    }
    
    /**
     * /mark/ignore
     */
    public Tip markIgnore(String tid) throws FoursquareException,
        FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_MARK_IGNORE), //
                new BasicNameValuePair("tid", tid));
        return (Tip) mHttpApi.doHttpRequest(httpPost, new TipParser());
    }
    
    /**
     * /mark/done
     */
    public Tip markDone(String tid) throws FoursquareException,
        FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_MARK_DONE), //
                new BasicNameValuePair("tid", tid));
        return (Tip) mHttpApi.doHttpRequest(httpPost, new TipParser());
    }
    
    /**
     * /unmark/todo
     */
    public Tip unmarkTodo(String tid) throws FoursquareException,
        FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_UNMARK_TODO), //
                    new BasicNameValuePair("tid", tid));
        return (Tip) mHttpApi.doHttpRequest(httpPost, new TipParser());
    }
    
    /**
     * /unmark/done
     */
    public Tip unmarkDone(String tid) throws FoursquareException,
        FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_UNMARK_DONE), //
                    new BasicNameValuePair("tid", tid));
        return (Tip) mHttpApi.doHttpRequest(httpPost, new TipParser());
    }

    /**
     * /tip/detail?tid=1234
     */
    public Tip tipDetail(String tid) throws FoursquareException,
        FoursquareCredentialsException, FoursquareError, IOException {
        HttpGet httpGet = mHttpApi.createHttpGet(fullUrl(URL_API_TIP_DETAIL), //
                new BasicNameValuePair("tid", tid));
        return (Tip) mHttpApi.doHttpRequest(httpGet, new TipParser());
    }

    /**
     * /findfriends/byphoneoremail?p=comma-sep-list-of-phones&e=comma-sep-list-of-emails
     */
    public FriendInvitesResult findFriendsByPhoneOrEmail(String phones, String emails) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_FIND_FRIENDS_BY_PHONE_OR_EMAIL), //
                new BasicNameValuePair("p", phones),
                new BasicNameValuePair("e", emails));
        return (FriendInvitesResult) mHttpApi.doHttpRequest(httpPost, new FriendInvitesResultParser());
    }
    
    /**
     * /invite/byemail?q=comma-sep-list-of-emails
     */
    public Response inviteByEmail(String emails) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_INVITE_BY_EMAIL), //
                new BasicNameValuePair("q", emails));
        return (Response) mHttpApi.doHttpRequest(httpPost, new ResponseParser());
    }
    
    /**
     * /settings/setpings?self=[on|off]
     */
    public Settings setpings(boolean on) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_SETPINGS), //
                new BasicNameValuePair("self", on ? "on" : "off"));
        return (Settings) mHttpApi.doHttpRequest(httpPost, new SettingsParser());
    }
    
    /**
     * /settings/setpings?uid=userid
     */
    public Settings setpings(String userid, boolean on) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_SETPINGS), //
                new BasicNameValuePair(userid, on ? "on" : "off"));
        return (Settings) mHttpApi.doHttpRequest(httpPost, new SettingsParser());
    }
    
    /**
     * /venue/flagclosed?vid=venueid
     */
    public Response flagclosed(String venueId) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_VENUE_FLAG_CLOSED), //
                new BasicNameValuePair("vid", venueId));
        return (Response) mHttpApi.doHttpRequest(httpPost, new ResponseParser());
    }

    /**
     * /venue/flagmislocated?vid=venueid
     */
    public Response flagmislocated(String venueId) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_VENUE_FLAG_MISLOCATED), //
                new BasicNameValuePair("vid", venueId));
        return (Response) mHttpApi.doHttpRequest(httpPost, new ResponseParser());
    }

    /**
     * /venue/flagduplicate?vid=venueid
     */
    public Response flagduplicate(String venueId) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_VENUE_FLAG_DUPLICATE), //
                new BasicNameValuePair("vid", venueId));
        return (Response) mHttpApi.doHttpRequest(httpPost, new ResponseParser());
    }
    
    /**
     * /venue/prposeedit?vid=venueid&name=...
     */
    public Response proposeedit(String venueId, String name, String address, String crossstreet, 
            String city, String state, String zip, String phone, String categoryId, String geolat, 
            String geolong, String geohacc, String geovacc, String geoalt) throws FoursquareException,
            FoursquareCredentialsException, FoursquareError, IOException {
        HttpPost httpPost = mHttpApi.createHttpPost(fullUrl(URL_API_VENUE_PROPOSE_EDIT), //
                new BasicNameValuePair("vid", venueId), //
                new BasicNameValuePair("name", name), //
                new BasicNameValuePair("address", address), //
                new BasicNameValuePair("crossstreet", crossstreet), //
                new BasicNameValuePair("city", city), //
                new BasicNameValuePair("state", state), //
                new BasicNameValuePair("zip", zip), //
                new BasicNameValuePair("phone", phone), //
                new BasicNameValuePair("primarycategoryid", categoryId), //
                new BasicNameValuePair("geolat", geolat), //
                new BasicNameValuePair("geolong", geolong), //
                new BasicNameValuePair("geohacc", geohacc), //
                new BasicNameValuePair("geovacc", geovacc), //
                new BasicNameValuePair("geoalt", geoalt) //
                );
        return (Response) mHttpApi.doHttpRequest(httpPost, new ResponseParser());
    }
    
    private String fullUrl(String url) {
        return mApiBaseUrl + url + DATATYPE;
    }
    
    /**
     * /user/update
     * Need to bring this method under control like the rest of the api methods. Leaving it 
     * in this state as authorization will probably switch from basic auth in the near future
     * anyway, will have to be updated. Also unlike the other methods, we're sending up data
     * which aren't basic name/value pairs.
     */
    public User userUpdate(String imagePathToJpg, String username, String password) 
        throws SocketTimeoutException, IOException, FoursquareError, FoursquareParseException {
        String BOUNDARY = "------------------319831265358979362846";
        String lineEnd = "\r\n"; 
        String twoHyphens = "--";
        int maxBufferSize = 8192;
        
        File file = new File(imagePathToJpg);
        FileInputStream fileInputStream = new FileInputStream(file);
        
        URL url = new URL(fullUrl(URL_API_USER_UPDATE));
        HttpURLConnection conn = mHttpApi.createHttpURLConnectionPost(url, BOUNDARY);
        conn.setRequestProperty("Authorization", "Basic " +  Base64Coder.encodeString(username + ":" + password));
        
        // We are always saving the image to a jpg so we can use .jpg as the extension below.
        DataOutputStream dos = new DataOutputStream(conn.getOutputStream()); 
        dos.writeBytes(twoHyphens + BOUNDARY + lineEnd); 
        dos.writeBytes("Content-Disposition: form-data; name=\"image,jpeg\";filename=\"" + "image.jpeg" +"\"" + lineEnd); 
        dos.writeBytes("Content-Type: " + "image/jpeg" + lineEnd);
        dos.writeBytes(lineEnd); 
        
        int bytesAvailable = fileInputStream.available(); 
        int bufferSize = Math.min(bytesAvailable, maxBufferSize); 
        byte[] buffer = new byte[bufferSize]; 
        
        int bytesRead = fileInputStream.read(buffer, 0, bufferSize); 
        int totalBytesRead = bytesRead;
        while (bytesRead > 0) {
            dos.write(buffer, 0, bufferSize); 
            bytesAvailable = fileInputStream.available(); 
            bufferSize = Math.min(bytesAvailable, maxBufferSize); 
            bytesRead = fileInputStream.read(buffer, 0, bufferSize); 
            totalBytesRead = totalBytesRead  + bytesRead;
        }
        dos.writeBytes(lineEnd); 
        dos.writeBytes(twoHyphens + BOUNDARY + twoHyphens + lineEnd); 
        
        fileInputStream.close(); 
        dos.flush(); 
        dos.close(); 
     
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String responseLine = "";
        while ((responseLine = in.readLine()) != null) {
            response.append(responseLine);
        }
        in.close();

        try {
            return (User)JSONUtils.consume(new UserParser(), response.toString());
        } catch (Exception ex) {
            throw new FoursquareParseException(
                    "Error parsing user photo upload response, invalid json.");
        }
    }
}
