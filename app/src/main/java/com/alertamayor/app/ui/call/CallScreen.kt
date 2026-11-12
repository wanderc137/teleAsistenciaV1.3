package com.alertamayor.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertamayor.app.R
import com.alertamayor.app.domain.model.CallState
import com.alertamayor.app.ui.theme.RedAlert
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    role: String,
    peerId: String,
    onHangUp: () -> Unit,
) {
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    val speakerOn by viewModel.speakerOn.collectAsStateWithLifecycle()

    // Video local arrastrable (PiP) — I-012
    var localOffset by remember { mutableStateOf(IntOffset.Zero) }

    // Cuando la llamada termina (por cualquiera de los dos lados), cerrar la pantalla.
    LaunchedEffect(callState) {
        if (callState == CallState.ENDED) {
            delay(1200)
            onHangUp()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Video remoto a pantalla completa
        AndroidView(
            factory = { viewModel.remoteVideoView },
            modifier = Modifier.fillMaxSize()
        )

        // Video local (Picture-in-Picture) arrastrable
        AndroidView(
            factory = { viewModel.localVideoView },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { localOffset }
                .padding(16.dp)
                .size(140.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        localOffset += IntOffset(
                            dragAmount.x.roundToInt(),
                            dragAmount.y.roundToInt()
                        )
                    }
                }
        )

        // Estado / temporizador — I-011
        Text(
            text = headerText(callState),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
            textAlign = TextAlign.Center
        )

        // Llamada entrante: Aceptar/Rechazar en modo Supervisor — I-003
        if (callState == CallState.RINGING && role == "supervisor") {
            IncomingCallOverlay(
                modifier = Modifier.align(Alignment.Center),
                onAccept = { viewModel.acceptIncomingCall() },
                onReject = {
                    viewModel.rejectIncomingCall()
                    onHangUp()
                }
            )
        } else if (callState != CallState.RINGING) {
            // Controles
            CallControls(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                muted = muted,
                speakerOn = speakerOn,
                onMute = { viewModel.toggleMute() },
                onSpeaker = { viewModel.toggleSpeaker() },
                onSwitchCamera = { viewModel.switchCamera() },
                onHangUp = {
                    viewModel.hangUp()
                    onHangUp()
                }
            )
        }
    }
}

@Composable
private fun CallControls(
    modifier: Modifier = Modifier,
    muted: Boolean,
    speakerOn: Boolean,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangUp: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingActionButton(
            onClick = onMute,
            containerColor = if (muted) Color.Gray else Color.White,
            contentColor = Color.Black
        ) {
            Icon(
                if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = stringResource(R.string.call_mute)
            )
        }

        FloatingActionButton(
            onClick = onSpeaker,
            containerColor = if (speakerOn) MaterialTheme.colorScheme.primary else Color.White,
            contentColor = Color.Black
        ) {
            Icon(
                Icons.Filled.VolumeUp,
                contentDescription = stringResource(R.string.call_speaker_on)
            )
        }

        FloatingActionButton(
            onClick = onSwitchCamera,
            containerColor = Color.White,
            contentColor = Color.Black
        ) {
            Icon(
                Icons.Filled.Cameraswitch,
                contentDescription = stringResource(R.string.call_flip_camera)
            )
        }

        FloatingActionButton(
            onClick = onHangUp,
            containerColor = RedAlert,
            contentColor = Color.White
        ) {
            Icon(
                Icons.Filled.CallEnd,
                contentDescription = stringResource(R.string.call_hang_up)
            )
        }
    }
}

@Composable
private fun IncomingCallOverlay(
    modifier: Modifier = Modifier,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.call_incoming),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.call_accept))
                }
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                ) {
                    Text(stringResource(R.string.call_reject))
                }
            }
        }
    }
}

@Composable
private fun headerText(callState: CallState): String {
    // Temporizador mm:ss cuando la llamada está activa — I-011
    if (callState == CallState.ACTIVE) {
        var elapsedSeconds by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                elapsedSeconds += 1
            }
        }
        val mm = (elapsedSeconds / 60).toString().padStart(2, '0')
        val ss = (elapsedSeconds % 60).toString().padStart(2, '0')
        return "$mm:$ss"
    }
    return when (callState) {
        CallState.DIALING -> stringResource(R.string.call_dialing)
        CallState.RINGING -> stringResource(R.string.call_ringing)
        CallState.CONNECTING -> stringResource(R.string.call_connecting)
        CallState.ACTIVE -> stringResource(R.string.call_in_progress)
        CallState.ENDED -> stringResource(R.string.call_ended)
        CallState.IDLE -> ""
    }
}
