package ivonhoe.android.skin.core;

import android.app.Application;
import android.util.Log;

import ivonhoe.android.skin.hack.SysHacks;

/**
 * @author Ivonhoe on 2017/5/23.
 */

public class Skin {

    public static final String SYMBOL_SEMICOLON = ";";

    private static Skin instance;

    public static synchronized Skin instance() {
        if (instance == null) {
            instance = new Skin();
        }

        return instance;
    }

    private Skin() {
    }

    public static void init(Application application) throws Exception {
        SysHacks.defineAndVerify();

        ResourceDelegate.hookDelegateResources(application, application.getResources());
    }

}
