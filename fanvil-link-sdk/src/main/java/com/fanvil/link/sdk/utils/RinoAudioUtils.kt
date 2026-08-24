package com.fanvil.link.sdk.utils

import android.content.Context
import android.media.AudioManager
import com.fanvil.link.sdk.utils.FvlLogger

object RinoAudioUtils {
    private val log = FvlLogger.getLogger("RinoAudioUtils")

    fun changeToSpeaker(context: Context) {
        log.i("changeToSpeaker")
        try {
            val audioManager = context
                .getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
        } catch (e: java.lang.Exception) {
            throw java.lang.RuntimeException(e)
        }
    }

    fun changeToEarpiece(context: Context) {
        log.i("changeToEarpiece")
        try {
            val audioManager = context
                .getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
            audioManager.isBluetoothScoOn = false
            audioManager.startBluetoothSco()
            audioManager.stopBluetoothSco()
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun changeToBluetooth(context: Context) {
        log.i("changeToBluetooth")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
        audioManager.isSpeakerphoneOn = false
    }

    fun changeToHeadset(context: Context) {
        log.i("changeToHeadset")
        changeToEarpiece(context)
    }

    fun choiceAudioModel(context: Context, isSpeakerOn: Boolean) {
        log.i("choiceAudioModel isSpeakerOn:$isSpeakerOn")
        if (isWiredHeadsetOn(context)) {
            changeToHeadset(context)
        } else if (isBluetoothA2dpOn(context)) {
            changeToBluetooth(context)
        } else {
            if (isSpeakerOn) {
                changeToSpeaker(context)
            } else {
                changeToEarpiece(context)
            }
        }
    }

    fun isSpeakerphoneOn(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isSpeakerphoneOn
    }

    fun isWiredHeadsetOn(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isWiredHeadsetOn
    }

    fun isBluetoothA2dpOn(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isBluetoothA2dpOn
    }

    fun isBluetoothScoOn(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isBluetoothScoOn
    }

    fun setMicrophoneMute(context: Context, mute: Boolean): Int {
        log.i("setMicrophoneMute mute:$mute")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isMicrophoneMute = mute
        return 1
    }

    fun isMicrophoneMute(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        log.d("isMicrophoneMute isMicrophoneMute:${audioManager.isMicrophoneMute}")
        return audioManager.isMicrophoneMute
    }

    fun setAudioManagerInCommunicationMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        log.i("setAudioManagerInCommunicationMode")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun setAudioManagerInNormalMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        log.i("setAudioManagerInNormalMode")
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}