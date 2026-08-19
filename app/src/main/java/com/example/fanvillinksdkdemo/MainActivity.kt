package com.example.fanvillinksdkdemo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton
import androidx.core.app.ActivityCompat
import com.fanvil.link.sdk.FanvilLinkSdk
import com.fanvil.link.sdk.FanvilSdkConfig
import com.fanvil.link.sdk.listener.FanvilSdkListener
import com.fanvil.link.sdk.rtc.FanvilRtcVideoView

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var rtcView: FanvilRtcVideoView
    private var mediaJoined = false

    private val sdkListener = object : FanvilSdkListener {
        override fun onMqttConnectionChanged(
            status: String,
            code: Int?,
            message: String?,
            reconnect: Boolean?
        ) {
            showStatus("MQTT: $status ${message.orEmpty()}")
        }

        override fun onSipRegistration(state: String, message: String) {
            showStatus("SIP: $state $message")
        }

        override fun onIncomingCall(
            remoteUsername: String?,
            remoteDisplayName: String?,
            remoteAddress: String?
        ) {
            showStatus("来电: ${remoteDisplayName ?: remoteUsername.orEmpty()}")
        }

        override fun onCallStateChanged(
            state: String,
            remoteUsername: String?,
            remoteDisplayName: String?,
            remoteAddress: String?
        ) {
            showStatus("通话状态: $state")
            if (state == "Connected" && !mediaJoined) {
                mediaJoined = true
                runSdkAction {
                    FanvilLinkSdk.joinCallMedia(rtcView, speakerOn = true, micEnabled = true)
                }
            }
            if (state == "End" || state == "Released" || state == "Error") {
                mediaJoined = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        rtcView = findViewById(R.id.rtcView)
        FanvilLinkSdk.addListener(sdkListener)
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
            runSdkAction {
                FanvilLinkSdk.initialize(
                    this,
                    FanvilSdkConfig(
                        userId = userId,
                        agoraId = agoraId,
                        agoraAppId = appId,
                        accessToken = token,
                        mqttUrl = mqttUrl,
                        mqttUserName = mqttUser,
                        displayName = "Android Demo"
                    )
                )
                showStatus("SDK 初始化完成")
            }
        }

        findViewById<Button>(R.id.callButton).setOnClickListener {
            val target = textOf(R.id.sipUsernameInput)
            if (target.isBlank()) {
                showStatus("请输入被叫 SIP 用户名")
                return@setOnClickListener
            }
            mediaJoined = false
            runSdkAction {
                FanvilLinkSdk.startCall(target, type = "video")
                showStatus("正在呼叫 $target")
            }
        }

        findViewById<Button>(R.id.acceptButton).setOnClickListener {
            mediaJoined = false
            runSdkAction { FanvilLinkSdk.acceptCall() }
        }

        findViewById<Button>(R.id.endButton).setOnClickListener {
            runSdkAction {
                FanvilLinkSdk.endCall()
                mediaJoined = false
                showStatus("通话已结束")
            }
        }

        findViewById<ToggleButton>(R.id.muteButton).setOnCheckedChangeListener { _, checked ->
            FanvilLinkSdk.setMuted(checked)
        }
        findViewById<ToggleButton>(R.id.speakerButton).setOnCheckedChangeListener { _, checked ->
            FanvilLinkSdk.setSpeakerOn(checked)
        }
    }

    private fun textOf(id: Int): String = findViewById<EditText>(id).text.toString().trim()

    private fun runSdkAction(action: () -> Unit) {
        Thread {
            try {
                action()
            } catch (error: Throwable) {
                showStatus("错误: ${error.message ?: error.javaClass.simpleName}")
            }
        }.start()
    }

    private fun showStatus(message: String) {
        runOnUiThread { statusText.text = message }
    }

    private fun requestMediaPermissions() {
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }

    override fun onDestroy() {
        FanvilLinkSdk.removeListener(sdkListener)
        FanvilLinkSdk.shutdown()
        super.onDestroy()
    }
}