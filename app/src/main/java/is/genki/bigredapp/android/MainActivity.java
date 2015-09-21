package is.genki.bigredapp.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.android.volley.RequestQueue;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        // See the "SingletonRequestQueue" class. Initializes the RequestQueue
        //noinspection UnusedAssignment
        RequestQueue queue = SingletonRequestQueue.getInstance(this).getRequestQueue();
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new DiningListFragment(),"Dining List")
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        //Check the showOnlyOpenHalls if needed
        //http://developer.android.com/reference/android/content/SharedPreferences.html
        SharedPreferences settings = getSharedPreferences("BraPrefs", 0);
        boolean showOnlyOpenHalls = settings.getBoolean("showOnlyOpenHalls", false);
        menu.findItem(R.id.menuShowOpen).setChecked(showOnlyOpenHalls);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_about) {
            this.startActivity(new Intent(this, AboutActivity.class));
        }
        else if (id == R.id.menuShowOpen) {
            //TODO Refresh if we are on DiningListFragment here

            // Update checkbox for showOnlyOpenHalls
            SharedPreferences settings = getSharedPreferences("BraPrefs", 0);
            boolean showOnlyOpenHalls = settings.getBoolean("showOnlyOpenHalls", false);
            SharedPreferences.Editor editor = settings.edit();

            if( showOnlyOpenHalls) {
                item.setChecked(false);
                editor.putBoolean("showOnlyOpenHalls", false);
            }
            else{
                item.setChecked(true);
                editor.putBoolean("showOnlyOpenHalls", true);
            }

            editor.commit();

        }

        return super.onOptionsItemSelected(item);
    }
}
