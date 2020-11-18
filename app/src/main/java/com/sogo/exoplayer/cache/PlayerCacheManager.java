package com.sogo.exoplayer.cache;


import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.sogo.exoplayer.PlayerCacheThreadManager;
import com.sogo.exoplayer.VUtil;
import com.spx.exoplayertest.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Manages the {@link ExoPlayer}, the IMA plugin and all video playback.
 * 单例模式---播放预加载、缓存管理器
 */
public final class PlayerCacheManager {

    private static final String TAG = PlayerCacheManager.class.getSimpleName();
    private static PlayerCacheManager instance;

    public static PlayerCacheManager getInstance() {
        if (instance == null) {
            synchronized (PlayerCacheManager.class) {
                if (instance == null) {
                    instance = new PlayerCacheManager();
                }
            }
        }
        return instance;
    }

    private DataSource.Factory dataSourceFactory = null;

    private File cacheFile = null;

    private SimpleCache simpleCache = null;
    private DataSource.Factory cachedDataSourceFactory = null;

    /**
     * video_url 列表
     */
    private ArrayList<String> urlList;

    private PlayerCacheManager() {
        urlList = new ArrayList<>();

        /**
         * 预加载数据准备机制
         */
        //创建一个DataSource对象，通过它来下载多媒体数据
        dataSourceFactory = new DefaultDataSourceFactory(VUtil.getApplicationContext(),
                Util.getUserAgent(VUtil.getApplicationContext(), VUtil.getApplicationContext().getString(R.string.app_name)));
        //创建本地缓存目录，凡是下载的数据会在设定的缓存目录中保存下来
        cacheFile = new File(VUtil.cacheDir.getAbsolutePath(), VUtil.videoCacheDirName);

        //todo 3.6.6 删除
        Log.d(TAG, "PlayerWrapper()  cache file:" + cacheFile.getAbsolutePath());
        // 本地最多保存512M, 按照LRU原则删除老数据
        simpleCache = new SimpleCache(cacheFile, new LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024));
        //创建一个缓存数据来源 工厂
        cachedDataSourceFactory = new CacheDataSourceFactory(simpleCache, dataSourceFactory);
    }

    /**
     * 当调用该方法时，立刻就会尝试去预加载，缓存videoUri地址的视频的0-512K(可以调整，最大不超过5M)
     * {@link CacheDataSink#DEFAULT_FRAGMENT_SIZE},
     * {@link CacheDataSink#open(DataSpec)} 处设置了dataSpecFragmentSize(用于预加载size限制),
     * 的视频数据，如果本地没有缓存的话。
     * <p>
     * {@link SimpleCache#startFile(String, long, long)} 位置设置了预加载缓存保存的本地地址
     *
     * @param videoUrl
     */
    public void preload(String videoUrl, @Nullable AtomicBoolean isCanceled) {
        if (videoUrl == null || urlList.contains(videoUrl)) {
            //已经在缓存了
            return;
        }
        urlList.add(videoUrl);
        PlayerCacheThreadManager.getInstance().executeRunnable(new Runnable() {
            @Override
            public void run() {
                preloading(videoUrl, isCanceled);
            }
        });
    }

    @WorkerThread
    private void preloading(String videoUrl, @Nullable AtomicBoolean isCanceled) {
        String path = "";
        try {
            path = videoUrl.substring(videoUrl.lastIndexOf("/"));
        } catch (Exception e) {

        }
        final String suffixName = path;
        //数据规范 1:url转uri ;2从流的哪个位置开始缓存，这里为0即流的起点处；3:缓存多少，这里是2MB;4:自定义缓存健，不传使用默认值
        DataSpec dataSpec = new DataSpec(Uri.parse(videoUrl), 0, 2 * 1024 * 1024/*CacheDataSink.DEFAULT_FRAGMENT_SIZE*/, null);
        try {
            CacheUtil.ProgressListener progressListener = new CacheUtil.ProgressListener() {
                @Override
                public void onProgress(long requestLength, long bytesCached, long newBytesCached) {
                    //缓存的内容长度(以字节为单位);已缓存的字节数；自上次进度更新以来新缓存的字节数
                    //todo 3.6.6 删除
                    Log.d(TAG, String.format("name=%s onProgress 缓存的内容长度(以字节为单位)=%s,已缓存的字节数=%s,新缓存的字节数=%s",
                            suffixName, requestLength, bytesCached, newBytesCached));
                }
            };
            //第一个参数:定义要缓存的数据;第二个参数:定义本地缓存存储数据;第三个参数:缓存key工厂，定义自己的规则;
            //第四个参数:用于读取不在缓存中的数据的数据源;第五个参数:缓存进度更新的监听器;第六个参数:一个可选的标志,如果设置为真,将中断缓存;
            CacheUtil.cache(dataSpec, simpleCache, CacheUtil.DEFAULT_CACHE_KEY_FACTORY, dataSourceFactory.createDataSource(), progressListener, isCanceled);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public DataSource.Factory getCachedDataSourceFactory() {
        return cachedDataSourceFactory;
    }

    //中断缓存后调用，清空列表
    public void interruptCache() {
        urlList.clear();
    }
}
