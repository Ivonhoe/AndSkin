package ivonhoe.android.themedemo;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * @author Ivonhoe on 2017/6/23.
 */

public class TestActivity extends Activity {


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test);

        Resources resources = getResources();
        Log.d("simply", "test activity resource:" + resources);
    }

}
