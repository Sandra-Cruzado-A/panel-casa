package es.sandra.panelcasa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Abre el panel cuando se enciende la tablet. */
public class Arranque extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Intent abrir = new Intent(context, MainActivity.class);
        abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(abrir);
        } catch (Exception ignorado) {
            // En Android 10 o posterior el sistema puede impedirlo; en una tablet de 2014 funciona
        }
    }
}
