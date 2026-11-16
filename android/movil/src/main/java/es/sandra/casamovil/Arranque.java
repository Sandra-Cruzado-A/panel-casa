package es.sandra.casamovil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Vuelve a activar la vigilancia del calendario tras reiniciar o actualizar la app. */
public class Arranque extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Revisor.programar(context);
    }
}
