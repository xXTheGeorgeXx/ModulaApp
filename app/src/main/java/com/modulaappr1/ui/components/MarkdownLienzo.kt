package com.modulaappr1.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

// =====================================================================
// BRIDGE — Kotlin ↔ JavaScript para medir la altura del contenido
// =====================================================================

private class ContentBridge(
    private val onHeightReady: (Int) -> Unit,
    private val onRenderComplete: () -> Unit
) {
    @JavascriptInterface
    fun reportHeight(px: Int) {
        onHeightReady(px)
    }

    @JavascriptInterface
    fun renderComplete() {
        onRenderComplete()
    }
}

// =====================================================================
// BUILDER DEL HTML — Template completo con tema oscuro
// =====================================================================

private fun buildKatexHtml(markdownText: String): String {
    // Escapar el markdown como JSON string para pasarlo seguro a JS
    // Esto maneja: comillas, backslashes, saltos de línea, etc.
    val escapedMarkdown = JSONObject.quote(markdownText)

    return """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<link rel="stylesheet" href="katex/katex.min.css">
<style>
  /* ── RESET Y BASE ───────────────────────────────────────── */
  * {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
  }

  html, body {
    background-color: transparent;
    color: #F5F5F5;
    font-family: -apple-system, 'Segoe UI', Roboto, sans-serif;
    font-size: 15px;
    line-height: 1.65;
    word-break: break-word;
    overflow-x: hidden;
    padding: 0;
  }

  /* ── TIPOGRAFÍA ─────────────────────────────────────────── */
  h1 { font-size: 1.5em; color: #FFFFFF; margin: 16px 0 8px; border-bottom: 1px solid #333; padding-bottom: 6px; }
  h2 { font-size: 1.3em; color: #EEEEEE; margin: 14px 0 6px; }
  h3 { font-size: 1.1em; color: #DDDDDD; margin: 12px 0 4px; }
  h4, h5, h6 { font-size: 1.0em; color: #CCCCCC; margin: 10px 0 4px; }

  p { margin: 6px 0; }

  strong { color: #FFFFFF; font-weight: 700; }
  em     { color: #CCCCCC; font-style: italic; }

  /* ── LISTAS ─────────────────────────────────────────────── */
  ul, ol {
    margin: 6px 0;
    padding-left: 20px;
  }
  li {
    margin: 3px 0;
  }
  li::marker { color: #00E5C3; }

  /* ── CÓDIGO ─────────────────────────────────────────────── */
  code {
    background: #1E1E2E;
    color: #00E5C3;
    font-family: 'Courier New', monospace;
    font-size: 0.88em;
    padding: 2px 6px;
    border-radius: 4px;
  }

  pre {
    background: #1E1E2E;
    border: 1px solid #2A2A3E;
    border-left: 3px solid #00E5C3;
    border-radius: 6px;
    padding: 12px;
    margin: 10px 0;
    overflow-x: auto;
  }

  pre code {
    background: transparent;
    color: #A8E6CF;
    padding: 0;
    font-size: 0.85em;
    line-height: 1.5;
  }

  /* ── BLOCKQUOTE ─────────────────────────────────────────── */
  blockquote {
    border-left: 3px solid #7C4DFF;
    margin: 8px 0;
    padding: 6px 12px;
    background: #1A1A2E;
    border-radius: 0 6px 6px 0;
    color: #AAAACC;
    font-style: italic;
  }

  /* ── TABLAS ─────────────────────────────────────────────── */
  table {
    border-collapse: collapse;
    width: 100%;
    margin: 10px 0;
    font-size: 0.9em;
  }
  th {
    background: #1E1E2E;
    color: #00E5C3;
    padding: 8px 12px;
    text-align: left;
    border: 1px solid #333;
    font-weight: 600;
  }
  td {
    padding: 7px 12px;
    border: 1px solid #2A2A2A;
    color: #DDDDDD;
  }
  tr:nth-child(even) td { background: #111118; }
  tr:hover td { background: #1A1A28; }

  /* ── SEPARADOR ──────────────────────────────────────────── */
  hr {
    border: none;
    border-top: 1px solid #333;
    margin: 12px 0;
  }

  /* ── LINKS ──────────────────────────────────────────────── */
  a { color: #00E5C3; text-decoration: none; }
  a:hover { text-decoration: underline; }

  /* ── MATEMÁTICAS KaTeX ──────────────────────────────────── */

  /* Display math — bloque centrado */
  .katex-display {
    margin: 14px 0;
    overflow-x: auto;
    overflow-y: hidden;
    padding: 10px 4px;
    background: #111118;
    border-radius: 6px;
    border: 1px solid #1E1E2E;
  }

  /* Inline math — integrado en el texto */
  .katex {
    font-size: 1.05em;
    color: #F0F0FF;
  }

  /* Error de LaTeX — visible pero no rompe el layout */
  .katex-error {
    color: #FF6B6B;
    font-family: monospace;
    font-size: 0.85em;
  }

  /* ── ANIMACIÓN DE FADE-IN ───────────────────────────────── */
  @keyframes fadeIn {
    from { opacity: 0; transform: translateY(3px); }
    to   { opacity: 1; transform: translateY(0); }
  }
  #content {
    animation: fadeIn 0.15s ease-out;
  }
</style>
</head>
<body>
<div id="content"></div>

<!-- Carga desde assets locales — 100% offline -->
<script src="marked/marked.min.js"></script>
<script src="katex/katex.min.js"></script>
<script src="katex/auto-render.min.js"></script>

<script>
(function() {
  try {
    // Configurar marked.js
    marked.setOptions({
      breaks: true,        // Saltos de línea con \n simple
      gfm: true,           // GitHub Flavored Markdown
      headerIds: false,    // Sin IDs en headers (seguridad)
      mangle: false
    });

    // El markdown viene escapado como JSON string desde Kotlin
    var rawMarkdown = $escapedMarkdown;

    // Renderizar Markdown → HTML
    var htmlContent = marked.parse(rawMarkdown);
    document.getElementById('content').innerHTML = htmlContent;

    // Renderizar LaTeX con KaTeX auto-render
    renderMathInElement(document.getElementById('content'), {
      delimiters: [
        { left: '$$',  right: '$$',  display: true  },
        { left: '$',   right: '$',   display: false },
        { left: '\\[', right: '\\]', display: true  },
        { left: '\\(', right: '\\)', display: false }
      ],
      throwOnError: false,  // Nunca crashear — mostrar error inline
      errorColor: '#FF6B6B',
      strict: false,
      trust: false
    });

    // Reportar altura al componente Compose
    // Usamos requestAnimationFrame para asegurar que el DOM está pintado
    requestAnimationFrame(function() {
      var height = document.getElementById('content').scrollHeight;
      if (window.AndroidBridge) {
        window.AndroidBridge.reportHeight(height);
        window.AndroidBridge.renderComplete();
      }
    });

  } catch (err) {
    // Fallback: mostrar texto plano si algo falla
    document.getElementById('content').innerText = rawMarkdown;
    if (window.AndroidBridge) {
      window.AndroidBridge.reportHeight(document.body.scrollHeight);
      window.AndroidBridge.renderComplete();
    }
  }
})();
</script>
</body>
</html>
    """.trimIndent()
}

