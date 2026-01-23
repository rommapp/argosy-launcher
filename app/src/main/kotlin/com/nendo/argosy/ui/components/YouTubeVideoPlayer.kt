package com.nendo.argosy.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeVideoPlayer(
    videoId: String,
    muted: Boolean,
    onReady: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val processLifecycle = ProcessLifecycleOwner.get().lifecycle

    LaunchedEffect(muted) {
        val js = if (muted) "if(player && player.mute) player.mute();" else "if(player && player.unMute) player.unMute();"
        webView?.evaluateJavascript(js, null)
    }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    webView?.evaluateJavascript("if(player && player.stopVideo) player.stopVideo();", null)
                    webView?.onPause()
                }
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                    webView?.onResume()
                }
                else -> {}
            }
        }
        processLifecycle.addObserver(observer)
        onDispose {
            processLifecycle.removeObserver(observer)
            webView?.evaluateJavascript("if(player && player.destroy) player.destroy();", null)
            webView?.destroy()
        }
    }

    val muteValue = if (muted) 1 else 0
    val embedHtml = remember(videoId, muted) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body { width: 100%; height: 100%; background: transparent; overflow: hidden; }
                #player { position: fixed; top: 0; left: 0; width: 100%; height: 100%; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
                var player;
                var hasReportedReady = false;
                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        videoId: '$videoId',
                        playerVars: {
                            'autoplay': 1, 'controls': 0, 'disablekb': 1, 'fs': 0,
                            'modestbranding': 1, 'rel': 0, 'showinfo': 0, 'iv_load_policy': 3,
                            'playsinline': 1, 'mute': $muteValue, 'loop': 1, 'playlist': '$videoId'
                        },
                        events: {
                            'onReady': function(e) { e.target.playVideo(); },
                            'onStateChange': function(e) {
                                if (e.data == YT.PlayerState.PLAYING && !hasReportedReady) {
                                    hasReportedReady = true;
                                    Android.onVideoReady();
                                }
                            },
                            'onError': function(e) { Android.onVideoError(e.data); }
                        }
                    });
                }
                setTimeout(function() { if (!hasReportedReady) Android.onVideoError(-1); }, 15000);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onVideoReady() {
                        post { onReady() }
                    }
                    @android.webkit.JavascriptInterface
                    fun onVideoError(@Suppress("UNUSED_PARAMETER") code: Int) {
                        post { onError() }
                    }
                }, "Android")

                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView?, req: WebResourceRequest?, err: WebResourceError?) {
                        if (req?.isForMainFrame == true) onError()
                    }
                }
                webChromeClient = WebChromeClient()

                loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null)
                webView = this
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
