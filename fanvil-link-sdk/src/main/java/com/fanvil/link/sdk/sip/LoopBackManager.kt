package com.fanvil.link.sdk.sip

import android.util.Log

object LoopBackManager {
  private const val TAG = "FvLoopBack"
  const val LB_LISTEN_PORT = 17060

  @Volatile
  private var inited = false

  private var onSipToMqtt: ((sipBody: String, from: String, to: String, callId: String, method: Int) -> Unit)? =
    null

  init {
    System.loadLibrary("sip2mqtt")
  }

  private external fun nativeInitClass(): Int
  private external fun nativeLbInit(listenPort: Int): Int
  private external fun nativeLbUnInit(): Int
  private external fun nativeMqttMsgIncoming(mqttMsg: String): Int
  private external fun nativeMqttRegStatus(success: Int): Int

  @Synchronized
  fun init(listenPort: Int = LB_LISTEN_PORT): Boolean {
    if (inited) return true
    nativeInitClass()
    val status = nativeLbInit(listenPort)
    inited = status == 0
    Log.i(TAG, "nativeLbInit port=$listenPort status=$status")
    return inited
  }

  fun uninit() {
    if (!inited) return
    nativeLbUnInit()
    inited = false
  }

  fun setOutgoingHandler(
    handler: ((sipBody: String, from: String, to: String, callId: String, method: Int) -> Unit)?,
  ) {
    onSipToMqtt = handler
  }

  fun feedMqttSipIncoming(sipBody: String): Int {
    if (!inited) return -1
    return nativeMqttMsgIncoming(sipBody)
  }

  fun notifyMqttConnected(connected: Boolean) {
    if (!inited) return
    nativeMqttRegStatus(if (connected) 1 else 0)
  }

  @JvmName("onMqttMessageCallback")
  fun onMqttMessageCallback(
    sipBody: String?,
    from: String?,
    to: String?,
    callId: String?,
    method: Int,
  ) {
    Log.i(TAG, "onMqttMessageCallback method=$method from=$from to=$to callId=$callId")
    onSipToMqtt?.invoke(
      sipBody.orEmpty(),
      from.orEmpty(),
      to.orEmpty(),
      callId.orEmpty(),
      method,
    )
  }
}
