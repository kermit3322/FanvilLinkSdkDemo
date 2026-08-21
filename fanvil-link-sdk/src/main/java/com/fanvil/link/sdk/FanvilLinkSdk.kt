package com.fanvil.link.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import com.fanvil.link.sdk.bridge.SipMqttBridge
import com.fanvil.link.sdk.call.CallService
import com.fanvil.link.sdk.call.CallSession
import com.fanvil.link.sdk.call.CallState
import com.fanvil.link.sdk.door.DoorService
import com.fanvil.link.sdk.listener.FanvilSdkListener
import com.fanvil.link.sdk.mqtt.MqttClientHolder
import com.fanvil.link.sdk.mqtt.buildMqttUsername
import com.fanvil.link.sdk.mqtt.defaultSubscribeTopics
import com.fanvil.link.sdk.rtc.FanvilRtcVideoView
import com.fanvil.link.sdk.rtc.RinoRtcEngine
import com.fanvil.link.sdk.sip.SipCore
import com.fanvil.link.sdk.sip.SipRegistrationState
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fanvil Link 原生 SDK 入口（开门 / 呼叫 / RTC）。
 * 原生工程与 Expo Module 共用同一套实现。
 */
object FanvilLinkSdk {
  private const val TAG = "FanvilLinkSdk1"

  @Volatile
  private var appContext: Context? = null

  @Volatile
  private var config: FanvilSdkConfig? = null

  private var mqtt: MqttClientHolder? = null
  private var sip: SipCore? = null
  private var rtc: RinoRtcEngine? = null
  @Volatile
  private var rtcVideoView: FanvilRtcVideoView? = null
  private var bridge: SipMqttBridge? = null
  private var activeCall: CallSession? = null

  @Volatile
  private var mediaJoined = false

  @Volatile
  private var monitorMode = false

  private val mainHandler = Handler(Looper.getMainLooper())
  private var monitorRemainSeconds = 0
  private val monitorTick = object : Runnable {
    override fun run() {
      val remain = monitorRemainSeconds
      listeners.forEach { it.onMonitorCountdown(remain) }
      if (remain <= 0) {
        stopMonitorTimer()
        endCall()
        return
      }
      monitorRemainSeconds = remain - 1
      mainHandler.postDelayed(this, 1000)
    }
  }

  private val listeners = CopyOnWriteArrayList<FanvilSdkListener>()

  fun addListener(listener: FanvilSdkListener) {
    listeners.add(listener)
    Log.d(TAG, "addListener size=${listeners.size}")
  }

  fun removeListener(listener: FanvilSdkListener) {
    listeners.remove(listener)
    Log.d(TAG, "removeListener size=${listeners.size}")
  }

  fun initialize(context: Context, config: FanvilSdkConfig) {
    Log.i(
      TAG,
      "initialize userId=${config.userId} agoraId=${config.agoraId} " +
        "mqttUrl=${config.mqttUrl.trim()} mqttUser=${config.mqttUserName.trim()} " +
        "appId=${config.agoraAppId} token=${config.accessToken}",
    )
    val app = context.applicationContext
    appContext = app
    this.config = config

    val mqttHolder = mqtt ?: MqttClientHolder(
      onConnectionChanged = { status, code, message, reconnect ->
        Log.i(TAG, "mqtt connection status=$status code=$code reconnect=$reconnect msg=$message")
        listeners.forEach { it.onMqttConnectionChanged(status, code, message, reconnect) }
        bridge?.onMqttConnectionChanged(status == "connected")
      },
      onMessage = { topic, payload ->
        Log.d(TAG, "mqtt message topic=$topic len=${payload.length}")
        listeners.forEach { it.onMqttMessage(topic, payload) }
        bridge?.onMqttMessage(topic, payload)
      },
    ).also { mqtt = it }

    val sipCore = sip ?: SipCore(app) { event, payload ->
      dispatchSipEvent(event, payload)
    }.also { sip = it }

    val rtcEngine = rtc ?: RinoRtcEngine(app) { event, payload ->
      Log.d(TAG, "rtc event=$event")
      listeners.forEach { it.onRtcEvent(event, payload) }
    }.also { rtc = it }

    val topics = defaultSubscribeTopics(config.userId, config.agoraId)
    if (!mqttHolder.isConnected()) {
      Log.i(TAG, "mqtt connect clientId=${config.userId}")
      mqttHolder.connect(
        url = config.mqttUrl.trim(),
        clientId = config.userId,
        username = buildMqttUsername(config.mqttUserName.trim()),
        password = config.accessToken.trim(),
      )
    } else {
      Log.i(TAG, "mqtt already connected, skip connect")
    }
    Log.i(TAG, "mqtt subscribe topics=$topics")
    mqttHolder.subscribe(topics)

    val sipBridge = bridge ?: SipMqttBridge(mqttHolder, sipCore, rtcEngine).also { bridge = it }
    sipBridge.start(
      agoraId = config.agoraId,
      userId = config.userId,
      agoraAppId = config.agoraAppId.trim(),
      displayName = config.displayName,
    )
    Log.i(TAG, "initialize done userId=${config.userId} agoraId=${config.agoraId}")
  }

