package com.sogo.exoplayer;


import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import com.spx.exoplayertest.R;

import java.io.File;
import java.io.IOException;


/**
 * Manages the {@link ExoPlayer}, the IMA plugin and all video playback.
 * 单例模式
 */
public final class PlayerWrapper {

    private static final String TAG = "Player.Wrapper";
    private static PlayerWrapper instance;

    public static PlayerWrapper getInstance() {
        if (instance == null) {
            synchronized (PlayerWrapper.class) {
                if (instance == null) {
                    instance = new PlayerWrapper();
                }
            }
        }
        return instance;
    }

    private SimpleExoPlayer player;
    private SimpleExoPlayerView simpleExoPlayerView;
    private boolean playFinished = false;
    private boolean isPlaying = false;

    private TrackSelector mTrackSelector;
    private String mVideoUrl;
    private Player.EventListener mEventListener;
    private VideoListener mVideoListener;


    private DataSource.Factory dataSourceFactory = null;
    private ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

    private File cacheFile = null;

    SimpleCache simpleCache = null;
    DataSource.Factory cachedDataSourceFactory = null;
    // This is the MediaSource representing the content media (i.e. not the ad).
//        String contentUrl = context.getString(R.string.content_url);
//        MediaSource contentMediaSource = new ExtractorMediaSource(
//                Uri.parse(contentUrl), dataSourceFactory, extractorsFactory, null, null);

    // Compose the content media source into a new AdsMediaSource with both ads and content.
//        MediaSource mediaSourceWithAds = new AdsMediaSource(contentMediaSource, dataSourceFactory,
//                adsLoader, simpleExoPlayerView.getOverlayFrameLayout());


    // This is the MediaSource representing the media to be played.
    MediaSource videoSource = null;

