package ivonhoe.android.skin.skin;

import org.w3c.dom.Text;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Ivonhoe on 2017/5/23.
 */

public class SkinStorage {

    private static final String DEFAULT_SKIN_NAME = "ivonhoe.android.themedemo";

    private static final String KEY_SKIN_PATH = "key_skin_path";
    private static final String KEY_SKIN_PACKAGE = "key_skin_name";


    public static List<SkinInfo> getSkins(Context context) {
        List<SkinInfo> skins = new ArrayList<>();

        File skinFile = getLocalSkinFile(context);
        SkinInfo skin = new SkinInfo();
        skin.setName("ivonhoe.android.skin");
        skin.setArchive(skinFile);
        skins.add(skin);

        return skins;
    }

    /**
     * @return 当前最高优先级的皮肤包路径
     */
    public static File getLocalSkinFile(Context context) {
        String skinName = getSkinName(context);
        if (TextUtils.isEmpty(skinName)) {
            skinName = DEFAULT_SKIN_NAME;
        }

        String skinFileName = getSkinFileName(skinName);
        String extSkinDir = getExternalSkinDir(context);
        String internalSkinDir = getInternalSkinDir(context);

        String skinPath = extSkinDir + File.separator + skinFileName;
        File skinFile = new File(skinPath);
        if (skinFile.exists()) {
            return skinFile;
        }

        skinPath = internalSkinDir + File.separator + skinFileName;
        skinFile = new File(skinPath);
        if (!skinFile.exists()) {
            throw new NullPointerException("不存在路径:" + skinFile.getAbsolutePath());
        }

        return skinFile;
    }

    /**
     * @return SDCARD上的存储路径
     */
    public static String getExternalSkinDir(Context context) {
        String dir = context.getExternalFilesDir("") + File.separator + "skin";

        File file = new File(dir);
        if (!file.exists()) {
            file.mkdirs();
        }

        return dir;
    }

    /**
     * @return 默认主题包的绝对路径
     */
    private static String getInternalSkinDir(Context context) {
        return "/data/data/" + context.getPackageName() + "/lib";
    }

    private static String getSkinFileName(String skinName) {
        return "lib" + skinName.replace('.', '_') + ".so";
    }

    public static String getSkinName(Context context) {
        SharedPreferences spDefault = PreferenceManager.getDefaultSharedPreferences(context);
        return spDefault.getString(KEY_SKIN_PACKAGE, "");
    }

    public static void saveSkinName(Context context, String skinPackageName) {
        SharedPreferences spDefault = PreferenceManager.getDefaultSharedPreferences(context);
        spDefault.edit().putString(KEY_SKIN_PACKAGE, skinPackageName).commit();
    }

}
