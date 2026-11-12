package com.alertamayor.app.webrtc

import android.content.Context
import com.alertamayor.app.webrtc.WebRtcManager.Listener
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Gestiona la sesión WebRTC de una videollamada (cámara, micrófono, PeerConnection).
 *
 * Vive en el [AppContainer] para sobrevivir a la rotación/cierre de pantalla y a la
 * llamada en segundo plano (Foreground Service).
 */
class WebRtcManager(private val context: Context) {

    interface Listener {
        fun onConnectionEstablished()
        fun onDisconnected()
        fun onIceStateChanged(newState: PeerConnection.IceConnectionState)
    }

    companion object {
        private const val STUN_SERVER = "stun:stun.l.google.com:19302"

        /**
         * Construye la lista de servidores ICE: siempre STUN, más TURN si está configurado.
         * turnUrl debe venir como URI completo, p. ej. "turn:DOMINIO:3478?transport=tcp".
         */
        fun buildIceServers(
            turnUrl: String?,
            turnUsername: String?,
            turnPassword: String?,
        ): List<PeerConnection.IceServer> {
            val servers = mutableListOf(PeerConnection.IceServer(STUN_SERVER))
            if (!turnUrl.isNullOrBlank()) {
                val username = turnUsername?.takeIf { it.isNotBlank() }
                val password = turnPassword?.takeIf { it.isNotBlank() }
                // Constructor posicional (compatible con todas las versiones de org.webrtc).
                val turn = if (username != null && password != null) {
                    PeerConnection.IceServer(turnUrl, username, password)
                } else {
                    PeerConnection.IceServer(turnUrl)
                }
                servers.add(turn)
            }
            return servers
        }
    }

    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var videoCapturer: VideoCapturer? = null
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var peerConnection: PeerConnection? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    @Volatile
    private var closed = true

    val localVideoView = SurfaceViewRenderer(context)
    val remoteVideoView = SurfaceViewRenderer(context)

    var listener: Listener? = null

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioEnabled = true

    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var pendingOfferSdp: String = ""

    /** Callback para enviar un candidato ICE por señalización. */
    var onCandidate: ((IceCandidate) -> Unit)? = null

    init {
        initialize()
    }

    private fun initialize() {
        val egl = EglBase.create()
        eglBase = egl
        localVideoView.init(egl.eglBaseContext, null)
        remoteVideoView.init(egl.eglBaseContext, null)
        localVideoView.setMirror(true)
        localVideoView.setZOrderMediaOverlay(true)
        remoteVideoView.setZOrderMediaOverlay(false)

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val f = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(org.webrtc.DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(org.webrtc.DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f
    }

    /** Prepara captura de cámara y micrófono. Devuelve true si la cámara está disponible. */
    fun prepareCapture(): Boolean {
        closed = false
        val factory = factory ?: return false
        val egl = eglBase ?: return false

        val cameraEnumerator = Camera2Enumerator(context)
        val deviceNames = cameraEnumerator.deviceNames
        var capturer: CameraVideoCapturer? = null
        // Preferir cámara frontal (selfie) en ambos modos.
        for (name in deviceNames) {
            if (cameraEnumerator.isFrontFacing(name)) {
                capturer = cameraEnumerator.createCapturer(name, null)
                break
            }
        }
        if (capturer == null) {
            for (name in deviceNames) {
                if (cameraEnumerator.isBackFacing(name)) {
                    capturer = cameraEnumerator.createCapturer(name, null)
                    break
                }
            }
        }
        if (capturer == null) return false
        videoCapturer = capturer

        val helper = SurfaceTextureHelper.create("CaptureThread", egl.eglBaseContext)
        surfaceTextureHelper = helper
        val videoSource = factory.createVideoSource(capturer.isScreencast)
        this.videoSource = videoSource
        capturer.initialize(helper, context, videoSource.capturerObserver)
        val vTrack = factory.createVideoTrack("local_video", videoSource)
        vTrack.addSink(localVideoView)
        localVideoTrack = vTrack

        val aSource = factory.createAudioSource(MediaConstraints())
        val aTrack = factory.createAudioTrack("local_audio", aSource)
        audioSource = aSource
        localAudioTrack = aTrack

        capturer.startCapture(1280, 720, 30)
        return true
    }

    /**
     * Crea la PeerConnection y comienza la llamada.
     * @param initiator true si este dispositivo inicia la llamada (envía el offer).
     * @param iceServers lista de servidores ICE (STUN + TURN opcional).
     */
    fun startPeerConnection(initiator: Boolean, iceServers: List<PeerConnection.IceServer>) {
        val factory = factory ?: return
        val config = PeerConnection.RTCConfiguration(iceServers)
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        val pc = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                onCandidate?.invoke(candidate)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                listener?.onIceStateChanged(state)
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    listener?.onConnectionEstablished()
                } else if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED ||
                    state == PeerConnection.IceConnectionState.CLOSED
                ) {
                    listener?.onDisconnected()
                }
            }

