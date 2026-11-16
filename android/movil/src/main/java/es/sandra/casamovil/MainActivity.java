package es.sandra.casamovil;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App «Casa» para los móviles: lista de la compra, agenda y cumpleaños (página web)
 * y, por debajo, el aviso «¿Añadir a la tablet de casa?» cuando se crea un evento.
 */
public class MainActivity extends Activity {

    static final String URL_WEB = "https://sandra-cruzado-a.github.io/panel-casa/movil.html";
    static final String URL_LOCAL = "file:///android_asset/movil.html";
    private static final int PEDIR_PERMISOS = 7;

    private WebView web;
    private boolean enLocal = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF1F5C3F);
        getWindow().setNavigationBarColor(0xFF1F5C3F);

        web = new WebView(this);
        web.setBackgroundColor(0xFFEEF4EF);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Puente(), "CasaNativo");
        web.setWebViewClient(new WebViewClient() {
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
        });
        web.loadUrl(URL_WEB);

        Revisor.crearCanal(this);
        Revisor.programar(this);
    }

    private void cargarLocal() {
        if (enLocal) return;
        enLocal = true;
        web.loadUrl(URL_LOCAL);
    }

    private void avisarWeb() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript("window.onNativo && window.onNativo()", null);
            }
        });
    }

    private void pedirPermisos() {
        if (Build.VERSION.SDK_INT < 23) {
            avisarWeb();
            return;
        }
        List<String> faltan = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            faltan.add(Manifest.permission.READ_CALENDAR);
        if (checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            faltan.add(Manifest.permission.WRITE_CALENDAR);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            faltan.add(Manifest.permission.POST_NOTIFICATIONS);
        if (faltan.isEmpty()) avisarWeb();
        else requestPermissions(faltan.toArray(new String[0]), PEDIR_PERMISOS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (Revisor.tienePermiso(this)) {
            Revisor.marcarInicio(this);
            Revisor.programar(this);
        }
        avisarWeb();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Lo que la página puede pedir a la app. */
    private class Puente {
        private final SharedPreferences prefsWeb = getSharedPreferences("web", Context.MODE_PRIVATE);

        @JavascriptInterface
        public String leer(String clave) {
            return prefsWeb.getString(clave, null);
        }

        @JavascriptInterface
        public void guardar(String clave, String valor) {
            if (valor == null) prefsWeb.edit().remove(clave).apply();
            else prefsWeb.edit().putString(clave, valor).apply();
        }

        @JavascriptInterface
        public String estado() {
            Context c = MainActivity.this;
            SharedPreferences p = Revisor.prefs(c);
            try {
                JSONObject o = new JSONObject();
                o.put("activo", p.getBoolean("activo", false));
                o.put("cuentaCasa", p.getString("cuentaCasa", ""));
                o.put("modo", p.getString("modo", "invitar"));
                JSONArray vig = new JSONArray();
                for (String id : p.getStringSet("vigilar", new HashSet<String>())) vig.put(id);
                o.put("vigilar", vig);
                o.put("permisoCalendario", Revisor.tienePermiso(c));
                o.put("permisoNotificaciones", Revisor.puedeNotificar(c));
                JSONArray cals = new JSONArray();
                if (Revisor.tienePermiso(c)) {
                    for (Revisor.Cal k : Revisor.calendarios(c).values()) {
                        JSONObject j = new JSONObject();
                        j.put("id", String.valueOf(k.id));
                        j.put("nombre", k.nombre);
                        j.put("cuenta", k.cuenta);
                        j.put("dueno", k.dueno);
                        j.put("color", String.format("#%06X", 0xFFFFFF & k.color));
                        j.put("propio", k.acceso >= 700);
                        j.put("escribible", k.acceso >= 500);
                        cals.put(j);
                    }
                }
                o.put("calendarios", cals);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void configurar(String json) {
            try {
                JSONObject o = new JSONObject(json);
                Set<String> vig = new HashSet<>();
                JSONArray a = o.optJSONArray("vigilar");
                if (a != null) for (int i = 0; i < a.length(); i++) vig.add(a.getString(i));
                Revisor.prefs(MainActivity.this).edit()
                        .putBoolean("activo", o.optBoolean("activo", false))
                        .putString("cuentaCasa", o.optString("cuentaCasa", "").trim())
                        .putString("modo", o.optString("modo", "invitar"))
                        .putStringSet("vigilar", vig)
                        .apply();
                if (Revisor.tienePermiso(MainActivity.this)) {
                    Revisor.marcarInicio(MainActivity.this);
                    Revisor.programar(MainActivity.this);
                }
            } catch (Exception ignorado) {
            }
        }

        @JavascriptInterface
        public void pedirPermisos() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.pedirPermisos();
                }
            });
        }

        @JavascriptInterface
        public void probarAviso() {
            Revisor.avisoPrueba(MainActivity.this);
        }
    }
}
