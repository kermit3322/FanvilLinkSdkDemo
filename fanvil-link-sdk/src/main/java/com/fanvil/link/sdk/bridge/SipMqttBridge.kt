package com.fanvil.link.sdk.bridge

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import com.fanvil.link.sdk.mqtt.MqttClientHolder
import com.fanvil.link.sdk.mqtt.MqttTopics
import com.fanvil.link.sdk.rtc.RinoRtcEngine
import com.fanvil.link.sdk.rtc.RtcViewRegistry
import com.fanvil.link.sdk.sip.LoopBackManager
import com.fanvil.link.sdk.sip.SipCore
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 对齐 TS sip-mqtt-bridge：SIP ↔ MQTT ↔ RTC token。
 */
class SipMqttBridge(
  private val mqtt: MqttClientHolder,
  private val sip: SipCore,
  private val rtc: RinoRtcEngine,
) {
  companion object {
    private const val TAG = "FvBridge"
  }

  private var agoraId: String = ""
  private var userId: String = ""
  private var agoraAppId: String = ""
  private var displayName: String? = null

  private val started = AtomicBoolean(false)
  private var pendingRtcChannel = ""
  private var rtcCallId = ""
  private var rtcRemoteUid = RinoRtcEngine.FV_DEVICE_REMOTE_ID
  private var rtcReady: RtcReadyState? = null
  private val rtcWaiters = CopyOnWriteArrayList<(RtcReadyState) -> Unit>()
  private val mainHandler = Handler(Looper.getMainLooper())

  data class RtcReadyState(
    val channelName: String,
    val localUid: Int,
    val remoteUid: Int,
    val agoraAppId: String,
    val userId: String,
    val rtcToken: String,
    val expireSecond: Int,
    val rtmAccount: String?,
    val rtmToken: String?,
    val rtmExpire: Int,
  )

  fun start(agoraId: String, userId: String, agoraAppId: String, displayName: String?) {
    this.agoraId = agoraId
    this.userId = userId
    this.agoraAppId = agoraAppId
    this.displayName = displayName

    if (started.compareAndSet(false, true)) {
      LoopBackManager.setOutgoingHandler { sipBody, from, to, callId, method ->
        val bean = JSONObject()
          .put("version", "1.0.0")
          .put("callId", callId)
          .put("sipType", method)
          .put("channelName", rtcReady?.channelName.orEmpty())
          .put("msgId", "app_${System.currentTimeMillis()}")
          .put("from", from)
          .put("to", to)
          .put("aid", agoraId.toLongOrNull() ?: 0)
          .put("sipBody", sipBody)
        try {
          mqtt.publish(MqttTopics.sipUp(agoraId), bean.toString())
        } catch (e: Exception) {
          Log.w(TAG, "publish sip/up failed", e)
        }
      }
      LoopBackManager.init(LoopBackManager.LB_LISTEN_PORT)
      sip.initialize()
    }

    LoopBackManager.notifyMqttConnected(true)
    sip.register(
      username = agoraId,
      displayName = displayName ?: agoraId,
      domain = "127.0.0.1",
      port = LoopBackManager.LB_LISTEN_PORT.toString(),
    )
  }

  fun onMqttMessage(topic: String, payload: String) {
    if (userId.isEmpty() || agoraId.isEmpty()) return
    when (topic) {
      MqttTopics.tokenAck(userId) -> handleRtcTokenAck(payload)
      MqttTopics.msgCallDown(agoraId) -> {
        try {
          val message = JSONObject(payload)
          if (message.optString("msgType") == "channelChange") {
            val channelName = message.optJSONObject("data")?.optString("channelName").orEmpty()
            if (channelName.isNotEmpty()) {
              pendingRtcChannel = ""
              requestRtcToken(channelName)
            }
          }
        } catch (_: Exception) {
        }
      }
      MqttTopics.sipDown(agoraId) -> {
        val sipBody = parseSipBody(payload) ?: return
        val aid = try {
          JSONObject(payload).optInt("aid", 0)
        } catch (_: Exception) {
          0
        }
        val channelName = try {
          JSONObject(payload).optString("channelName")
        } catch (_: Exception) {
          ""
        }
        val callId = try {
          JSONObject(payload).optString("callId")
        } catch (_: Exception) {
          ""
        }
        if (aid > 0) rtcRemoteUid = aid
        if (channelName.isNotEmpty()) requestRtcToken(channelName, callId)
        LoopBackManager.feedMqttSipIncoming(sipBody)
      }
    }
  }

  fun onMqttConnectionChanged(connected: Boolean) {
    LoopBackManager.notifyMqttConnected(connected)
  }

  fun joinOutgoingCallRtc(
    videoContainer: ViewGroup? = null,
    isSpeakerOn: Boolean = true,
    timeoutMs: Long = 15_000,
    micEnabled: Boolean = true,
  ) {
    Log.e(TAG,"joinOutgoingCallRtc rtcReady=$rtcReady")
    val ready = rtcReady ?: awaitRtcReady(timeoutMs)
    rtc.initIpc(ready.agoraAppId)
    rtc.setToken(
      agoraAppId = ready.agoraAppId,
      userId = ready.userId,
      channelName = ready.channelName,
      uid = ready.localUid,
      rtcToken = ready.rtcToken,
      expireSecond = ready.expireSecond,
      rtmAccount = ready.rtmAccount,
      rtmToken = ready.rtmToken,
      rtmExpire = ready.rtmExpire,
    )
    rtc.setRemoteUid(rtcRemoteUid)
    rtc.setRemoteChannelName(ready.channelName)
    rtc.setMicEnabled(micEnabled)
    val container = videoContainer ?: RtcViewRegistry.current
      ?: throw IllegalStateException("FvRtcVideoView not mounted")
    rtc.attachPlayerContainer(container)

    var lastError: Exception? = null
    repeat(8) {
      try {
        rtc.joinChannel(ready.localUid, isSpeakerOn)
        return
      } catch (e: Exception) {
        lastError = e
        if (e.message?.contains("not ready") != true && e.message?.contains("not mounted") != true) {
          throw e
        }
        Thread.sleep(150)
      }
    }
    throw lastError ?: IllegalStateException("FvRtcVideoView not mounted")
  }

  fun stop() {
    LoopBackManager.setOutgoingHandler(null)
    LoopBackManager.uninit()
    started.set(false)
    pendingRtcChannel = ""
    rtcCallId = ""
    rtcReady = null
    rtcRemoteUid = RinoRtcEngine.FV_DEVICE_REMOTE_ID
    rtcWaiters.clear()
  }

  private fun requestRtcToken(channelName: String, callId: String = rtcCallId) {
    if (callId.isNotEmpty() && rtcCallId != callId) {
      rtcCallId = callId
      pendingRtcChannel = ""
      rtcReady = null
    }
    if (userId.isEmpty() || channelName.isEmpty() || pendingRtcChannel == channelName) return
    pendingRtcChannel = channelName
    rtcReady = null
    val body = JSONObject()
      .put("version", "1.0.0")
      .put("channelName", channelName)
      .put("aid", agoraId.toLongOrNull() ?: 0)
      .put("msgId", "app_${System.currentTimeMillis()}")
      .put("rawData", "")
    try {
      mqtt.publish(MqttTopics.tokenRequest(userId), body.toString())
    } catch (e: Exception) {
      pendingRtcChannel = ""
      Log.w(TAG, "token request failed", e)
    }
  }

  private fun handleRtcTokenAck(payload: String) {
    try {
      val parsed = JSONObject(payload)
      val data = parsed.optJSONObject("data") ?: return
      val rtcTokenJson = data.optJSONObject("rtcToken") ?: return
      val channelName = rtcTokenJson.optString("channelName")
      val token = rtcTokenJson.optString("rtcToken")
      val localUid = agoraId.toIntOrNull() ?: 0
      if (channelName.isEmpty() || token.isEmpty() || localUid <= 0 || agoraAppId.isEmpty()) return
      if (pendingRtcChannel.isNotEmpty() && channelName != pendingRtcChannel) return

      val rtm = data.optJSONObject("rtmToken")
      val ready = RtcReadyState(
        channelName = channelName,
        localUid = localUid,
        remoteUid = rtcRemoteUid,
        agoraAppId = agoraAppId,
        userId = localUid.toString(),
        rtcToken = token,
        expireSecond = rtcTokenJson.optInt("expireSecond", 3600),
        rtmAccount = rtm?.optString("account"),
        rtmToken = rtm?.optString("rtmToken"),
        rtmExpire = rtm?.optInt("expireSecond", 3600) ?: 3600,
      )
      rtc.initIpc(agoraAppId)
      rtc.setToken(
        agoraAppId = ready.agoraAppId,
        userId = ready.userId,
        channelName = ready.channelName,
        uid = ready.localUid,
        rtcToken = ready.rtcToken,
        expireSecond = ready.expireSecond,
        rtmAccount = ready.rtmAccount,
        rtmToken = ready.rtmToken,
        rtmExpire = ready.rtmExpire,
      )
      rtcReady = ready
      rtcWaiters.toList().forEach { it(ready) }
      rtcWaiters.clear()
    } catch (e: Exception) {
      Log.w(TAG, "handleRtcTokenAck", e)
    }
  }

  private fun awaitRtcReady(timeoutMs: Long): RtcReadyState {
    rtcReady?.let { return it }
    val latch = CountDownLatch(1)
    var result: RtcReadyState? = null
    val waiter: (RtcReadyState) -> Unit = {
      result = it
      latch.countDown()
    }
    rtcWaiters.add(waiter)
    val ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    rtcWaiters.remove(waiter)
    if (!ok || result == null) throw IllegalStateException("RTC token timeout")
    return result!!
  }

  private fun parseSipBody(payload: String): String? {
    return try {
      val parsed = JSONObject(payload)
      when {
        parsed.has("sipBody") -> parsed.optString("sipBody")
        parsed.optJSONObject("data")?.has("sipBody") == true ->
          parsed.optJSONObject("data")!!.optString("sipBody")
        else -> null
      }.takeIf { !it.isNullOrEmpty() }
    } catch (_: Exception) {
      if (payload.contains("SIP/") || payload.startsWith("INVITE")) payload else null
    }
  }
}
