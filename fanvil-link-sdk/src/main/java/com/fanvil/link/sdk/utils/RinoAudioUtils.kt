package com.fanvil.link.sdk.utils

import android.content.Context
import android.media.AudioManager
import android.util.Log

object RinoAudioUtils {
    fun changeToSpeaker(context: Context) {
        Log.e("[RinoAudioUtils]", "changeToSpeaker")
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
        Log.e("[RinoAudioUtils]", "changeToEarpiece")
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
        Log.e("[RinoAudioUtils]", "changeToBluetooth")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
        audioManager.isSpeakerphoneOn = false
    }

    fun changeToHeadset(context: Context) {
        Log.e("[RinoAudioUtils]", "changeToHeadset")
        changeToEarpiece(context)
    }

    fun choiceAudioModel(context: Context, isSpeakerOn: Boolean) {
        Log.e("[RinoAudioUtils]", "choiceAudioModel isSpeakerOn:$isSpeakerOn")
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
        Log.e("[RinoAudioUtils]", "setMicrophoneMute mute:$mute")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isMicrophoneMute = mute
        return 1
    }

    fun isMicrophoneMute(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.e("[RinoAudioUtils]", "isMicrophoneMute isMicrophoneMute:${audioManager.isMicrophoneMute}")
        return audioManager.isMicrophoneMute
    }

    fun setAudioManagerInCommunicationMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.e("[RinoAudioUtils]", "Setting audio manager in communication mode")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun setAudioManagerInNormalMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.e("[RinoAudioUtils]", "Setting audio manager in normal mode")
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}