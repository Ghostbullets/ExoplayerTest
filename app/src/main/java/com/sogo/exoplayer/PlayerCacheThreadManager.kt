package com.sogo.exoplayer

import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.annotation.IntDef
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * 单例模式---缓存任务管理器
 */
class PlayerCacheThreadManager constructor(
        threadCount: Int? = DEFAULT_THREAD_COUNT,//线程数
        private @QueueType var type: Int? = LIFO //队列的调度方式,默认后进先出
) {
    /**
     * 轮询的线程
     */
    private lateinit var poolThread: Thread
    private var poolThreadHandler: ThreadHandler? = null

    /**
     * 运行在UI线程的handler，用于添加任务
     */
    private lateinit var handler: Handler

    /**
     * 线程池
     */
    private var threadPool: ExecutorService = Executors.newFixedThreadPool(threadCount
            ?: DEFAULT_THREAD_COUNT)

    /**
     * 任务队列
     */
    private val tasks = LinkedList<Runnable>()

    /**
     * 引入一个值为1的信号量，防止poolThreadHandler未初始化完成
     */
    private val poolThreadInitSemaphore = Semaphore(0)

    /**
     * 引入一个值为threadCount的信号量，由于线程池内部也有一个阻塞线程，防止加入任务的速度过快，使LIFO效果不明显
     */

    private val poolSemaphore: Semaphore = Semaphore(threadCount ?: DEFAULT_THREAD_COUNT)

    init {
        // loop thread
        poolThread = Thread(Runnable {
            Looper.prepare()
            //初始化
            poolThreadHandler = ThreadHandler(this)
            //释放信号量
            poolThreadInitSemaphore.release()
            Looper.loop()
        })
        poolThread.start()
    }

    /**
     * 执行任务
     */
    fun executeRunnable(runnable: Runnable) {
        addTask(Runnable {
            try {
                //执行任务
                runnable.run()
            } finally {
                //释放一个信号量
                poolSemaphore.release()
            }
        })
    }

    /**
     * 添加一个任务到任务列表中
     */
    @Synchronized
    private fun addTask(runnable: Runnable) {
        try {
            // 请求信号量，防止poolThreadHandler为null
            if (poolThreadHandler == null)
                poolThreadInitSemaphore.acquire()
        } catch (e: InterruptedException) {
        }
        tasks.add(runnable)
        //发送执行任务
        poolThreadHandler?.sendEmptyMessage(0x110)
    }

    /**
     * 取出一个任务
     */
    private fun getTask(): Runnable? {
        return try {
            when (type) {
                FIFO -> tasks.removeFirst()
                LIFO -> tasks.removeLast()
                else -> tasks.removeLast()
            }
        } catch (e: NoSuchElementException) {
            null
        }
    }


    companion object {
        @Volatile
        private var sInstance: PlayerCacheThreadManager? = null

        @JvmStatic
        fun getInstance(): PlayerCacheThreadManager {
            return getInstance(DEFAULT_THREAD_COUNT, LIFO)
        }

        //单例模式---双重校验锁
        @JvmStatic
        fun getInstance(threadCount: Int? = DEFAULT_THREAD_COUNT, @QueueType type: Int? = LIFO): PlayerCacheThreadManager {
            return sInstance ?: synchronized(this) {
                sInstance ?: PlayerCacheThreadManager(threadCount, type).also { sInstance = it }
            }
        }

        const val DEFAULT_THREAD_COUNT = 3 //默认线程数

        const val FIFO = 0 //先进先出
        const val LIFO = 1 //后进先出
    }


    //静态内部类 子线程 handler
    class ThreadHandler(manager: PlayerCacheThreadManager) : WeakHandler<PlayerCacheThreadManager>(manager) {
        override fun handlerMessageEx(msg: Message?, manager: PlayerCacheThreadManager) {
            val task = manager.getTask()
            if (task != null) {
                //执行任务
                manager.threadPool.execute(task)
                //使用一个信号量,如果没有则堵塞
                try {
                    manager.poolSemaphore.acquire()
                } catch (e: Exception) {

                }
            }
        }
    }

    /**
     * 队列的调度方式
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(FIFO, LIFO)
    annotation class QueueType
}