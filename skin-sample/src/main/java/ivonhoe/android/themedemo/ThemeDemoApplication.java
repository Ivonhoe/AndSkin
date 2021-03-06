package ivonhoe.android.themedemo;

import android.app.Application;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;

import ivonhoe.android.skin.core.Skin;

/**
 * @author Ivonhoe on 2017/5/23.
 */

public class ThemeDemoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Skin.init(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Resources resources = getResources();
        Log.d("simply", "---app resources:" + resources);
    }
}
