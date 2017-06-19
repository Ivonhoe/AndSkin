package ivonhoe.android.themedemo;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;


public class MainActivity extends Activity {

    ImageView mImageView;
    TextView mHelloWorld;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mHelloWorld = (TextView) findViewById(R.id.plugin_hello_world);
        mImageView = (ImageView) findViewById(R.id.plugin_drawable_test);
    }

    public void test() {
        try {
            int stringId = getResources().getIdentifier("skin_icon_my_address", "drawable", "ivonhoe.android.themedemo");
            Drawable drawable = getResources().getDrawable(stringId);
            Log.d("simply", "drawable:" + drawable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
