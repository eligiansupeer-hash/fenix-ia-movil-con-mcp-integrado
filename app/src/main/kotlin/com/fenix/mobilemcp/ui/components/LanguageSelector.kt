@file:Suppress("FunctionNaming")

package com.fenix.mobilemcp.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fenix.mobilemcp.R
import com.fenix.mobilemcp.utils.LocaleHelper

/**
 * Selector de idioma compartido: Sistema / Español / English.
 *
 * Se usa en Ajustes → General y también en la pantalla Inicio, así la usuaria cambia el
 * idioma sin salir de ahí. Los tres rótulos salen de resources (sin hardcode) y se
 * recrea la Activity para que los recursos se recarguen al instante.
 */
@Composable
fun LanguageSelector(
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val context = LocalContext.current
    var lang by remember { mutableStateOf(LocaleHelper.current(context)) }
    val options =
        listOf(
            "" to stringResource(R.string.language_option_system),
            "es" to stringResource(R.string.language_option_spanish),
            "en" to stringResource(R.string.language_option_english),
        )

    if (showLabel) {
        Text(
            text = stringResource(R.string.settings_language_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }

    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, (code, label) ->
            SegmentedButton(
                selected = code == lang,
                onClick = {
                    lang = code
                    LocaleHelper.set(context, code)
                    (context as? Activity)?.recreate()
                },
                shape =
                    SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                    ),
            ) {
                Text(label)
            }
        }
    }
}
