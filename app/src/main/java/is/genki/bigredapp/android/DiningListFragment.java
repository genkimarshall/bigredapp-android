package is.genki.bigredapp.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.android.swiperefreshlistfragment.SwipeRefreshListFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fragment containing a ListView of dining halls.
 * Each dining hall name can be tapped to get it's menu for today.
 * Has swipe-to-refresh, see:
 * https://developer.android.com/samples/SwipeRefreshListFragment/src/
 *  com.example.android.swiperefreshlistfragment/SwipeRefreshListFragmentFragment.html
 */
public class DiningListFragment extends SwipeRefreshListFragment {

    private static final int MS_IN_10_MIN = 600000;
    private static final int MS_IN_HOUR = 3600000;
    private static final long MS_IN_2_WEEKS = 1209600000;

    public static final String BASE_URL = "http://redapi-tious.rhcloud.com/dining";
    private static final String DINING_LIST_KEY = "DINING_LIST_KEY";
    private static final String DINING_LIST_DATE_KEY = "DINING_LIST_DATE_KEY";
    private static final String LAST_REFRESHED_KEY = "LAST_REFRESHED_KEY";
    private static final int NUM_DAYS_OF_EVENTS_TO_GET = 6;

    private static Context mContext;
    private JSONArray mDiningList;
    private SharedPreferences mPreferences;
    private DateFormat mDateFormat;
    private static int mTextColor;
    private boolean onCreateHasJustFinished;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getActivity();

        // to parse: 2015-04-25T16:30:00.000Z
        mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        mPreferences = getActivity().getPreferences(Context.MODE_PRIVATE);

