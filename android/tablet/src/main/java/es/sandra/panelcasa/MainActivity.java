package es.sandra.panelcasa;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Panel de casa para la tablet: una pantalla completa con la web del panel.
 * - Carga la web publicada en GitHub Pages (así los cambios llegan sin reinstalar).
 * - Si no hay internet al arrancar, usa la copia que va dentro de la app y lo reintenta cada 30 min.
 * - Mantiene la pantalla encendida y oculta las barras del sistema.
 */
public class MainActivity extends Activity {

    static final String URL_WEB = "https://sandra-cruzado-a.github.io/panel-casa/";
    static final String URL_LOCAL = "file:///android_asset/index.html";
    private static final long REINTENTO_MS = 30L * 60L * 1000L;

    private WebView web;
    private boolean enLocal = false;
    private final Handler handler = new Handler();

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(0xFFEEF4EF);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new Puente(), "PanelNativo");
        web.setWebViewClient(new Cliente());
        cargarWeb();
    }

    private void cargarWeb() {
        enLocal = false;
        web.loadUrl(URL_WEB + "?app=tablet");
    }

    private void cargarLocal() {
        if (enLocal) return;
        enLocal = true;
        web.loadUrl(URL_LOCAL);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                cargarWeb();
            }
        }, REINTENTO_MS);
    }

    private class Cliente extends WebViewClient {
        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (failingUrl != null && failingUrl.startsWith(URL_WEB)) cargarLocal();
        }

        @TargetApi(23)
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame() && request.getUrl().toString().startsWith(URL_WEB)) cargarLocal();
        }

        @TargetApi(23)
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (request.isForMainFrame() && response.getStatusCode() >= 400) cargarLocal();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // Nunca se acepta un certificado no válido: se usa la copia interna
            handler.cancel();
            cargarLocal();
        }
    }

    /** Lo que la web puede pedir a la app: guardar sus ajustes en la tablet. */
    private class Puente {
        private final SharedPreferences prefs = getSharedPreferences("web", Context.MODE_PRIVATE);

        @JavascriptInterface
        public String leer(String clave) {
            return prefs.getString(clave, null);
        }

        @JavascriptInterface
        public void guardar(String clave, String valor) {
            if (valor == null) prefs.edit().remove(clave).apply();
            else prefs.edit().putString(clave, valor).apply();
        }

        @JavascriptInterface
        public String version() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "";
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) ocultarBarras();
    }

    @SuppressWarnings("deprecation")
    private void ocultarBarras() {
        int flags = View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= 19) {
            flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // El botón «atrás» cierra ventanas o vuelve a Inicio; la app no se cierra (es un panel fijo)
        String js = "window.panelAtras && window.panelAtras()";
        if (Build.VERSION.SDK_INT >= 19) web.evaluateJavascript(js, null);
        else web.loadUrl("javascript:" + js);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        ocultarBarras();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        web.destroy();
        super.onDestroy();
    }
}
