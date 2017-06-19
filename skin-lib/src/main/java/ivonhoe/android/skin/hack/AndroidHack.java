package ivonhoe.android.skin.hack;

import android.app.Application;
import android.app.Instrumentation;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by yb.wang on 15/1/5.
 * Android中的Resource Hack
 */
public class AndroidHack {
    private static Object _mLoadedApk;
    private static Object _sActivityThread;

    static Field sActiveResourcesField = null;
    static Class sResourcesManagerClazz = null;
    static Method sgetInstanceMethod = null;
    static Field sAssetsField = null;

    static {
        try {
            if (Build.VERSION.SDK_INT <= 18) {
                Class ActivityThreadClazz = Class.forName("android.app.ActivityThread");
                sActiveResourcesField = ActivityThreadClazz.getDeclaredField("mActiveResources");
                sActiveResourcesField.setAccessible(true);
                sAssetsField = Resources.class.getDeclaredField("mAssets");
                sAssetsField.setAccessible(true);
            } else if (Build.VERSION.SDK_INT < 24) {
                sResourcesManagerClazz = Class.forName("android.app.ResourcesManager");
                sActiveResourcesField = sResourcesManagerClazz.getDeclaredField("mActiveResources");
                sActiveResourcesField.setAccessible(true);
                sgetInstanceMethod = sResourcesManagerClazz.getDeclaredMethod("getInstance");
                sgetInstanceMethod.setAccessible(true);
                sAssetsField = Resources.class.getDeclaredField("mAssets");
                sAssetsField.setAccessible(true);
            } else {
                sResourcesManagerClazz = Class.forName("android.app.ResourcesManager");
                sActiveResourcesField = sResourcesManagerClazz.getDeclaredField("mActivityResourceReferences");
                sActiveResourcesField.setAccessible(true);
                sgetInstanceMethod = sResourcesManagerClazz.getDeclaredMethod("getInstance");
                sgetInstanceMethod.setAccessible(true);
            }
        } catch (Throwable e) {
        }
    }

    static class ActivityThreadGetter implements Runnable {
        ActivityThreadGetter() {
        }

        public void run() {
            try {
                _sActivityThread = SysHacks.ActivityThread_currentActivityThread.invoke(SysHacks.ActivityThread.getmClass(), new Object[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            synchronized (SysHacks.ActivityThread_currentActivityThread) {
                SysHacks.ActivityThread_currentActivityThread.notify();
            }
        }
    }

    static {
        _sActivityThread = null;
        _mLoadedApk = null;
    }

    public static Object getActivityThread() throws Exception {
        if (_sActivityThread == null) {
            if (Thread.currentThread().getId() == Looper.getMainLooper().getThread().getId()) {
                _sActivityThread = SysHacks.ActivityThread_currentActivityThread.invoke(null, new Object[0]);
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                synchronized (SysHacks.ActivityThread_currentActivityThread) {
                    handler.post(new ActivityThreadGetter());
                    SysHacks.ActivityThread_currentActivityThread.wait();
                }
            }
        }
        return _sActivityThread;
    }

    public static Object getLoadedApk(Object obj, String str) throws Exception {
        if (_mLoadedApk == null) {
            WeakReference weakReference = (WeakReference) ((Map) SysHacks.ActivityThread_mPackages.get(obj)).get(str);
            if (weakReference != null) {
                _mLoadedApk = weakReference.get();
            }
        }
        return _mLoadedApk;
    }


    public static void injectResources(Application application, Resources resources) throws Exception {
        Object activityThread = getActivityThread();
        if (activityThread == null) {
            throw new Exception("Failed to get ActivityThread.sCurrentActivityThread");
        }
        Object loadedApk = getLoadedApk(activityThread, application.getPackageName());
        if (loadedApk == null) {
            throw new Exception("Failed to get ActivityThread.mLoadedApk");
        }
        SysHacks.LoadedApk_mResources.set(loadedApk, resources);
        SysHacks.ContextImpl_mResources.set(application.getBaseContext(), resources);
        SysHacks.ContextImpl_mTheme.set(application.getBaseContext(), null);

        try {
            Collection<WeakReference<Resources>> references = null;
            if (Build.VERSION.SDK_INT <= 18) {
                HashMap<?, WeakReference<Resources>> map = (HashMap<?, WeakReference<Resources>>) sActiveResourcesField.get(activityThread);
                references = map.values();
            } else if (Build.VERSION.SDK_INT < 24) {
                Object sResourcesManager = sgetInstanceMethod.invoke(sResourcesManagerClazz);
                ArrayMap<?, WeakReference<Resources>> activeResources = (ArrayMap<?, WeakReference<Resources>>) sActiveResourcesField.get(sResourcesManager);
                references = activeResources.values();
            }
            if (Build.VERSION.SDK_INT < 24) {
                for (WeakReference<Resources> wr : references) {
                    Resources res = wr.get();
                    if (res != null) {
                        sAssetsField.set(res, resources.getAssets());
                        res.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
                    }
                }
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static Instrumentation getInstrumentation() throws Exception {
        Object activityThread = getActivityThread();
        if (activityThread != null) {
            return SysHacks.ActivityThread_mInstrumentation.get(activityThread);
        }
        throw new Exception("Failed to get ActivityThread.sCurrentActivityThread");
    }

    public static void injectInstrumentationHook(Instrumentation instrumentation) throws Exception {
        Object activityThread = getActivityThread();
        if (activityThread == null) {
            throw new Exception("Failed to get ActivityThread.sCurrentActivityThread");
        }
        SysHacks.ActivityThread_mInstrumentation.set(activityThread, instrumentation);
    }

}
