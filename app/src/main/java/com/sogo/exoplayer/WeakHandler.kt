package com.sogo.exoplayer

import android.os.Handler
import android.os.Message
import java.lang.ref.WeakReference

/**
 * 弱引用，可传入Activity、Fragment等并调用它的public方法
 *
 * @param <T>
 */
abstract class WeakHandler<T>(referent: T) : Handler() {
    private val ref: WeakReference<T> = WeakReference(referent)
    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        val t = ref.get()
        t?.let { handlerMessageEx(msg, it) }
    }

    abstract fun handlerMessageEx(msg: Message?, t: T)
}