  fun isReady(): Boolean = config != null && mqtt?.isConnected() == true

  fun isCalling(): Boolean = sip?.isCalling() == true || activeCall != null

  fun isMediaJoined(): Boolean = mediaJoined

  fun isMonitorMode(): Boolean = monitorMode

  fun getActiveCall(): CallSession? = activeCall

  fun getRtcView(context: Context): FanvilRtcVideoView {
    val existing = rtcVideoView
    if (existing != null) {
      (existing.parent as? ViewGroup)?.removeView(existing)
      return existing
    }
    return FanvilRtcVideoView(context).also { rtcVideoView = it }
  }

  fun openDoor(mac: String, whichDoor: Int = 1, doorNoList: List<Int>? = null) {
    Log.i(TAG, "openDoor mac=$mac whichDoor=$whichDoor doorNoList=$doorNoList")
    val cfg = requireConfig()
    val mqttHolder = mqtt ?: throw IllegalStateException("SDK not initialized")
    if (!mqttHolder.isConnected()) throw IllegalStateException("MQTT is not connected")
    DoorService.openDoor(mqttHolder, cfg.userId, mac, whichDoor, doorNoList)
    Log.i(TAG, "openDoor published userId=${cfg.userId}")
  }

  fun startCall(
    sipUsername: String,
    displayName: String? = null,
    type: String = "video",
  ): CallSession {
    Log.i(
      TAG,
      "startCall sipUsername=$sipUsername type=$type displayName=$displayName",
    )
    requireConfig()
    val sipCore = sip ?: throw IllegalStateException("SDK not initialized")
    if (sipCore.isCalling()) {
      Log.w(TAG, "startCall rejected: already calling")
      throw IllegalStateException("A call is already in progress")
    }
    val session = CallService.startCall(sipCore, sipUsername, displayName, type)
    activeCall = session
    mediaJoined = false
    monitorMode = type == "monitor"
    Log.i(TAG, "startCall ok callId=${session.callId} monitorMode=$monitorMode")
    return session
  }

  private fun joinMedia(speakerOn: Boolean = true) {
    val micEnabled = !monitorMode
    Log.i(TAG, "joinMedia speakerOn=$speakerOn micEnabled=$micEnabled monitorMode=$monitorMode")
    joinCallMedia(rtcVideoView, speakerOn, micEnabled = micEnabled)
  }

  private fun joinCallMedia(
    videoContainer: ViewGroup? = null,
    speakerOn: Boolean = true,
    micEnabled: Boolean = true,
  ) {
    Log.i(
      TAG,
      "joinCallMedia speakerOn=$speakerOn micEnabled=$micEnabled " +
        "hasContainer=${videoContainer != null}",
    )
    requireConfig()
    val sipBridge = bridge ?: throw IllegalStateException("SDK not initialized")
    if (mediaJoined) {
      Log.i(TAG, "joinCallMedia skipped: already joined")
      return
    }
    mediaJoined = true
    try {
      sipBridge.joinOutgoingCallRtc(videoContainer, speakerOn, micEnabled = micEnabled)
    } catch (e: Exception) {
      mediaJoined = false
      throw e
    }
    Log.i(TAG, "joinCallMedia done")
  }

  fun startMonitor(
    sipUsername: String,
    displayName: String? = null,
    timeoutSeconds: Int = 30,
  ): CallSession {
    Log.i(TAG, "startMonitor sipUsername=$sipUsername timeoutSeconds=$timeoutSeconds")
    setMuted(true)
    val session = startCall(sipUsername, displayName, type = "monitor")
    startMonitorTimer(timeoutSeconds)
    return session
  }

  private fun startMonitorTimer(timeoutSeconds: Int) {
    stopMonitorTimer()
    if (timeoutSeconds <= 0) return
    monitorRemainSeconds = timeoutSeconds
    mainHandler.post(monitorTick)
  }

  private fun stopMonitorTimer() {
    mainHandler.removeCallbacks(monitorTick)
  }

