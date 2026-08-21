package com.example.fanvillinksdkdemo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ToggleButton
import androidx.core.app.ActivityCompat
import com.fanvil.link.sdk.FvCloudTalkSDK
import com.fanvil.link.sdk.FvSdkConfig
import com.fanvil.link.sdk.call.CallState
import com.fanvil.link.sdk.listener.FvSdkListener
import com.fanvil.link.sdk.rtc.RtcEvent
import com.fanvil.link.sdk.sip.SipRegistrationState

class MainActivity : Activity() {
    companion object {
        const val TAG = "Fvl_MainActivity"
    }

    private lateinit var statusText: TextView

    private val sdkListener = object : FvSdkListener {
        override fun onMqttConnectionChanged(
            status: String,
            code: Int?,
            message: String?,
            reconnect: Boolean?
        ) {
            showStatus("MQTT: $status ${message.orEmpty()}")
        }

        override fun onSipRegistration(state: SipRegistrationState, message: String) {
            showStatus("SIP: $state $message")
        }

        override fun onCallStateChanged(
            state: CallState,
            remoteUsername: String?,
            remoteDisplayName: String?,
            remoteAddress: String?
        ) {
            if (state == CallState.Incoming) {
                showStatus("来电: ${remoteDisplayName ?: remoteUsername.orEmpty()}")
            } else {
                showStatus("通话状态: $state")
            }
        }

        override fun onRtcEvent(event: RtcEvent, payload: Map<String, Any?>) {
            Log.e(TAG, "onRtcEvent event=$event,payload=$payload")
        }

        override fun onMonitorCountdown(remainSeconds: Int) {
            showStatus("监控剩余: ${remainSeconds}s")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        findViewById<FrameLayout>(R.id.rtcView).addView(
            FvCloudTalkSDK.getRtcView(this),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        FvCloudTalkSDK.addListener(sdkListener)
        requestMediaPermissions()

        findViewById<Button>(R.id.initButton).setOnClickListener {
            val userId = textOf(R.id.userIdInput)
            val agoraId = textOf(R.id.agoraIdInput)
            val appId = textOf(R.id.agoraAppIdInput)
            val token = textOf(R.id.accessTokenInput)
            val mqttUrl = textOf(R.id.mqttUrlInput)
            val mqttUser = textOf(R.id.mqttUserInput)
            if (listOf(userId, agoraId, appId, token, mqttUrl, mqttUser).any { it.isBlank() }) {
                showStatus("请填写完整的初始化参数")
                return@setOnClickListener
            }
            FvCloudTalkSDK.initialize(
                this,
                FvSdkConfig(
                    userId = userId,
                    agoraId = agoraId,
                    agoraAppId = appId,
                    accessToken = token,
                    mqttUrl = mqttUrl,
                    mqttUserName = mqttUser,
                    displayName = "Android Demo"
                )
            )
            showStatus("正在初始化")
        }

        findViewById<Button>(R.id.callButton).setOnClickListener {
            val target = textOf(R.id.sipUsernameInput)
            if (target.isBlank()) {
                showStatus("请输入被叫 SIP 用户名")
                return@setOnClickListener
            }
            FvCloudTalkSDK.startCall(target, type = "video")
            showStatus("正在呼叫 $target")
        }

        findViewById<Button>(R.id.monitorButton).setOnClickListener {
            val target = textOf(R.id.sipUsernameInput)
            if (target.isBlank()) {
                showStatus("请输入监控 SIP 用户名")
                return@setOnClickListener
            }
            FvCloudTalkSDK.startMonitor(target)
            showStatus("正在监控 $target")
        }

        findViewById<Button>(R.id.acceptButton).setOnClickListener {
            FvCloudTalkSDK.acceptCall()
        }

        findViewById<Button>(R.id.endButton).setOnClickListener {
            FvCloudTalkSDK.endCall()
            showStatus("已结束")
        }

        findViewById<ToggleButton>(R.id.muteButton).setOnCheckedChangeListener { _, checked ->
            FvCloudTalkSDK.setMuted(checked)
        }
        findViewById<ToggleButton>(R.id.speakerButton).setOnCheckedChangeListener { _, checked ->
            FvCloudTalkSDK.setSpeakerOn(checked)
        }
    }

    private fun textOf(id: Int): String = findViewById<EditText>(id).text.toString().trim()

    private fun showStatus(message: String) {
        runOnUiThread { statusText.text = message }
    }

    private fun requestMediaPermissions() {
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (permissions.any {
                ActivityCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }

    override fun onDestroy() {
        FvCloudTalkSDK.removeListener(sdkListener)
        FvCloudTalkSDK.shutdown()
        super.onDestroy()
    }
}
