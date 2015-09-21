package is.genki.bigredapp.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

/**
 * https://developer.android.com/training/volley/requestqueue.html
 * Sets up a Volley RequestQueue to last the lifetime of the app.
 */
public class SingletonRequestQueue {
    private static SingletonRequestQueue mInstance;
    private RequestQueue mRequestQueue;
    private static Context mContext;

    private SingletonRequestQueue(Context context) {
        mContext = context;
        mRequestQueue = getRequestQueue();

    }

    public static synchronized SingletonRequestQueue getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new SingletonRequestQueue(context);
        }
        return mInstance;
    }

    public RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            mRequestQueue = Volley.newRequestQueue(mContext.getApplicationContext());
        }
        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }


    /**
     * https://developer.android.com/training/basics/network-ops/connecting.html#connection
     * @return the device has a connection to the internet
     * Makes a Toast if there is no connection
     */
    public static boolean isConnected(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        } else {
            Toast.makeText(context, "No Internet Connection", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /**
     * @return the default RetryPolicy for the app
     * first value = Timeout = Socket Timeout in milliseconds per retry attempt
     * second value = number of retries to attempt
     * third value = multiplier to determine exponential time set to socket for each retry
     */
    public static RetryPolicy getRetryPolicy() {
        return new DefaultRetryPolicy(6000, // 6 seconds
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES, // 1
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT); // 1f
    }

    /**
     * @return the default ErrorListener for requests in the app
     */
    public static Response.ErrorListener getErrorListener(final Context context) {
        return new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, "Internet timed out", Toast.LENGTH_LONG).show();
            }
        };
    }
}