        refreshContent();
        onCreateHasJustFinished = true;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshContent();
            }
        });

        getSwipeRefreshLayout().setColorSchemeResources(R.color.primary, R.color.primaryDark);
    }

    /**
     * See http://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
     */
    @Override
    public void onResume() {
        super.onResume();
        if (onCreateHasJustFinished) {
            // create has just been called => there is already new content => don't refresh
            onCreateHasJustFinished = false;
        } else {
            // this is a true resume
            final long lastRefreshedTime = mPreferences.getLong(LAST_REFRESHED_KEY, 0);
            if (System.currentTimeMillis() - lastRefreshedTime >= MS_IN_10_MIN) {
                // if it has been at least 10 min since the data was refreshed, get new data
                refreshContent();
            }
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
            String diningHall = ((DiningHallListAdapter.DiningListViewHolder) v.getTag()).diningHallName;
            Intent intent = new Intent(mContext, DiningLocationActivity.class);
            intent.putExtra(DiningLocationActivity.KEY_DINING_HALL, diningHall);

            // ViewCompat.setTransitionName(view, "shared_transition");
            ActivityOptionsCompat options = ActivityOptionsCompat.makeScaleUpAnimation(
                    v, 0, 0, v.getWidth(), v.getHeight());
            mContext.startActivity(intent, options.toBundle());
    }

    /**
     * Refreshes the content, for initialization and swipe-refresh
     */
    private void refreshContent() {
        // check for a cached dining list to use
        final String cachedDiningList = mPreferences.getString(DINING_LIST_KEY, null);
        final long cachedDiningListDate = mPreferences.getLong(DINING_LIST_DATE_KEY, 0);
        if (cachedDiningList == null || cachedDiningListDate == 0 ||
                System.currentTimeMillis() - cachedDiningListDate >= MS_IN_2_WEEKS) {
            // no cached diningList that's younger than a week, so let's request a new one
            mDiningList = new JSONArray();
            getDiningList();
        } else {
            // we have a cached diningList that's younger than 2 weeks, so let's use it
            try {
                mDiningList = new JSONArray(cachedDiningList);
                // now we can get the calendar events for the list of dining halls
                getDiningCalendarEvents();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Handles the response from getDiningCalendarEvents().
     * Sets mListView's adapter on the parsed information.
     * When finished, stops the refreshing indicator.
     */
    private void handleCalendarEventsResponse(JSONObject response) {
        try {
            ArrayList<NameCalEventList> nameCalEventLists = new ArrayList<>();

            Calendar cal = Calendar.getInstance();
            int offsetUTC = (TimeZone.getDefault().getOffset(cal.getTimeInMillis())) / MS_IN_HOUR;

            // parse the calendar data into an ArrayList<NameCalEventList>
            int mDiningListLen = mDiningList.length();
            for (int i = 0; i < mDiningListLen; i++) {
                String name = (String) mDiningList.get(i);

                JSONArray jsonEventList = response.getJSONArray(name);
                int jsonEventListLen = jsonEventList.length();
                ArrayList<CalEvent> calEventList = new ArrayList<>();
                for (int j = 0; j < jsonEventListLen; j++) {
                    JSONObject jsonEvent = jsonEventList.getJSONObject(j);

                    CalEvent calEvent = new CalEvent();
                    String summary = jsonEvent.getString("summary").toLowerCase();
                    // we only care about open events
                    if (!summary.contains("closed")) {
                        Date startDate = mDateFormat.parse(jsonEvent.getString("start"));
                        cal = Calendar.getInstance();
                        cal.setTime(startDate);
                        cal.add(Calendar.HOUR_OF_DAY, offsetUTC);
                        calEvent.startCal = cal;

                        Date endDate = mDateFormat.parse(jsonEvent.getString("end"));
                        cal = Calendar.getInstance();
                        cal.setTime(endDate);
                        cal.add(Calendar.HOUR_OF_DAY, offsetUTC);
                        calEvent.endCal = cal;

                        calEventList.add(calEvent);
                    }
                }
                Collections.sort(calEventList);
                nameCalEventLists.add(new NameCalEventList(name, calEventList));
            }


            ArrayAdapter<NameCalEventList> adapter = (ArrayAdapter<NameCalEventList>) getListAdapter();
            //Order by what's open
            Collections.sort(nameCalEventLists);

            //If we are only showing open halls, hide the non-open ones.
            SharedPreferences settings = mContext.getSharedPreferences("BraPrefs", 0);
            boolean showOnlyOpenHalls = settings.getBoolean("showOnlyOpenHalls", false);
            if( showOnlyOpenHalls ){
                Iterator<NameCalEventList> iter = nameCalEventLists.iterator();
                while( iter.hasNext()){
                    NameCalEventList next = iter.next();
                    if( calToRatingInt(Calendar.getInstance(), next.calEventList) == 0)
                        iter.remove();
                }
            }

            if (adapter == null) {
                adapter = new DiningHallListAdapter(mContext, R.layout.dining_list_row, nameCalEventLists);
                setListAdapter(adapter);
            } else {
                adapter.clear();
                adapter.addAll(nameCalEventLists);
                adapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // update the last-refreshed time
        mPreferences.edit().putLong(LAST_REFRESHED_KEY, System.currentTimeMillis()).apply();
        // Stop the refreshing indicator
        setRefreshing(false);
    }

    // helper for getDiningCalendarEvents()
    private static String getRequestCalFormat(Calendar cal) {
        return cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US) +
                cal.get(Calendar.DAY_OF_MONTH) + "," + cal.get(Calendar.YEAR);
    }

    /**
     * Uses a Volley request to RedAPI to get the calendar events for all the dining halls.
     * Assumes mDiningList has already been set.
     * Calls handleDiningCalendarEvents on what is returned.
     */
    private void getDiningCalendarEvents() {
        try {
            final StringBuilder builder = new StringBuilder();
            int len = mDiningList.length();
            for (int i = 0; i < len; i++) {
                builder.append(mDiningList.get(i)).append(',');
            }
            final String commaSeparatedDiningHalls = builder.toString();

            // create the date range of CalEvents to get for each location
            final Calendar rightNow = Calendar.getInstance();
            rightNow.add(Calendar.DATE, -1); // so can handle open hours after midnight
            String dateRange = getRequestCalFormat(rightNow) + "-";
            rightNow.add(Calendar.DATE, NUM_DAYS_OF_EVENTS_TO_GET + 1);
            dateRange = dateRange + getRequestCalFormat(rightNow);
            final String diningHallCalendarsUrl = DiningListFragment.BASE_URL + "/event/" + commaSeparatedDiningHalls + "/" + dateRange;

            // See the "SingletonRequestQueue" Class
            JsonObjectRequest jsonObjectRequest = (JsonObjectRequest)
                    new JsonObjectRequest(Request.Method.GET, diningHallCalendarsUrl,
                            new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    handleCalendarEventsResponse(response);
                                }
                            }, SingletonRequestQueue.getErrorListener(mContext))
                            .setRetryPolicy(SingletonRequestQueue.getRetryPolicy());
            SingletonRequestQueue.getInstance(mContext).addToRequestQueue(jsonObjectRequest);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Uses a GetRequest to RedAPI to populate mDiningList with the String list of dining halls.
     * After it is done, calls getDiningCalendarEvents() now that mDiningList is set.
     * See the "SingletonRequestQueue" Class
     */
    private void getDiningList() {
        if (SingletonRequestQueue.isConnected(mContext)) {
            JsonArrayRequest jsonArrayRequest = (JsonArrayRequest)
                    new JsonArrayRequest(Request.Method.GET, BASE_URL,
                            new Response.Listener<JSONArray>() {
                                @Override
                                public void onResponse(JSONArray response) {
                                    // cache the result
                                    mPreferences.edit().putString(DINING_LIST_KEY, response.toString()).apply();
                                    mPreferences.edit().putLong(DINING_LIST_DATE_KEY, System.currentTimeMillis()).apply();
                                    mDiningList = response;
                                    getDiningCalendarEvents();
                                }
                            }, SingletonRequestQueue.getErrorListener(mContext))
                            .setRetryPolicy(SingletonRequestQueue.getRetryPolicy());
            SingletonRequestQueue.getInstance(mContext).addToRequestQueue(jsonArrayRequest);
        }
    }

    /**
     * @param cal - a standard Calendar object
     * @return Returns a human-friendly string of hh:mm am|pm of the given cal
     */
    private static String getTime(Calendar cal) {
        String minutePad = "";
        int minute = cal.get(Calendar.MINUTE); // returns [0,59]
        if (minute < 10) {
            minutePad += "0";
        }
        String ret = cal.get(Calendar.HOUR) + ":" + minutePad + minute + " " +
                cal.getDisplayName(Calendar.AM_PM, Calendar.SHORT, Locale.US).toLowerCase();
        switch (ret) {
            case "0:00 am":
                return "midnight";
            case "0:00 pm":
                return "noon";
            default:
                if (ret.charAt(0) == '0') {
                    ret = "12" + ret.substring(1);
                }
                return ret;
        }
    }

    /**
     * @param rightNow     - Calendar event for current time
     * @param calEventList - List of CalEvents of a location
     * Basically, the idea is to loop through the CalEvents, checking current time compared to it.
     * If we are before it, we stop, saying we are closed until the event starts.
     * If we are in it, we stop, saying we are open until the event ends.
     * If nothing is found (this is looping through the next NUM_DAYS_OF_EVENTS_TO_GET days),
     * then just return "closed".
     */
    private static void setHoursText(TextView hoursTextView, Calendar rightNow,
                                     ArrayList<CalEvent> calEventList) {
        String hoursText = "closed";
        boolean isOpen = false;
        boolean isAlmostOpen = false; // if it is closed now but will be open within next 2 hours
        for (CalEvent e : calEventList) {
            Calendar startCal = e.startCal;
            Calendar endCal = e.endCal;
            if (rightNow.after(startCal) && rightNow.before(endCal)) {
                isOpen = true;
                hoursText = "open until " + getTime(endCal);
                break; // stop loop here
            } else if (rightNow.before(startCal)) {
                String dayText = "";
                int eventDayOfWeek = startCal.get(Calendar.DAY_OF_WEEK);
                int rightNowDayOfWeek = rightNow.get(Calendar.DAY_OF_WEEK);

                int dayDiff = eventDayOfWeek - rightNowDayOfWeek; // difference in day between event and today
                if (dayDiff == 0) {
                    if (startCal.get(Calendar.HOUR_OF_DAY) - rightNow.get(Calendar.HOUR_OF_DAY) <= 2) {
                        isAlmostOpen = true;
                    }
                    hoursText = "opens at " + dayText + getTime(startCal);
                    break; // stop loop here
                } else if (dayDiff == 1) {
                    dayText += "tomorrow, ";
                } else {
                    dayText += startCal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US) + ", ";
                }
                hoursText = "closed until " + dayText + getTime(startCal);
                break; // stop loop here
            }
        }
        hoursTextView.setText(hoursText);
        int textColor;
        Resources res = mContext.getResources();
        if (isOpen) {
            textColor = res.getColor(R.color.openGreen);
        } else if (isAlmostOpen) {
            textColor = res.getColor(R.color.almostOpenGreen);
        } else {
            textColor = res.getColor(R.color.closedColor);
        }
        hoursTextView.setTextColor(textColor);
    }

    /**
     * Classes for the DiningHallListAdapter
     */
    public static class CalEvent implements Comparable<CalEvent> {
        public Calendar startCal;
        public Calendar endCal;

        @Override
        public int compareTo(@NonNull CalEvent cOther) {
            return this.startCal.compareTo(cOther.startCal);
        }
    }

    public static class NameCalEventList implements Comparable<NameCalEventList> {
        public final String name;
        public final ArrayList<CalEvent> calEventList;

        public NameCalEventList(String name, ArrayList<CalEvent> calEventList) {
            this.name = name;
            this.calEventList = calEventList;
        }

        public int compareTo(@NonNull NameCalEventList cOther) {
            Calendar rightNow = Calendar.getInstance();
            int thisStatus = calToRatingInt(rightNow, calEventList);
            int otherStatus = calToRatingInt(rightNow,cOther.calEventList);
            return otherStatus - thisStatus;
        }
    }

        /**
         * http://developer.android.com/guide/topics/ui/declaring-layout.html#FillingTheLayout
         * Returns a custom view for an array of CalEvents on their way through the mListView adapter
         */
        public class DiningHallListAdapter extends ArrayAdapter<NameCalEventList> {

            final int mResource;
            final LayoutInflater mInflater;
            Calendar mRightNowCal;

            public DiningHallListAdapter(Context context, int res, ArrayList<NameCalEventList> items) {
                super(context, res, items);
                this.mResource = res;
                mInflater = LayoutInflater.from(context);
                mRightNowCal = Calendar.getInstance();
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                NameCalEventList nameCalEventList = getItem(position);
                mRightNowCal = Calendar.getInstance();

                // http://developer.android.com/training/improving-layouts/smooth-scrolling.html#ViewHolder
                // http://lucasr.org/2012/04/05/performance-tips-for-androids-listview/
                DiningListViewHolder holder;
                if (convertView == null) {
                    convertView = mInflater.inflate(this.mResource, parent, false);
                    holder = new DiningListViewHolder();
                    holder.nameTextView = (TextView) convertView.findViewById(R.id.dining_list_row_name);
                    holder.hoursTextView = (TextView) convertView.findViewById(R.id.dining_list_row_hours);
                    holder.diningHallName = nameCalEventList.name;
                    convertView.setTag(holder);
                } else {
                    holder = (DiningListViewHolder) convertView.getTag();
                    holder.diningHallName = nameCalEventList.name;
                }

                if (mTextColor == 0) {
                    mTextColor = holder.hoursTextView.getCurrentTextColor();
                }

                setHoursText(holder.hoursTextView, mRightNowCal, nameCalEventList.calEventList);

                // parse the name to make it pretty
                holder.nameTextView.setText(formatDiningHallName(nameCalEventList.name));

                return convertView;
            }

            // http://developer.android.com/training/improving-layouts/smooth-scrolling.html#ViewHolder
            class DiningListViewHolder {
                TextView nameTextView;
                TextView hoursTextView;
                String diningHallName;
            }
        }

        /**
         * Converts the API's badly formatted string to something nicer (atrium_cafe becomes Atrium Cafe)
         *
         * @param name the name to be formatted
         * @return the formatted name
         */
        protected static String formatDiningHallName(String name) {
            final Pattern p = Pattern.compile("\\b([a-z])");
            name = name.replace("_", " ");
            Matcher m = p.matcher(name);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, m.group(1).toUpperCase());
            }
            m.appendTail(sb);
            return sb.toString();
        }

    /**
     * Assigns a rating to a calendar's times to right now where 2 is currently open, 1 is almost open, 0 is closed.
     * @param rightNow the current calendar instance
     * @param calEventList the calendar event list to be processed
     * @return the rating
     */
        private static int calToRatingInt(Calendar rightNow,ArrayList<CalEvent> calEventList){
            int status = 0;
            for (CalEvent e : calEventList) {
                Calendar startCal = e.startCal;
                Calendar endCal = e.endCal;
                if (rightNow.after(startCal) && rightNow.before(endCal)) {
                    status = 2;
                    break; // stop loop here
                } else if (rightNow.before(startCal)) {
                    String dayText = "";
                    int eventDayOfWeek = startCal.get(Calendar.DAY_OF_WEEK);
                    int rightNowDayOfWeek = rightNow.get(Calendar.DAY_OF_WEEK);

                    int dayDiff = eventDayOfWeek - rightNowDayOfWeek; // difference in day between event and today
                    if (dayDiff == 0) {
                        if (startCal.get(Calendar.HOUR_OF_DAY) - rightNow.get(Calendar.HOUR_OF_DAY) <= 2) {
                            status = 1;
                        }
                        break; // stop loop here
                    }
                    break; // stop loop here
                }
            }
            return status;
        }

}
