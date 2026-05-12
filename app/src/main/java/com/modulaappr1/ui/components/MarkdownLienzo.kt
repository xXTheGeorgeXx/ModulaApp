package com.modulaappr1.ui.components

import android.graphics.Color as AndroidColor
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.math.MathPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import io.noties.prism4j.annotations.PrismBundle

@Composable
fun MarkdownLienzo(markdownText: String) {
    val context = LocalContext.current

    // Configuramos el motor de renderizado una sola vez (remember) para no gastar recursos
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            // Activamos el renderizado de ecuaciones físicas/matemáticas (KaTeX/LaTeX)
            .usePlugin(MathPlugin.create())
            // Activamos las tablas
            .usePlugin(TablePlugin.create(context))
            // TODO en el futuro: Añadir el tema oscuro (Darkula) para SyntaxHighlightPlugin
            .build()
    }

    // Usamos AndroidView para incrustar el motor de texto avanzado dentro de Compose
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                // Configuramos los colores para que coincidan con tu tema Neural Quartz
                setTextColor(AndroidColor.parseColor("#F5F5F5")) // TextPrimary
                textSize = 16f
                setLineSpacing(0f, 1.2f) // Interlineado elegante
            }
        },
        update = { textView ->
            // Aquí es donde ocurre la magia: El texto crudo se convierte en visual puro en tiempo real
            markwon.setMarkdown(textView, markdownText)
        }
    )
}