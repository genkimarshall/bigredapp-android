package is.genki.bigredapp.android;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Activity displaying info for the passed-in location.
 * Currently, gets and displays the menu for all-you-can-eat dining halls.
 */
public class DiningLocationActivity extends ActionBarActivity {

    public static final String KEY_DINING_HALL = "DiningLocationActivity.DINING_HALL";


    private static final String[] MEALS_LIST = {"Breakfast", "Brunch", "Lunch", "Dinner"};
    private static final String KEY_MEALS = "DiningLocationActivity.MEALS";
    private static final String KEY_MENUS = "DiningLocationActivity.MENUS";
    private static final String KEY_FRAGMENT = "DiningLocationActivity.FRAGMENT";
    private String[] mealsRequested;

    private String mDiningHall;
    private LocationInfoFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dining_location);

        Toolbar toolbar = (Toolbar) findViewById(R.id.dining_location_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mFragment = new LocationInfoFragment();
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, mFragment)
                    .commit();
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                mDiningHall = extras.getString(KEY_DINING_HALL);
                getLocationData();
            }
        }
        else {
            mDiningHall = savedInstanceState.getString(KEY_DINING_HALL);
            // Restore the fragment's instance
            mFragment = (LocationInfoFragment)
                    getSupportFragmentManager().getFragment(savedInstanceState, KEY_FRAGMENT);
        }
        //Shamelessly stolen from DiningListFragment to beautify name
        setTitle(DiningListFragment.formatDiningHallName(mDiningHall));
    }

    /**
     * See the "SingletonRequestQueue" Class
     */
    private void getLocationData() {
        if (SingletonRequestQueue.isConnected(this)) {
            final String diningHallMenuUrl = getMealRequestString();
            JsonObjectRequest jsonObjectRequest = (JsonObjectRequest)
                    new JsonObjectRequest(Request.Method.GET, diningHallMenuUrl,
                    new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        List<MealMenu> menus = new ArrayList<>();
                        for (String meal : mealsRequested ) {
                            StringBuilder menu = new StringBuilder();
                            JSONObject mealObject = response.getJSONObject(meal);
                            // some menus won't have all the meals (e.g. Brunch), but are still valid
                            if (!mealObject.isNull(mDiningHall)) {
                                JSONArray jsonArray = mealObject.getJSONArray(mDiningHall);
                                int len = jsonArray.length();
                                for (int i = 0; i < len; i++) {
                                    if (i != 0) menu.append("\n");
                                    menu.append(jsonArray.getJSONObject(i).getString("name"));
                                }
                                menus.add(new MealMenu(meal, menu.toString()));
                            }
                        }
                        if (menus.size() == 0) throw new JSONException("No menus");
                        mFragment.addMenus(menus);
                    } catch (JSONException e) {
                       mFragment.noMenus();
                    }
                }
            }, SingletonRequestQueue.getErrorListener(this))
            .setRetryPolicy(SingletonRequestQueue.getRetryPolicy());
            SingletonRequestQueue.getInstance(this).addToRequestQueue(jsonObjectRequest);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(KEY_DINING_HALL, mDiningHall);

        // Save the fragment's instance
        getSupportFragmentManager().putFragment(outState, KEY_FRAGMENT, mFragment);

        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_about) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A fragment containing a RecyclerView with cards for the different meals
     * https://developer.android.com/training/material/lists-cards.html
     */
    public static class LocationInfoFragment extends Fragment {
        private static final String KEY_HAS_MENUS = "LocationInfoFragment.HAS_MENUS";

        private RecyclerView mRecyclerView;
        private LinearLayoutManager mLayoutManager;
        private MealMenuAdapter mAdapter;
        private View mLoadingPanel;
        private View mNoMenusPanel;
        private List<MealMenu> mMenus;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_dining_location, container, false);

            mRecyclerView = (RecyclerView) rootView.findViewById(R.id.menuList);
            mLayoutManager = new LinearLayoutManager(getActivity());
            mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            mRecyclerView.setLayoutManager(mLayoutManager);

            mLoadingPanel = rootView.findViewById(R.id.menuLoadingPanel);
            mNoMenusPanel = rootView.findViewById(R.id.menuNonePanel);

            return rootView;
        }

        public void addMenus(List<MealMenu> menus) {
            mMenus = menus;
            mLoadingPanel.setVisibility(View.GONE);
            mAdapter = new MealMenuAdapter(menus);
            mRecyclerView.setAdapter(mAdapter);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            if (mMenus == null || mMenus.size() == 0) {
                outState.putBoolean(KEY_HAS_MENUS, false);
            } else {
                outState.putBoolean(KEY_HAS_MENUS, true);
                ArrayList<String> meals = new ArrayList<>();
                ArrayList<String> menus = new ArrayList<>();

                for (MealMenu m : mMenus) {
                    meals.add(m.meal);
                    menus.add(m.menu);
                }

                outState.putStringArrayList(KEY_MEALS, meals);
                outState.putStringArrayList(KEY_MENUS, menus);
            }
            super.onSaveInstanceState(outState);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null) {
                if (savedInstanceState.getBoolean(KEY_HAS_MENUS)) {
                    mMenus = new ArrayList<>();

                    ArrayList<String> meals = savedInstanceState.getStringArrayList(KEY_MEALS);
                    ArrayList<String> menus = savedInstanceState.getStringArrayList(KEY_MENUS);
                    for (int i = 0; i < meals.size(); i++) {
                        mMenus.add(new MealMenu(meals.get(i), menus.get(i)));
                    }

                    addMenus(mMenus);
                } else {
                    noMenus();
                }
            }
        }

        public void noMenus() {
            mLoadingPanel.setVisibility(View.GONE);
            mNoMenusPanel.setVisibility(View.VISIBLE);
        }
    }

    public static class MealMenu {
        public final String meal;
        public final String menu;

        public MealMenu(String meal, String menu) {
            this.meal = meal;
            this.menu = menu;
        }
    }

    public static class MealMenuViewHolder extends RecyclerView.ViewHolder {
        final TextView mMeal;
        final TextView mMenu;

        public MealMenuViewHolder(View v) {
            super(v);

            mMeal = (TextView) v.findViewById(R.id.menu_meal);
            mMenu = (TextView) v.findViewById(R.id.menu_body);
        }
    }

    /**
     * Adapter for the cards in the LocationInfoFragment
     * https://developer.android.com/training/material/lists-cards.html
     */
    public static class MealMenuAdapter extends RecyclerView.Adapter<MealMenuViewHolder> {
        private final List<MealMenu> menus;

        public MealMenuAdapter(List<MealMenu> menus) {
            this.menus = menus;
        }

        @Override
        public MealMenuViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.
                from(parent.getContext()).
                inflate(R.layout.menu_card, parent, false);

            return new MealMenuViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(MealMenuViewHolder holder, int position) {
            MealMenu menu = menus.get(position);
            holder.mMeal.setText(menu.meal);
            holder.mMenu.setText(menu.menu);
        }

        @Override
        public int getItemCount() {
            return menus.size();
        }
    }

    /**
     * Gives meal request string based on time of day
     * Based on breakfast until 11, brunch until 2, lunch until 3
     */
    private String getMealRequestString(){
       Calendar time = Calendar.getInstance();
        int now = time.get(Calendar.HOUR_OF_DAY);
        String meals = MEALS_LIST[3];
        if( now < 15 ){
            meals = MEALS_LIST[2] + "," + meals;
            if( now < 14 ) {
                meals = MEALS_LIST[1] + "," + meals;
                if( now < 11 ) {
                    meals = MEALS_LIST[0] + "," + meals;
                }
            }
        }
        mealsRequested = meals.split(",");

        return DiningListFragment.BASE_URL + "/menu/" + mDiningHall + "/" +
                meals + "/MEALS";
    }
}
