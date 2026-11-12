package com.alertamayor.app.ui.navigation

import android.app.NotificationManager
import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alertamayor.app.AlertaMayorApp
import com.alertamayor.app.R
import com.alertamayor.app.service.NotificationHelper
import com.alertamayor.app.ui.AppViewModelFactory
import com.alertamayor.app.ui.call.CallScreen
import com.alertamayor.app.ui.call.CallViewModel
import com.alertamayor.app.ui.onboarding.ModeSelectionScreen
import com.alertamayor.app.ui.onboarding.OnboardingViewModel
import com.alertamayor.app.ui.qr.QrScannerScreen
import com.alertamayor.app.ui.settings.SettingsScreen
import com.alertamayor.app.ui.settings.SettingsViewModel
import com.alertamayor.app.ui.supervised.SupervisedHomeScreen
import com.alertamayor.app.ui.supervised.SupervisedHomeViewModel
import com.alertamayor.app.ui.supervisor.SupervisorHomeScreen
import com.alertamayor.app.ui.supervisor.SupervisorHomeViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val SUPERVISED_HOME = "supervisedHome"
    const val SUPERVISOR_HOME = "supervisorHome"
    const val SETTINGS = "settings"
    const val QR_SCANNER = "qrScanner"
    const val CALL = "call/{role}/{peerId}"

    fun call(role: String, peerId: String) = "call/$role/$peerId"
}

@Composable
fun AlertaMayorNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as AlertaMayorApp
    val container = app.container
    val factory = AppViewModelFactory(container)

    // I-003: el Supervisor recibe la notificación de llamada entrante y marca RINGING.
    container.callCoordinator.onIncomingCall = { peerName ->
        NotificationHelper.createChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NotificationHelper.NOTIFICATION_ID_INCOMING,
            NotificationHelper.buildIncomingCallNotification(context, peerName)
        )
    }

    val onboardingVm: OnboardingViewModel = viewModel(factory = factory)
    val onboardingDone by onboardingVm.onboardingDone.collectAsStateWithLifecycle()

    // Estado del diálogo de nombre editable tras escanear el QR — I-015
    var scannedDeviceId by remember { mutableStateOf<String?>(null) }
    var scannedName by remember { mutableStateOf("") }

    val startDestination = if (onboardingDone) Routes.SUPERVISED_HOME else Routes.ONBOARDING

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            ModeSelectionScreen(
                viewModel = onboardingVm,
                onModeSelected = { mode ->
                    when (mode) {
                        com.alertamayor.app.domain.model.AppMode.SUPERVISED ->
                            navController.navigate(Routes.SUPERVISED_HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        com.alertamayor.app.domain.model.AppMode.SUPERVISOR ->
                            navController.navigate(Routes.SUPERVISOR_HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                    }
                }
            )
        }

        composable(Routes.SUPERVISED_HOME) {
            val vm: SupervisedHomeViewModel = viewModel(factory = factory)
            SupervisedHomeScreen(
                viewModel = vm,
                onCall = { peerId ->
                    navController.navigate(Routes.call("supervised", peerId))
                },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SUPERVISOR_HOME) {
            val vm: SupervisorHomeViewModel = viewModel(factory = factory)
            SupervisorHomeScreen(
                viewModel = vm,
                onScanQr = { navController.navigate(Routes.QR_SCANNER) },
                onCall = { peerId ->
                    navController.navigate(Routes.call("supervisor", peerId))
                },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.QR_SCANNER) {
            val supervisorVm: SupervisorHomeViewModel = viewModel(factory = factory)
            QrScannerScreen(
                onScanned = { deviceId ->
                    // I-015: ofrecer nombre editable antes de persistir el vínculo
                    scannedDeviceId = deviceId
                    scannedName = deviceId
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CALL,
            arguments = listOf(
                navArgument("role") { type = NavType.StringType },
                navArgument("peerId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "supervised"
            val peerId = backStackEntry.arguments?.getString("peerId") ?: ""
            val vm: CallViewModel = viewModel(factory = factory)
            CallScreen(
                viewModel = vm,
                role = role,
                peerId = peerId,
                // R-001 (ciclo 4): tras colgar la llamada (o que termine por el otro lado),
                // volver SIEMPRE a la home del rol correspondiente. Nunca salir a la home
                // del sistema ni quedarse en otra pantalla.
                onHangUp = {
                    val homeRoute = if (role == "supervised") {
                        Routes.SUPERVISED_HOME
                    } else {
                        Routes.SUPERVISOR_HOME
                    }
                    val popped = navController.popBackStack(homeRoute, false)
                    if (!popped) {
                        navController.navigate(homeRoute) {
                            popUpTo(0) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }

    // Diálogo de nombre editable tras escanear el QR — I-015
    scannedDeviceId?.let { deviceId ->
        val supervisorVm: SupervisorHomeViewModel = viewModel(factory = factory)
        AlertDialog(
            onDismissRequest = { scannedDeviceId = null },
            title = { Text(stringResource(R.string.link_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = scannedName,
                    onValueChange = { scannedName = it },
                    label = { Text(stringResource(R.string.link_dialog_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val name = scannedName.trim().ifEmpty { deviceId }
                    supervisorVm.linkPeer(deviceId, name)
                    scannedDeviceId = null
                    navController.popBackStack()
                }) { Text(stringResource(R.string.settings_save)) }
            },
            dismissButton = {
                TextButton(onClick = { scannedDeviceId = null }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }
}
