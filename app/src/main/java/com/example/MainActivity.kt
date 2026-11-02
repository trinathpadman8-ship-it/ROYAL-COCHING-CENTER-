package com.example

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
          color = MaterialTheme.colorScheme.background
        ) {
          FeePortalWebView()
        }
      }
    }
  }
}

class WebAppInterface(private val context: Context) {
  @JavascriptInterface
  fun copyToClipboard(text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("UPI ID", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard: $text", Toast.LENGTH_SHORT).show()
  }

  @JavascriptInterface
  fun showToast(message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FeePortalWebView() {
  val context = LocalContext.current
  var webViewRef: WebView? = remember { null }

  BackHandler(enabled = webViewRef?.canGoBack() == true) {
    webViewRef?.goBack()
  }

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      WebView(ctx).apply {
        webViewRef = this
        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          databaseEnabled = true
          allowFileAccess = true
          useWideViewPort = true
          loadWithOverviewMode = true
          cacheMode = WebSettings.LOAD_DEFAULT
        }

        webChromeClient = WebChromeClient()
        addJavascriptInterface(WebAppInterface(ctx), "AndroidBridge")

        webViewClient = object : WebViewClient() {
          override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false

            if (url.startsWith("upi://") || url.startsWith("intent://")) {
              try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                if (intent.resolveActivity(ctx.packageManager) != null) {
                  ctx.startActivity(intent)
                } else {
                  val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                  if (fallbackUrl != null) {
                    view?.loadUrl(fallbackUrl)
                  } else {
                    Toast.makeText(ctx, "No compatible UPI app found. Please copy UPI ID to pay.", Toast.LENGTH_LONG).show()
                  }
                }
              } catch (e: Exception) {
                Toast.makeText(ctx, "Could not launch UPI app: ${e.message}", Toast.LENGTH_SHORT).show()
              }
              return true
            }

            if (url.startsWith("https://wa.me/") || url.startsWith("whatsapp://") ||
                url.startsWith("tel:") || url.startsWith("mailto:")) {
              try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                ctx.startActivity(intent)
              } catch (e: Exception) {
                Toast.makeText(ctx, "Could not open external app", Toast.LENGTH_SHORT).show()
              }
              return true
            }

            return false
          }
        }

        loadUrl("file:///android_asset/index.html")
      }
    }
  )
}
