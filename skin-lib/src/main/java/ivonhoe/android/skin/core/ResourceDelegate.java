package ivonhoe.android.skin.core;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import ivonhoe.android.skin.hack.AndroidHack;
import ivonhoe.android.skin.hack.SysHacks;
import ivonhoe.android.skin.log.Logger;
import ivonhoe.android.skin.log.LoggerFactory;
import ivonhoe.android.skin.skin.SkinInfo;
import ivonhoe.android.skin.skin.SkinStorage;

/**
 * @author Ivonhoe on 2017/5/22.
 */

public class ResourceDelegate extends Resources {

    static final Logger log;

    static {
        log = LoggerFactory.getLogcatLogger("DelegateResources");

    }

    public ResourceDelegate(AssetManager assets, Resources resources) {
        super(assets, resources.getDisplayMetrics(), resources.getConfiguration());
    }

    public static void hookDelegateResources(Application application, Resources resources) throws Exception {
        Resources delegateResources = newDelegateResources(application, resources);
        if (delegateResources != null) {
            AndroidHack.injectResources(application, delegateResources);
        }
    }

    private static Resources newDelegateResources(Application context, Resources resources) {
        List<SkinInfo> bundles = SkinStorage.getSkins(context);
        if (bundles != null && !bundles.isEmpty()) {
            List<String> arrayList = new ArrayList();
            arrayList.add(context.getApplicationInfo().sourceDir);
            for (SkinInfo skin : bundles) {
                arrayList.add(skin.getArchive().getAbsolutePath());
            }
            try {
                return newDelegateResources(context, resources, arrayList);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private static Resources newDelegateResources(Application app, Resources resources, List<String> bundleList) throws Exception {
        // log
        printNewDelegateResources(bundleList);
        AssetManager assetManager = newAssetManager(app, bundleList);
        if (assetManager == null) {
            return null;
        }

        return newDelegateResources(resources, assetManager);
    }

    private static AssetManager newAssetManager(Application app, List<String> bundleList) throws Exception {
        if (bundleList != null && !bundleList.isEmpty()) {
            AssetManager assetManager;
            if (Build.VERSION.SDK_INT < 24) {
                assetManager = newAssetManager();
            } else {
                // On Android 7.0+, this should contains a WebView asset as base. #347
                assetManager = app.getAssets();
            }
            for (String str : bundleList) {
                SysHacks.AssetManager_addAssetPath.invoke(assetManager, str);
            }

            return assetManager;
        }

        return null;
    }

    private static Resources newDelegateResources(Resources resources, AssetManager assetManager) {
        try {
            Resources delegateResources = null;
            //处理小米UI资源
            if (resources == null || !resources.getClass().getName().equals("android.content.res.MiuiResources")) {
                delegateResources = new ResourceDelegate(assetManager, resources);
            } else {
                Constructor declaredConstructor = Class.forName("android.content.res.MiuiResources").getDeclaredConstructor(new Class[]{AssetManager.class, DisplayMetrics.class, Configuration.class});
                declaredConstructor.setAccessible(true);
                delegateResources = (Resources) declaredConstructor.newInstance(new Object[]{assetManager, resources.getDisplayMetrics(), resources.getConfiguration()});
            }

            return delegateResources;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static void printNewDelegateResources(List<String> bundleList) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("newDelegateResources [");
        for (int i = 0; i < bundleList.size(); i++) {
            if (i > 0) {
                stringBuffer.append(",");
            }
            stringBuffer.append(bundleList.get(i));
        }
        stringBuffer.append("]");
        log.log(stringBuffer.toString(), Logger.LogLevel.DBUG);
    }

    /**
     * 使用反射的方式，使用Bundle的Resource对象，替换Context的mResources对象
     */
    public static void replaceContextResources(Context context, Resources newResources) {
        try {
            Field field = context.getClass().getDeclaredField("mResources");
            field.setAccessible(true);
            field.set(context, newResources);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static AssetManager newAssetManager() {
        AssetManager assets;
        try {
            assets = AssetManager.class.newInstance();
        } catch (InstantiationException e1) {
            e1.printStackTrace();
            return null;
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
            return null;
        }
        return assets;
    }
}
