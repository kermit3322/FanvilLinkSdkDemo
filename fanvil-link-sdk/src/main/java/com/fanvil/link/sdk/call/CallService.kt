package com.fanvil.link.sdk.call

import com.fanvil.link.sdk.rtc.RinoRtcEngine
import com.fanvil.link.sdk.sip.SipCore

data class CallSession(
  val callId: String,
  val deviceId: String,
  val startedAt: Long = System.currentTimeMillis(),
)

object CallService {
  fun startCall(
    sip: SipCore,
    sipUsername: String,
    displayName: String? = null,
    type: String = "video",
  ): CallSession {
    sip.makeCall(username = sipUsername, displayName = displayName, type = type)
    return CallSession(callId = "${sipUsername}_${System.currentTimeMillis()}", deviceId = sipUsername)
  }

  fun accept(sip: SipCore) {
    sip.accept()
  }

  fun hangup(sip: SipCore, rtc: RinoRtcEngine?) {
    sip.hangup()
    try {
      rtc?.leaveChannel()
    } catch (_: Exception) {
    }
  }

  fun setMuted(sip: SipCore, rtc: RinoRtcEngine?, muted: Boolean) {
    sip.setMicEnabled(!muted)
    try {
      rtc?.setMuteAudio(muted)
    } catch (_: Exception) {
    }
  }

  fun setSpeakerOn(sip: SipCore, rtc: RinoRtcEngine?, enabled: Boolean) {
    sip.setSpeakerEnabled(enabled)
    try {
      rtc?.setEnableSpeakerphone(enabled)
    } catch (_: Exception) {
    }
  }
}
