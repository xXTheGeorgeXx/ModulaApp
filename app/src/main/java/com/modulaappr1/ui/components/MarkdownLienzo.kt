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

private class ContentBridge(
    private val onHeightReady: (Int) -> Unit,
    private val onRenderComplete: () -> Unit
) {
    @JavascriptInterface
    fun reportHeight(px: Int) { onHeightReady(px) }

    @JavascriptInterface
    fun renderComplete() { onRenderComplete() }
}

private fun buildKatexHtml(markdownText: String): String {
    val escapedMarkdown = JSONObject.quote(markdownText)
    return """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<link rel="stylesheet" href="katex/katex.min.css">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }

  html, body {
    background-color: transparent;
    color: #F5F5F5;
    font-family: -apple-system, 'Segoe UI', Roboto, sans-serif;
    font-size: 15px;
    line-height: 1.65;
    word-break: break-word;
    overflow-x: hidden;
  }

  h1 { font-size: 1.5em; color: #FFFFFF; margin: 16px 0 8px; border-bottom: 1px solid #333; padding-bottom: 6px; }
  h2 { font-size: 1.3em; color: #EEEEEE; margin: 14px 0 6px; }
  h3 { font-size: 1.1em; color: #DDDDDD; margin: 12px 0 4px; }
  h4, h5, h6 { font-size: 1.0em; color: #CCCCCC; margin: 10px 0 4px; }
  p  { margin: 6px 0; }
  strong { color: #FFFFFF; font-weight: 700; }
  em     { color: #CCCCCC; font-style: italic; }

  ul, ol { margin: 6px 0; padding-left: 20px; }
  li { margin: 3px 0; }
  li::marker { color: #00E5C3; }

  code {
    background: #1E1E2E; color: #00E5C3;
    font-family: 'Courier New', monospace;
    font-size: 0.88em; padding: 2px 6px; border-radius: 4px;
  }
  pre {
    background: #1E1E2E; border: 1px solid #2A2A3E;
    border-left: 3px solid #00E5C3; border-radius: 6px;
    padding: 12px; margin: 10px 0;
    overflow-x: auto;                          /* scroll horizontal en código */
    -webkit-overflow-scrolling: touch;
  }
  pre code { background: transparent; color: #A8E6CF; padding: 0; font-size: 0.85em; }

  blockquote {
    border-left: 3px solid #7C4DFF; margin: 8px 0;
    padding: 6px 12px; background: #1A1A2E;
    border-radius: 0 6px 6px 0; color: #AAAACC; font-style: italic;
  }

  table { border-collapse: collapse; width: 100%; margin: 10px 0; font-size: 0.9em; }
  th { background: #1E1E2E; color: #00E5C3; padding: 8px 12px; border: 1px solid #333; font-weight: 600; }
  td { padding: 7px 12px; border: 1px solid #2A2A2A; color: #DDDDDD; }
  tr:nth-child(even) td { background: #111118; }

  hr { border: none; border-top: 1px solid #333; margin: 12px 0; }
  a  { color: #00E5C3; text-decoration: none; }

  /* ── FÓRMULAS KATEX ─────────────────────────────────────── */
  .katex-display {
    margin: 14px 0;
    overflow-x: auto;                          /* ← scroll horizontal fórmulas largas */
    overflow-y: hidden;
    -webkit-overflow-scrolling: touch;         /* ← scroll suave en Android */
    max-width: 100%;
    padding: 10px 4px;
    background: #111118;
    border-radius: 6px;
    border: 1px solid #1E1E2E;
  }
  .katex-display > .katex {
    display: block;
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 4px;
  }
  .katex { font-size: 1.05em; color: #F0F0FF; }
  .katex-error { color: #FF6B6B; font-family: monospace; font-size: 0.85em; }

  @keyframes fadeIn {
    from { opacity: 0; transform: translateY(3px); }
    to   { opacity: 1; transform: translateY(0); }
  }
  #content { animation: fadeIn 0.15s ease-out; }
</style>
</head>
<body>
<div id="content"></div>
<script src="marked/marked.min.js"></script>
<script src="katex/katex.min.js"></script>
<script src="katex/auto-render.min.js"></script>
<script>
(function() {
  try {
    marked.setOptions({ breaks: true, gfm: true, headerIds: false, mangle: false });
    var rawMarkdown = $escapedMarkdown;
    document.getElementById('content').innerHTML = marked.parse(rawMarkdown);
    renderMathInElement(document.getElementById('content'), {
      delimiters: [
        { left: '$$',  right: '$$',  display: true  },
        { left: '$',   right: '$',   display: false },
        { left: '\\[', right: '\\]', display: true  },
        { left: '\\(', right: '\\)', display: false }
      ],
      throwOnError: false,
      errorColor: '#FF6B6B',
      strict: false
    });
    requestAnimationFrame(function() {
      var h = document.getElementById('content').scrollHeight;
      if (window.AndroidBridge) {
        window.AndroidBridge.reportHeight(h);
        window.AndroidBridge.renderComplete();
      }
    });
  } catch (err) {
    document.getElementById('content').innerText = $escapedMarkdown;
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownLienzo(
    markdownText: String,
    modifier: Modifier = Modifier
) {
    var contentHeightPx by remember { mutableStateOf(0) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(markdownText) {
        contentHeightPx = 0
        webViewRef.value?.loadDataWithBaseURL(
            "file:///android_asset/",
            buildKatexHtml(markdownText),
            "text/html", "UTF-8", null
        )
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (contentHeightPx == 0) 24.dp else 0.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isScrollContainer            = false
                isVerticalScrollBarEnabled   = false
                isHorizontalScrollBarEnabled = true   // ← fórmulas largas scrolleables

                with(settings) {
                    javaScriptEnabled    = true
                    domStorageEnabled    = false
                    allowFileAccess      = true
                    allowContentAccess   = false
                    setSupportZoom(false)
                    builtInZoomControls  = false
                    displayZoomControls  = false
                    loadWithOverviewMode = true
                    useWideViewPort      = true
                    cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean = true

                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            "document.getElementById('content').scrollHeight"
                        ) { result ->
                            result?.toIntOrNull()?.let { px ->
                                if (px > 0 && contentHeightPx == 0) contentHeightPx = px
                            }
                        }
                    }
                }

                addJavascriptInterface(
                    ContentBridge(
                        onHeightReady    = { px -> if (px > 0) contentHeightPx = px },
                        onRenderComplete = { }
                    ),
                    "AndroidBridge"
                )

                loadDataWithBaseURL(
                    "file:///android_asset/",
                    buildKatexHtml(markdownText),
                    "text/html", "UTF-8", null
                )
                webViewRef.value = this
            }
        },
        update = { }
    )
}