// =====================================================================
// COMPONENTE COMPOSE
// =====================================================================

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownLienzo(
    markdownText: String,
    modifier: Modifier = Modifier
) {
    // Altura dinámica — empieza en 0, JS reporta la real
    var contentHeightPx by remember { mutableStateOf(0) }
    var isRendered      by remember { mutableStateOf(false) }

    // Ref al WebView para poder llamar loadData cuando cambia el texto
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // Cuando el texto cambia, forzar re-render y resetear estado
    LaunchedEffect(markdownText) {
        isRendered      = false
        contentHeightPx = 0
        webViewRef.value?.let { wv ->
            val html = buildKatexHtml(markdownText)
            wv.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            // Altura mínima mientras carga, dinámica cuando JS reporta
            .heightIn(
                min = if (contentHeightPx == 0) 24.dp else 0.dp
            ),
        factory = { context ->
            WebView(context).apply {
                // ── Configuración del WebView ──────────────────────
                setBackgroundColor(Color.TRANSPARENT)
                isScrollContainer = false  // Compose maneja el scroll
                isVerticalScrollBarEnabled   = false
                isHorizontalScrollBarEnabled = false

                with(settings) {
                    javaScriptEnabled      = true
                    domStorageEnabled      = false  // No necesario
                    allowFileAccess        = true   // Para assets locales
                    allowContentAccess     = false  // Sin acceso a content://
                    setSupportZoom(false)
                    builtInZoomControls    = false
                    displayZoomControls    = false
                    loadWithOverviewMode   = true
                    useWideViewPort        = true
                    // Sin cache de red — todo viene de assets
                    cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                }

                // ── WebViewClient — bloquea navegación externa ─────
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true // Bloquear toda navegación

                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Backup: si JS no reportó, medir con evaluateJavascript
                        view?.evaluateJavascript(
                            "document.getElementById('content').scrollHeight"
                        ) { result ->
                            result?.toIntOrNull()?.let { px ->
                                if (px > 0 && contentHeightPx == 0) {
                                    contentHeightPx = px
                                }
                            }
                        }
                    }
                }

                // ── Bridge Kotlin ↔ JavaScript ─────────────────────
                addJavascriptInterface(
                    ContentBridge(
                        onHeightReady = { px ->
                            if (px > 0) contentHeightPx = px
                        },
                        onRenderComplete = {
                            isRendered = true
                        }
                    ),
                    "AndroidBridge"
                )

                // Cargar el HTML inicial
                val html = buildKatexHtml(markdownText)
                loadDataWithBaseURL(
                    "file:///android_asset/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )

                webViewRef.value = this
            }
        },
        update = { _ ->
            // El re-render lo maneja LaunchedEffect(markdownText)
            // No hacer nada aquí para evitar re-renders duplicados
        }
    )
}