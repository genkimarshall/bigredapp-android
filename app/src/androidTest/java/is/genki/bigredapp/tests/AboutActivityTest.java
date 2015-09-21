package is.genki.bigredapp.tests;

import android.test.ActivityInstrumentationTestCase2;
import android.widget.TextView;

import is.genki.bigredapp.android.AboutActivity;
import is.genki.bigredapp.android.R;

/**
 * A sample of the testing we could be doing on an activity that doesn't need testing.
 * https://developer.android.com/training/activity-testing/activity-basic-testing.html
 */
public class AboutActivityTest extends ActivityInstrumentationTestCase2<AboutActivity> {

    private AboutActivity myAboutActivity;
    private TextView aboutText;

    public AboutActivityTest(){
        super(AboutActivity.class);
    }

    public void testPreconditions() {
        assertNotNull("myAboutActivity is null", myAboutActivity);
        assertNotNull("aboutText is null", aboutText);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myAboutActivity = getActivity();
        aboutText =
                (TextView) myAboutActivity
                        .findViewById(R.id.textView2);
    }

    /**
     * Test that the about text is what we set it to be....
     */
    public void testAboutText() {
        final String expected =
                myAboutActivity.getString(R.string.about_description);
        final String actual = aboutText.getText().toString();
        assertEquals(expected, actual);
    }
}