            override fun onAddStream(stream: MediaStream) {
                stream.videoTracks.firstOrNull()?.addSink(remoteVideoView)
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver?.track()
                if (track is VideoTrack) {
                    track.addSink(remoteVideoView)
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })

        localVideoTrack?.let { pc?.addTrack(it, listOf("stream1")) }
        localAudioTrack?.let { pc?.addTrack(it, listOf("stream1")) }
        peerConnection = pc
    }

    /** Genera el offer (solo si este dispositivo inicia la llamada). */
    fun createOffer(onSdpReady: (String) -> Unit) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        onSdpReady(sdp.description)
                    }

                    override fun onSetFailure(error: String) {}
                    override fun onCreateSuccess(desc: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, sdp)
            }

            override fun onCreateFailure(error: String) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

    /** Aplica la respuesta (answer) recibida por el initiator. */
    fun setRemoteAnswer(sdp: String) {
        val pc = peerConnection ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                drainIceCandidates()
            }

            override fun onSetFailure(error: String) {}
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    /** Almacena el offer entrante. */
    fun setRemoteOffer(sdp: String) {
        pendingOfferSdp = sdp
    }

    /** Genera la respuesta (answer) cuando se recibe un offer. */
    fun createAnswer(onSdpReady: (String) -> Unit) {
        val pc = peerConnection ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                val constraints = MediaConstraints()
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                onSdpReady(sdp.description)
                            }

                            override fun onSetFailure(error: String) {}
                            override fun onCreateSuccess(desc: SessionDescription?) {}
                            override fun onCreateFailure(error: String?) {}
                        }, sdp)
                    }

                    override fun onCreateFailure(error: String) {}
                    override fun onSetSuccess() {}
                    override fun onSetFailure(error: String) {}
                }, constraints)
            }

            override fun onSetFailure(error: String) {}
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, SessionDescription(SessionDescription.Type.OFFER, pendingOfferSdp))
    }

    /** Agrega un candidato ICE recibido. */
    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        val pc = peerConnection ?: return
        val iceCandidate = IceCandidate(sdpMid ?: "0", sdpMLineIndex ?: 0, candidate)
        if (pc.remoteDescription != null) {
            pc.addIceCandidate(iceCandidate)
        } else {
            pendingIceCandidates.add(iceCandidate)
        }
    }

    private fun drainIceCandidates() {
        val pc = peerConnection ?: return
        pendingIceCandidates.forEach { pc.addIceCandidate(it) }
        pendingIceCandidates.clear()
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun setAudioEnabled(enabled: Boolean) {
        audioEnabled = enabled
        runCatching { localAudioTrack?.setEnabled(enabled) }
    }

    fun setVideoEnabled(enabled: Boolean) {
        runCatching { localVideoTrack?.setEnabled(enabled) }
    }

    fun isAudioEnabled() = audioEnabled

    /**
     * Cierra la sesión WebRTC y libera todos los recursos nativos.
     *
     * Idempotente: un segundo close() (p. ej. por race entre hangUp() y el callback
     * onDisconnected()) es un no-op gracias al guard `closed` + synchronized.
     * El orden importa: se para la captura ANTES de hacer dispose() de la
     * PeerConnection y de las fuentes/tracks.
     */
    fun close() {
        if (closed) return
        synchronized(this) {
            if (closed) return
            closed = true
            runCatching { videoCapturer?.stopCapture() }
            runCatching { peerConnection?.dispose() }
            peerConnection = null
            runCatching { localVideoTrack?.removeSink(localVideoView) }
            runCatching { localVideoTrack?.dispose() }
            localVideoTrack = null
            runCatching { localAudioTrack?.dispose() }
            localAudioTrack = null
            runCatching { videoCapturer?.dispose() }
            videoCapturer = null
            runCatching { videoSource?.dispose() }
            videoSource = null
            runCatching { audioSource?.dispose() }
            audioSource = null
            runCatching { surfaceTextureHelper?.dispose() }
            surfaceTextureHelper = null
            runCatching { localVideoView.clearImage() }
            runCatching { remoteVideoView.clearImage() }
        }
    }
}
