package com.nendo.argosy.ui.screens.stream

object StreamPageScripts {
    private const val STYLE_ID = "argosy-stream-chrome"

    private const val CHROME_CSS =
        "#root,#touch-gamepad-host,#dashboard-root,#dev-sidebar{display:none !important;}"

    private val FRAME_FUNCTIONS = """
        function each(w, fn) {
          try { fn(w); } catch (err) { return; }
          for (var i = 0; i < w.frames.length; i++) each(w.frames[i], fn);
        }
        function hideChrome(w) {
          var d = w.document;
          if (!d || d.getElementById('$STYLE_ID')) return;
          if (!d.querySelector('#app > .video-container')) return;
          var root = d.head || d.documentElement;
          if (!root) return;
          var s = d.createElement('style');
          s.id = '$STYLE_ID';
          s.textContent = '$CHROME_CSS';
          root.appendChild(s);
        }
        function drawing(w) {
          if ((w.videoChunksReceived || 0) > 0 && (w.fps || 0) > 0) return true;
          var videos = w.document.getElementsByTagName('video');
          for (var i = 0; i < videos.length; i++) {
            if (videos[i].videoWidth > 0 && videos[i].readyState >= 2) return true;
          }
          return false;
        }
    """.trimIndent()

    val HIDE_CHROME = """
        (function(){
        $FRAME_FUNCTIONS
        each(window, hideChrome);
        })();
    """.trimIndent()

    val PROBE = """
        (function(){
        $FRAME_FUNCTIONS
        var seen = false;
        each(window, function(w) {
          hideChrome(w);
          if (drawing(w)) seen = true;
        });
        return seen;
        })();
    """.trimIndent()
}