  fun takeSnapshot(filePath: String? = null, saveToGallery: Boolean = false): Int {
    Log.i(TAG, "takeSnapshot filePath=$filePath saveToGallery=$saveToGallery")
    requireConfig()
    val code = rtc?.takeSnapshot(filePath, saveToGallery)
      ?: throw IllegalStateException("RTC is not ready")
    Log.i(TAG, "takeSnapshot result=$code")
    return code
  }

  fun clearRtcCache() {
    Log.i(TAG, "clearRtcCache")
    try {
      rtc?.clearCache()
    } catch (e: Exception) {
      Log.w(TAG, "clearRtcCache failed", e)
    }
  }

  fun acceptCall() {
    Log.i(TAG, "acceptCall")
    requireConfig()
    monitorMode = false
    mediaJoined = false
    CallService.accept(sip ?: throw IllegalStateException("SDK not initialized"))
  }

  fun rejectCall() {
    Log.i(TAG, "rejectCall")
    endCall()
  }

  fun endCall() {
    Log.i(TAG, "endCall activeCallId=${activeCall?.callId} calling=${sip?.isCalling()}")
    val sipCore = sip ?: return
    CallService.hangup(sipCore, rtc)
    activeCall = null
    mediaJoined = false
    monitorMode = false
    stopMonitorTimer()
    Log.i(TAG, "endCall done")
  }

  fun setMuted(muted: Boolean) {
    Log.i(TAG, "setMuted muted=$muted")
    val sipCore = sip ?: return
    CallService.setMuted(sipCore, rtc, muted)
  }

  fun setSpeakerOn(enabled: Boolean) {
    Log.i(TAG, "setSpeakerOn enabled=$enabled")
    val sipCore = sip ?: return
    CallService.setSpeakerOn(sipCore, rtc, enabled)
  }

  fun shutdown() {
    Log.i(TAG, "shutdown begin calling=${sip?.isCalling()} activeCall=${activeCall != null}")
    try {
      if (sip?.isCalling() == true || activeCall != null) {
        endCall()
      }
    } catch (e: Exception) {
      Log.w(TAG, "shutdown endCall failed", e)
    }
    try {
      sip?.unregister()
    } catch (e: Exception) {
      Log.w(TAG, "shutdown unregister failed", e)
    }
    bridge?.stop()
    bridge = null
    try {
      mqtt?.disconnect()
    } catch (e: Exception) {
      Log.w(TAG, "shutdown mqtt disconnect failed", e)
    }
    try {
      rtc?.destroy()
    } catch (e: Exception) {
      Log.w(TAG, "shutdown rtc destroy failed", e)
    }
    try {
      sip?.destroy()
    } catch (e: Exception) {
      Log.w(TAG, "shutdown sip destroy failed", e)
    }
    mqtt = null
    sip = null
    rtc = null
    rtcVideoView?.let { (it.parent as? ViewGroup)?.removeView(it) }
    rtcVideoView = null
    activeCall = null
    mediaJoined = false
    monitorMode = false
    config = null
    appContext = null
    Log.i(TAG, "shutdown done")
  }

  private fun requireConfig(): FanvilSdkConfig {
    val cfg = config
    if (cfg == null) {
      Log.e(TAG, "requireConfig failed: SDK not initialized")
      throw IllegalStateException("Fanvil SDK is not initialized")
    }
    return cfg
  }

  private fun dispatchSipEvent(event: String, payload: Map<String, Any?>) {
    Log.i(TAG, "sip event=$event payload=$payload")
    when (event) {
      "onRegistration" -> {
        val state = payload["state"] as? SipRegistrationState ?: return
        listeners.forEach {
          it.onSipRegistration(state, payload["message"] as? String ?: "")
        }
      }
      "onCallStateChanged" -> {
        val state = payload["state"] as? CallState ?: return
        if (state == CallState.Incoming) {
          monitorMode = false
          mediaJoined = false
          stopMonitorTimer()
        }
        if (state == CallState.Connected) {
          try {
            joinMedia()
          } catch (e: Exception) {
            Log.e(TAG, "auto joinMedia failed", e)
          }
        }
        if (state == CallState.End || state == CallState.Released || state == CallState.Error) {
          mediaJoined = false
          monitorMode = false
          stopMonitorTimer()
        }
        listeners.forEach {
          it.onCallStateChanged(
            state,
            payload["remoteUsername"] as? String,
            payload["remoteDisplayName"] as? String,
            payload["remoteAddress"] as? String,
          )
        }
      }
    }
  }
}
