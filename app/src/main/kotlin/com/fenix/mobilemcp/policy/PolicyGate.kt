package com.fenix.mobilemcp.policy

import android.content.Context

class PolicyGate(private val context: Context) {
    fun evaluate(action: UiAction): PolicyDecision {
        if (action.isEmergency) {
            return PolicyDecision.Deny(PolicyReasonCodes.EMERGENCY_LATCH)
        }

        if (action.pkg == null) {
            return PolicyDecision.Deny(PolicyReasonCodes.UNKNOWN_PACKAGE)
        }

        if (isPackageProtected(action.pkg) || action.isFinancial) {
            return PolicyDecision.Deny(PolicyReasonCodes.FINANCIAL_PACKAGE)
        }

        if (action.isSensitive) {
            return PolicyDecision.Deny(PolicyReasonCodes.SENSITIVE_WINDOW)
        }

        return PolicyDecision.Allow
    }

    private fun isPackageProtected(pkg: String): Boolean {
        // En una implementación real esto se leería del registry instalado (installed_apps_policy.json)
        val protectedPackages = setOf(
            "com.android.settings.password",
            "com.android.systemui",
            // Banca y billeteras por defecto
            "com.mercadopago.android",
            "ar.com.santander.rio.mbanking",
            "com.bbva.bbvacontigo",
            "com.paypal.android.p2pmobile",
            "com.binance.dev"
        )
        return pkg in protectedPackages
    }
}
