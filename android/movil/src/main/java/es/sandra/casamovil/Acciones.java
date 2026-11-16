package es.sandra.casamovil;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Botones de la notificación: «Añadir a casa» y «No». */
public class Acciones extends BroadcastReceiver {

    static final String ANADIR = "es.sandra.casamovil.ANADIR";
    static final String DESCARTAR = "es.sandra.casamovil.DESCARTAR";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null) return;
        final long id = intent.getLongExtra("id", Long.MIN_VALUE);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel((int) id);
        if (!ANADIR.equals(intent.getAction()) || id == Long.MIN_VALUE) return;

        final PendingResult pendiente = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String mensaje;
                try {
                    mensaje = Revisor.anadirACasa(context, id);
                } catch (Exception e) {
                    mensaje = "No se ha podido añadir: " + e.getMessage();
                }
                Revisor.notificarResultado(context, id, mensaje);
                pendiente.finish();
            }
        }).start();
    }
}