    private PlayerWrapper() {
        /**
         * 预加载数据准备机制
         */
        //创建一个DataSource对象，通过它来下载多媒体数据
        dataSourceFactory = new DefaultDataSourceFactory(VUtil.getApplicationContext(),
                Util.getUserAgent(VUtil.getApplicationContext(), VUtil.getApplicationContext().getString(R.string.app_name)));
        //创建本地缓存目录，凡是下载的数据会在设定的缓存目录中保存下来
        cacheFile = new File(VUtil.getApplicationContext().getExternalCacheDir().getAbsolutePath(), "video");
        VLog.d(TAG, "PlayerWrapper()  cache file:" + cacheFile.getAbsolutePath());
        // 本地最多保存512M, 按照LRU原则删除老数据
        //todo 判断当前剩余空间大小
        simpleCache = new SimpleCache(cacheFile, new LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024));
        //创建一个缓存数据来源 工厂
        cachedDataSourceFactory = new CacheDataSourceFactory(simpleCache, dataSourceFactory);
        //使用默认参数创建自适应跟踪选择工厂
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory();
        mTrackSelector = new DefaultTrackSelector(VUtil.getApplicationContext(),videoTrackSelectionFactory);
    }

    /**
     * 当调用该方法时，立刻就会尝试去预加载，缓存videoUri地址的视频的0-512K(可以调整，最大不超过5M)
     * {@link com.google.android.exoplayer2.upstream.cache.CacheDataSink#DEFAULT_FRAGMENT_SIZE},
     * {@link com.google.android.exoplayer2.upstream.cache.CacheDataSink#open(DataSpec)} 处设置了dataSpecFragmentSize(用于预加载size限制),
     * 的视频数据，如果本地没有缓存的话。
     *
     * {@link SimpleCache#startFile(String, long, long)} 位置设置了预加载缓存保存的本地地址
     * @param videoUri
     */
    public void preload(String videoUri) {
        //数据规范 1:url转uri ;2从流的哪个位置开始缓存，这里为0即流的起点处；3:缓存多少，这里是512K;4:自定义缓存健，不传使用默认值
        DataSpec dataSpec = new DataSpec(Uri.parse(videoUri), 0, 512 * 1024/*CacheDataSink.DEFAULT_FRAGMENT_SIZE*/, null);
        try {
            CacheUtil.ProgressListener progressListener = new CacheUtil.ProgressListener() {
                @Override
                public void onProgress(long requestLength, long bytesCached, long newBytesCached) {
                    //缓存的内容长度(以字节为单位);已缓存的字节数；自上次进度更新以来新缓存的字节数
                    VLog.d(TAG, String.format("onProgress 缓存的内容长度(以字节为单位)=%s,已缓存的字节数=%s,新缓存的字节数=%s",
                            requestLength, bytesCached, newBytesCached));
                }
            };
            //第一个参数:定义要缓存的数据;第二个参数:定义本地缓存存储数据;第三个参数:缓存key工厂，定义自己的规则;
            //第四个参数:用于读取不在缓存中的数据的数据源;第五个参数:缓存进度更新的监听器;第六个参数:一个可选的标志,如果设置为真,将中断缓存;
            CacheUtil.cache(dataSpec, simpleCache, CacheUtil.DEFAULT_CACHE_KEY_FACTORY, dataSourceFactory.createDataSource(), progressListener, null);
            //2.8.3
//            CacheUtil.CachingCounters counters = new CacheUtil.CachingCounters();
//            CacheUtil.cache(dataSpec, simpleCache, dataSourceFactory.createDataSource(), counters, null);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void init(Context context, SimpleExoPlayerView simpleExoPlayerView, String videoUri,
                     Player.EventListener eventListener, SimpleExoPlayer.VideoListener videoListener,
                     boolean userAction) {
        this.mVideoUrl = videoUri;
        this.mEventListener = eventListener;
        this.mVideoListener = videoListener;
        initPlayer(context, simpleExoPlayerView, userAction);
    }

    private void initPlayer(Context context, SimpleExoPlayerView simpleExoPlayerView, boolean userAction) {
        // Create a default track selector.
        VLog.d(TAG, "initPlayer: ... " + userAction);
        this.simpleExoPlayerView = simpleExoPlayerView;
        //2.11.3

        MyDefaultLoadControl loadControl = new MyDefaultLoadControl.Builder()
                //播放器触发加载数据的时机，缓冲区数据duration time 小于该值时,毫秒为单位；播放器进行加载数据的上限，即当前缓冲区AV数据 duration time 小于该值,毫秒为单位;
                //也就是说,当缓冲区  duration time<minBufferMs时，开始请求缓冲
                //        当缓冲区  duration time>=maxBufferMs时，停止请求缓冲

                //定义了播放器播放数据的条件，即缓冲区 duration time 不小于bufferForPlaybackMs；
                //播放器从 REBUFFER状态(REBUFFER是由于运行时缓冲区耗尽触发导致) 恢复为可播放状态后可播放AV数据的条件；即缓冲区 duration time 不小于bufferForPlaybackAfterRebufferMs

                //在这里，当已缓冲区域(缓冲位置-当前播放位置)<360秒，开始请求数据到缓冲区；当已缓冲区域(缓冲位置-当前播放位置)>=600秒，停止请求；
                //当已缓冲区域(缓冲位置-当前播放位置)>1秒，满足播放条件；seekTo到某个未缓冲位置时，当已缓冲区域(缓冲位置-当前seekTo位置)>=5秒时，满足播放条件
                .setBufferDurationsMs(360000, 600000, 1000, 5000)
                .createDefaultLoadControl();
        loadControl.setWithoutWifiContinueLoading(userAction);
        player = new SimpleExoPlayer.Builder(context, new DefaultRenderersFactory(context), mTrackSelector, loadControl,
                DefaultBandwidthMeter.getSingletonInstance(context), Util.getLooper(),
                new AnalyticsCollector(Clock.DEFAULT), true, Clock.DEFAULT)
                .setBandwidthMeter(new DefaultBandwidthMeter.Builder(VUtil.getApplicationContext()).build())//提供当前可用带宽的估计
                .build();
        //2.8.3
//        player = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(context),
//                mTrackSelector, new LdDefaultLoadControl(userAction));


        // Bind the player to the view.
        simpleExoPlayerView.setPlayer(player);

        // Produces DataSource instances through which media data is loaded.

//        DataSource.Factory dataSourceFactory = new OkHttpDataSourceFactory();
        // Produces Extractor instances for parsing the content media (i.e. not the ad).


        // Prepare the player with the source.
        isPlaying = false;
//        player.seekTo(contentPosition);
        //这是一个代表将要被播放的媒体的MediaSource
        //过时，实际上还是调用ProgressiveMediaSource
//        videoSource = new ExtractorMediaSource(Uri.parse(mVideoUrl),
//                cachedDataSourceFactory, extractorsFactory, null, null);
        videoSource = new ProgressiveMediaSource.Factory(cachedDataSourceFactory,extractorsFactory)
                .createMediaSource(Uri.parse(mVideoUrl));
        //使用资源准备播放器
        player.prepare(videoSource);

//        player.setPlayWhenReady(true);

        player.addListener(mEventListener);
        player.addVideoListener(mVideoListener);
        hasReleased = false;
    }

    public void transformIn(SimpleExoPlayerView newSimpleExoPlayerView){
        newSimpleExoPlayerView.setPlayer(player);
        simpleExoPlayerView.setPlayer(null);
    }
    public void transformOut(SimpleExoPlayerView newSimpleExoPlayerView){
        simpleExoPlayerView.setPlayer(player);
        newSimpleExoPlayerView.setPlayer(null);
    }

    public void onPlayFinished() {
        VLog.d(TAG, "onPlayFinished: ...");
        playFinished = true;
        isPlaying = false;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void play() {
        VLog.d(TAG, "play: ...");
        if (playFinished) {
            player.seekTo(0);
            playFinished = false;
        }
        player.setPlayWhenReady(true);

        if (player.getPlaybackState() == Player.STATE_IDLE) {
            VLog.d(TAG, "player is IDLE!!!");
        }

        isPlaying = true;
        hasReleased = false;
    }

    public boolean isIdle() {
        if (player != null) {
            if (player.getPlaybackState() == Player.STATE_IDLE) {
                return true;
            }
        }
        return false;
    }

    public void pause() {
        VLog.d(TAG, "pause: ...");
        if (player != null) {
            isPlaying = false;
            player.stop();
        }
    }

    private boolean hasReleased = false;

    public void release() {
        VLog.d(TAG, "release: ..."+this);
        if (player != null) {
            isPlaying = false;
            player.removeListener(mEventListener);
            player.removeVideoListener(mVideoListener);
            player.release();
            hasReleased = true;
            VLog.d(TAG, "release: ...hasReleased!");
//            player = null;
        }
//        adsLoader.release();
    }

    public String getPlayUrl() {
        return mVideoUrl;
    }

    public boolean isReleased() {
        return hasReleased;
    }
}
