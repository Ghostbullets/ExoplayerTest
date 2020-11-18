package com.sogo.exoplayer;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;

import com.spx.exoplayertest.MyApplication;

import java.io.File;
import java.lang.reflect.Field;


/**
 * Created by shaopengxiang on 2017/12/20.
 */

public class VUtil {

    /**
     * 判断wifi是否连接
     *
     * @param context
     * @return true:wifi连接状态
     */
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi.isConnected();
    }

    public static Context getApplicationContext(){
        return MyApplication.getApp();
    }

    //数据保存位置---对应设置里面的清除数据
    public static final File cacheDir = !isExternalStorageWritable() ? getApplicationContext().getCacheDir() : getApplicationContext().getExternalCacheDir();
    //短视频模块----视频缓存目录
    public static final String videoCacheDirName = "short_video";

    /**
     * 判断外部存储是否可用
     */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) || !Environment.isExternalStorageRemovable();
    }

    // 获得通知栏高度
    public static int getStatusBarHeight(Context context) {
        Class<?> c = null;
        Object obj = null;
        Field field = null;
        int x = 0, statusBarHeight = 0;
        try {
            c = Class.forName("com.android.internal.R$dimen");
            obj = c.newInstance();
            field = c.getField("status_bar_height");
            x = Integer.parseInt(field.get(obj).toString());
            statusBarHeight = context.getResources().getDimensionPixelSize(x);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return statusBarHeight;
    }
}
