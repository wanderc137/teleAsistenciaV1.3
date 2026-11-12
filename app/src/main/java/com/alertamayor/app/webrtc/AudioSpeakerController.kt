package com.alertamayor.app.webrtc

import android.content.Context
import android.media.AudioManager

/**
 * Controla el enrutamiento del audio (altavoz) durante una llamada WebRTC.
 */
class AudioSpeakerController(context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Activa el modo de comunicación y el altavoz (manos libres). */
    fun enableSpeakerphone() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
    }

    /** Activa el modo de comunicación con el auricular (altavoz apagado). */
    fun disableSpeakerphone() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
    }

    val isSpeakerphoneOn: Boolean
        get() = audioManager.isSpeakerphoneOn

    /** Restaura el modo de audio normal. */
    fun restore() {
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }
}
