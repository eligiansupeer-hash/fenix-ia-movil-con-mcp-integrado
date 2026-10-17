@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "LongMethod", "MaxLineLength")

package com.fenix.mobilemcp.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityWindowInfo
import com.fenix.mobilemcp.telemetry.FenixBlackBoxRecorder
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class SplitWindowSlot(
    val windowId: Int,
    val title: String,
    val type: Int,
    val isActive: Boolean,
    val isFocused: Boolean,
    val packageName: String,
)

@Serializable
data class SplitScreenStatusResult(
    val success: Boolean,
    val actionExecuted: String,
    val isMultiWindowActive: Boolean,
    val forceResizableEnabled: Boolean,
    val visibleAppWindows: List<SplitWindowSlot>,
    val message: String,
)

/**
 * Controlador de Pantalla Dividida (Split-Screen / Multi-Window) bajo demanda para Agentes IA.
 * Permite forzar el modo de pantalla dividida mediante `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN`,
 * activar `force_resizable_activities` (cuando hay permiso `WRITE_SECURE_SETTINGS`) y lanzar
 * aplicaciones objetivo en ventana adyacente (`FLAG_ACTIVITY_LAUNCH_ADJACENT`) sin perder
 * de vista Fénix Mobile MCP.
 */
object SplitScreenController {

    private const val GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN_ID = 7

    private val blockedFinancialKeywords = listOf(
        "mercadopago",
        "uala",
        "brubank",
        "naranjax",
        "santander",
        "galicia",
        "bbva",
        "macro",
        "bna",
        "modo",
        "binance",
        "lemoncash",
        "belo",
        "ripio",
        "paypal",
    )

    fun isPackageBlockedByFirewall(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return blockedFinancialKeywords.any { lower.contains(it) }
    }

    suspend fun executeCommand(
        context: Context,
        action: String,
        targetPackage: String? = null,
        forceResizable: Boolean = true,
    ): SplitScreenStatusResult {
        val cleanAction = action.trim().lowercase()

        if (forceResizable) {
            tryEnableForceResizable(context)
        }

        return when (cleanAction) {
            "toggle" -> toggleSplitScreen(context)
            "launch_adjacent", "open_split", "force_split" -> {
                if (targetPackage.isNullOrBlank()) {
                    toggleSplitScreen(context)
                } else {
                    launchAppInSplitScreen(context, targetPackage.trim())
                }
            }
            "status" -> inspectSplitStatus(context, "status")
            else -> inspectSplitStatus(context, cleanAction)
        }
    }

    private suspend fun toggleSplitScreen(context: Context): SplitScreenStatusResult {
        val service = McpAccessibilityService.instance
            ?: return SplitScreenStatusResult(
                success = false,
                actionExecuted = "toggle",
                isMultiWindowActive = false,
                forceResizableEnabled = isForceResizableEnabled(context),
                visibleAppWindows = emptyList(),
                message = "El Servicio de Accesibilidad no está activo. Actívalo en Ajustes -> Permisos para controlar la pantalla dividida.",
            )

        val triggered = service.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN_ID)
        delay(450L)

        FenixBlackBoxRecorder.recordEvent(
            category = "SPLIT_SCREEN",
            action = "TOGGLE_SPLIT_SCREEN",
            component = "SplitScreenController",
            details = "Resultado performGlobalAction(SPLIT_SCREEN)=$triggered",
        )

        val status = inspectSplitStatus(context, "toggle")
        return status.copy(
            success = triggered,
            message = if (triggered) {
                "Pantalla dividida conmutada correctamente."
            } else {
                "El sistema Android rechazó conmutar pantalla dividida en la ventana actual."
            },
        )
    }

    private suspend fun launchAppInSplitScreen(
        context: Context,
        packageName: String,
    ): SplitScreenStatusResult {
        if (isPackageBlockedByFirewall(packageName)) {
            return SplitScreenStatusResult(
                success = false,
                actionExecuted = "launch_adjacent",
                isMultiWindowActive = false,
                forceResizableEnabled = isForceResizableEnabled(context),
                visibleAppWindows = emptyList(),
                message = "BLOQUEO DE SEGURIDAD BANCARIA: Prohibido abrir o manipular aplicaciones financieras ($packageName).",
            )
        }

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return SplitScreenStatusResult(
                success = false,
                actionExecuted = "launch_adjacent",
                isMultiWindowActive = false,
                forceResizableEnabled = isForceResizableEnabled(context),
                visibleAppWindows = emptyList(),
                message = "No se encontró una actividad de inicio para el paquete: $packageName",
            )

        val service = McpAccessibilityService.instance
        val beforeStatus = inspectSplitStatus(context, "pre_check")

        // Si aún no está en pantalla dividida y Accesibilidad está conectada, forzamos el modo dividido primero
        if (!beforeStatus.isMultiWindowActive && service != null) {
            service.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN_ID)
            delay(500L)
        }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
        )

        return try {
            context.startActivity(launchIntent)
            delay(600L)
            FenixBlackBoxRecorder.recordEvent(
                category = "SPLIT_SCREEN",
                action = "LAUNCH_ADJACENT",
                component = "SplitScreenController",
                details = "App lanzada en pantalla dividida: $packageName",
            )
            val afterStatus = inspectSplitStatus(context, "launch_adjacent")
            afterStatus.copy(
                success = true,
                message = "Aplicación '$packageName' lanzada en modo pantalla dividida (adyacente).",
            )
        } catch (e: Exception) {
            SplitScreenStatusResult(
                success = false,
                actionExecuted = "launch_adjacent",
                isMultiWindowActive = false,
                forceResizableEnabled = isForceResizableEnabled(context),
                visibleAppWindows = emptyList(),
                message = "Error al lanzar '$packageName' en pantalla dividida: ${e.message}",
            )
        }
    }

    fun inspectSplitStatus(context: Context, actionLabel: String = "status"): SplitScreenStatusResult {
        val service = McpAccessibilityService.instance
        val slots = mutableListOf<SplitWindowSlot>()

        if (service != null) {
            try {
                val windows = service.windows ?: emptyList()
                for (win in windows) {
                    if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
                        win.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER
                    ) {
                        val pkg = win.root?.packageName?.toString() ?: ""
                        slots.add(
                            SplitWindowSlot(
                                windowId = win.id,
                                title = win.title?.toString() ?: pkg,
                                type = win.type,
                                isActive = win.isActive,
                                isFocused = win.isFocused,
                                packageName = pkg,
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        val appWindowsCount = slots.count { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val hasDivider = slots.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }
        val isMulti = appWindowsCount >= 2 || hasDivider

        return SplitScreenStatusResult(
            success = true,
            actionExecuted = actionLabel,
            isMultiWindowActive = isMulti,
            forceResizableEnabled = isForceResizableEnabled(context),
            visibleAppWindows = slots,
            message = if (isMulti) {
                "Modo pantalla dividida ACTIVO ($appWindowsCount ventanas de aplicación visibles)."
            } else {
                "Modo pantalla única estándar ($appWindowsCount ventana activa)."
            },
        )
    }

    private fun tryEnableForceResizable(context: Context): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, "force_resizable_activities", 1)
            Settings.Global.putInt(context.contentResolver, "enable_freeform_support", 1)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isForceResizableEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "force_resizable_activities", 0) == 1
        } catch (_: Exception) {
            false
        }
    }
}

