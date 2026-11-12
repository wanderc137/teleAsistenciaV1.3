package com.alertamayor.app.ui.supervised

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Surface
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import com.alertamayor.app.R
import com.alertamayor.app.data.network.NetworkStatus
import com.alertamayor.app.domain.model.CallState
import com.alertamayor.app.ui.theme.GreenOnline
import com.alertamayor.app.ui.theme.RedAlert
import com.alertamayor.app.ui.theme.RedAlertDark

@Composable
fun SupervisedHomeScreen(
    viewModel: SupervisedHomeViewModel,
    onCall: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qrBitmap.collectAsStateWithLifecycle()
    val isLinked by viewModel.isLinked.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val activePeerId by viewModel.activePeerId.collectAsStateWithLifecycle()
    val incomingPeerId by viewModel.incomingPeerId.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()

    // R-002 (ciclo 4): bloquear el botón Back del sistema en modo Supervisado para
    // que el anciano/niño no pueda salir de la pantalla de ALERTA.
    BackHandler {}

    // Permisos cámara/micrófono
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun ensurePermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        ensurePermissions()
        viewModel.generateQr(context)
    }

    // Navega automáticamente a la llamada cuando hay una en curso (auto-respuesta)
    // Usa el par activo/entrante del coordinator, no linkedPeerId (que en modo
    // Supervisado siempre está vacío). — I-002
    LaunchedEffect(callState, activePeerId, incomingPeerId) {
        if (callState == CallState.RINGING || callState == CallState.CONNECTING ||
            callState == CallState.ACTIVE
        ) {
            val peerId = activePeerId.ifEmpty { incomingPeerId }
            if (peerId.isNotEmpty()) {
                onCall(peerId)
            }
        }
    }

    // La pantalla se mantiene encendida gracias a FLAG_KEEP_SCREEN_ON en MainActivity
    // (R-003 ciclo 4), de modo que el botón rojo de ALERTA esté siempre visible/operativo.
    // M-004 (ciclo 12): fondo NEGRO en la pantalla de la campanilla (el blanco molesta,
    // sobre todo de noche). Los textos/badges se ajustan a colores claros para legibilidad.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Estado
            StatusBadge(connected = state.connected, linked = isLinked, peerName = state.linkedPeerName)

            Spacer(Modifier.height(40.dp))

            if (!isLinked) {
                // Mostrar QR de vinculación
                Text(
                    text = stringResource(R.string.supervised_no_link),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_qr_code),
                        modifier = Modifier.size(220.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.generateQr(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                ) {
                    Text(stringResource(R.string.supervised_link_qr))
                }
            } else {
                // Botón rojo grande de alerta.
                // M-002 (ciclo 6): sin internet (NONE) el botón se muestra gris con
                // barra diagonal roja y su onClick no inicia ninguna llamada.
                val online = networkStatus != NetworkStatus.NONE
                Box(
                    modifier = Modifier.offset(y = 20.dp)
                ) {
                    BigRedButton(
                        onClick = { if (online) viewModel.callLinkedPeer() },
                        enabled = online
                    )
                }
                Spacer(Modifier.height(24.dp))
                // Ciclo 12-fix: debajo del botón, "SOS" en amarillo, MUY grande y bold.
                Text(
                    text = stringResource(R.string.supervised_sos),
                    fontSize = 120.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color(0xFFFFC107), // amarillo
                    textAlign = TextAlign.Center
                )
            }
        }

        // Indicador de conexión a internet (arriba a la izquierda) — M-002 (ciclo 6)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = when (networkStatus) {
                    NetworkStatus.WIFI -> Icons.Filled.Wifi
                    NetworkStatus.CELLULAR -> Icons.Filled.SignalCellularAlt
                    NetworkStatus.NONE -> Icons.Filled.WifiOff
                },
                contentDescription = stringResource(
                    when (networkStatus) {
                        NetworkStatus.WIFI -> R.string.network_status_wifi
                        NetworkStatus.CELLULAR -> R.string.network_status_cellular
                        NetworkStatus.NONE -> R.string.network_status_none
                    }
                ),
                tint = when (networkStatus) {
                    NetworkStatus.NONE -> RedAlert
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(28.dp)
            )
        }

        // Botón de configuración.
        // I-011 (V1.3): el tint por defecto de MaterialTheme es oscuro y quedaba
        // invisible sobre el fondo NEGRO (no se veía la rueda en ningún modo).
        // Se fuerza Color.White para que contraste y sea SIEMPRE visible.
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun StatusBadge(connected: Boolean, linked: Boolean, peerName: String) {
    // Ciclo 12-fix: mostrar "Actualmente Supervisado" en amarillo centrado.
    // (Ya no se muestra el token/ID del par vinculado, que no aporta al anciano.)
    Text(
        text = stringResource(R.string.supervised_currently_supervised),
        color = Color(0xFFFFC107), // amarillo
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun BigRedButton(onClick: () -> Unit, enabled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (isPressed) 0.92f else 1f)
    val haptic = LocalHapticFeedback.current

    // ============================================================
    // EFECTO 3D HIPERREALISTA: esfera roja que rebota como pelota.
    // Ciclo de rebote sincronizado de 1000 ms:
    //   - La esfera cae (acelera), toca el "suelo" con squash & stretch,
    //     rebota y sube (desacelera).
    //   - La sombra en el suelo crece/encoge según la altura.
    // ============================================================
    val bounceTransition = rememberInfiniteTransition(label = "bounce")

    // Altura de la pelota sobre el suelo (desplazamiento vertical).
    val bounceOffsetY by bounceTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                0f at 0 with LinearOutSlowInEasing          // arriba
                -30f at 720 with FastOutLinearInEasing       // cae acelerando
                -30f at 880                                  // contacto con el suelo
                0f at 1600 with LinearOutSlowInEasing        // sube desacelerando
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "bounceOffsetY"
    )

    // Escala de la sombra proyectada en el suelo (pequeña cuando la pelota
    // está arriba, grande al tocar el suelo).
    val shadowScale by bounceTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                0.72f at 0
                0.72f at 720
                1.05f at 800
                1.05f at 880
                0.72f at 1600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "shadowScale"
    )

    // Gradiente radial para dar volumen de esfera (highlight arriba-izquierda,
    // sombra hacia abajo-derecha). Gris cuando no hay internet.
    val sphereBrush = if (enabled) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF8A80), // destello claro (highlight)
                RedAlert,          // color base de la esfera
                RedAlertDark       // sombra profunda en el borde
            ),
            center = Offset(0.35f, 0.30f),
            radius = 1.4f
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFFBDBDBD),
                Color.Gray,
                Color(0xFF616161)
            ),
            center = Offset(0.35f, 0.30f),
            radius = 1.4f
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(220.dp)
            .scale(pressScale)
    ) {
        // Sombra elíptica en el "suelo".
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 20.dp)
                .graphicsLayer { scaleX = shadowScale }
                .size(width = 150.dp, height = 32.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        )

        // Esfera 3D con rebote.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    translationY = bounceOffsetY.dp.toPx()
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = {
                        // Feedback háptico al pulsar — I-010
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                )
                .shadow(
                    elevation = 18.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
                .background(sphereBrush, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.supervised_alert_button),
                modifier = Modifier.size(120.dp),
                tint = Color.White
            )
        }

        // M-002 (ciclo 6): barra diagonal roja superpuesta cuando no hay internet,
        // indicando que el botón ALERTA está inhabilitado.
        if (!enabled) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                val stroke = 18.dp.toPx()
                drawLine(
                    color = RedAlert,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                    strokeWidth = stroke,
                )
            }
        }
    }
}
