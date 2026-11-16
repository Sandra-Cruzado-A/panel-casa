package es.sandra.casamovil;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

/** Se despierta cuando cambia el calendario del móvil (y cada 15 min como respaldo). */
public class Vigilante extends JobService {

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context c = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Revisor.revisar(c);
                } catch (Exception ignorado) {
                }
                jobFinished(params, false);
                // El aviso por cambios solo sirve una vez: se vuelve a programar
                Revisor.programar(c);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
