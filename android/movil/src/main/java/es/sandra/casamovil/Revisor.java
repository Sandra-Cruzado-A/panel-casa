package es.sandra.casamovil;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Lógica del aviso: detectar eventos nuevos en el calendario del móvil, preguntar con
 * una notificación y, si se acepta, añadirlos a la cuenta / calendario de casa.
 */
final class Revisor {

    static final String PREFS = "avisos";
    static final String CANAL = "eventos-casa";
    static final int JOB_CAMBIOS = 1;
    static final int JOB_PERIODICO = 2;
    private static final int MAX_AVISOS_SEGUIDOS = 8;   // más que esto = sincronización masiva: no se avisa
    private static final Locale ES = new Locale("es", "ES");

    private Revisor() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean tienePermiso(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean puedeNotificar(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return Build.VERSION.SDK_INT < 24 || nm == null || nm.areNotificationsEnabled();
    }

    /* ---------------------------- programación ---------------------------- */

    static void programar(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        ComponentName comp = new ComponentName(c, Vigilante.class);
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                // Se dispara cuando cambia algo en el calendario del móvil
                JobInfo.Builder b = new JobInfo.Builder(JOB_CAMBIOS, comp)
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(Events.CONTENT_URI,
                                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                        .setTriggerContentUpdateDelay(3000)
                        .setTriggerContentMaxDelay(30000);
                js.schedule(b.build());
            }
            boolean hayPeriodico = false;
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_PERIODICO) hayPeriodico = true;
            if (!hayPeriodico) {
                // Respaldo cada 15 min (sobrevive a reinicios)
                JobInfo.Builder p = new JobInfo.Builder(JOB_PERIODICO, comp).setPersisted(true);
                if (Build.VERSION.SDK_INT >= 24) p.setPeriodic(15L * 60L * 1000L, 5L * 60L * 1000L);
                else p.setPeriodic(15L * 60L * 1000L);
                js.schedule(p.build());
            }
        } catch (Exception ignorado) {
        }
    }

    /** La primera vez solo se apunta hasta dónde llega el calendario: no se pregunta por lo que ya había. */
    static void marcarInicio(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("ultimoId")) p.edit().putLong("ultimoId", maxId(c)).apply();
    }

    private static long maxId(Context c) {
        long max = 0;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Events.CONTENT_URI, new String[]{Events._ID}, null, null, null);
            while (cur != null && cur.moveToNext()) max = Math.max(max, cur.getLong(0));
        } catch (Exception ignorado) {
        } finally {
            if (cur != null) cur.close();
        }
        return max;
    }

    /* ------------------------------ calendarios --------------------------- */

    static final class Cal {
        long id;
        String nombre, cuenta, tipo, dueno;
        int acceso, color;
    }

    static Map<Long, Cal> calendarios(Context c) {
        Map<Long, Cal> mapa = new LinkedHashMap<>();
        String[] proy = {Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
                Calendars.OWNER_ACCOUNT, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CALENDAR_COLOR};
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Calendars.CONTENT_URI, proy, null, null, Calendars.ACCOUNT_NAME + " ASC");
            while (cur != null && cur.moveToNext()) {
                Cal k = new Cal();
                k.id = cur.getLong(0);
                k.nombre = texto(cur.getString(1));
                k.cuenta = texto(cur.getString(2));
                k.tipo = texto(cur.getString(3));
                k.dueno = texto(cur.getString(4));
                k.acceso = cur.getInt(5);
                k.color = cur.getInt(6);
                mapa.put(k.id, k);
            }
        } catch (Exception ignorado) {
        } finally {
            if (cur != null) cur.close();
        }
        return mapa;
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }

    private static boolean esCasa(Cal k, String casa) {
        return !casa.isEmpty() && (casa.equalsIgnoreCase(k.dueno) || casa.equalsIgnoreCase(k.cuenta) && k.acceso >= Calendars.CAL_ACCESS_OWNER);
    }

    /* -------------------------- revisar eventos nuevos --------------------- */

    static void revisar(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("activo", false) || !tienePermiso(c)) return;
        long ultimo = p.getLong("ultimoId", -1);
        if (ultimo < 0) {
            p.edit().putLong("ultimoId", maxId(c)).apply();
            return;
        }
        String casa = p.getString("cuentaCasa", "").trim();
        Set<String> vigilar = p.getStringSet("vigilar", new HashSet<String>());
        Map<Long, Cal> cals = calendarios(c);

        String[] proy = {Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DTSTART, Events.DTEND,
                Events.ALL_DAY, Events.ORGANIZER, Events.RRULE, Events.ORIGINAL_ID};
        List<long[]> candidatos = new ArrayList<>();
        List<String> titulos = new ArrayList<>();
        long maxVisto = ultimo;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Events.CONTENT_URI, proy,
                    Events._ID + " > ? AND " + Events.DELETED + " = 0", new String[]{String.valueOf(ultimo)}, Events._ID + " ASC");
            long ahora = System.currentTimeMillis();
            while (cur != null && cur.moveToNext()) {
                long id = cur.getLong(0);
                maxVisto = Math.max(maxVisto, id);
                Cal k = cals.get(cur.getLong(1));
                if (k == null || esCasa(k, casa)) continue;
                if (!cur.isNull(8)) continue;                                // cambio de una repetición, no es nuevo
                if (vigilar.isEmpty()) {
                    if (k.acceso < Calendars.CAL_ACCESS_OWNER) continue;   // solo calendarios propios
                } else if (!vigilar.contains(String.valueOf(k.id))) continue;
                String organizador = texto(cur.getString(6));
                if (!organizador.isEmpty() && !k.dueno.isEmpty() && !organizador.equalsIgnoreCase(k.dueno)) continue; // invitación de otra persona
                String titulo = texto(cur.getString(2)).trim();
                if (titulo.isEmpty()) continue;
                long ini = cur.getLong(3), fin = cur.isNull(4) ? ini : cur.getLong(4);
                boolean repite = !texto(cur.getString(7)).isEmpty();
                if (!repite && fin < ahora - 6L * 3600L * 1000L) continue;     // eventos ya pasados
                if (!casa.isEmpty() && yaInvitada(c, id, casa)) continue;
                candidatos.add(new long[]{id, ini, cur.getInt(5), k.id});
                titulos.add(titulo);
            }
        } catch (Exception ignorado) {
        } finally {
            if (cur != null) cur.close();
        }
        p.edit().putLong("ultimoId", maxVisto).apply();
        if (candidatos.size() > MAX_AVISOS_SEGUIDOS) return;   // p. ej. al añadir una cuenta nueva al móvil
        for (int i = 0; i < candidatos.size(); i++) {
            long[] e = candidatos.get(i);
            Cal k = cals.get(e[3]);
            notificar(c, e[0], titulos.get(i), e[1], e[2] == 1, k != null ? k.nombre : "");
        }
    }

    private static boolean yaInvitada(Context c, long eventoId, String correo) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Attendees.CONTENT_URI, new String[]{Attendees.ATTENDEE_EMAIL},
                    Attendees.EVENT_ID + " = ?", new String[]{String.valueOf(eventoId)}, null);
            while (cur != null && cur.moveToNext()) if (correo.equalsIgnoreCase(cur.getString(0))) return true;
        } catch (Exception ignorado) {
        } finally {
            if (cur != null) cur.close();
        }
        return false;
    }

    /* ------------------------------ notificaciones ------------------------- */

    static void crearCanal(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel canal = new NotificationChannel(CANAL, "Eventos para la tablet de casa", NotificationManager.IMPORTANCE_DEFAULT);
        canal.setDescription("Pregunta si quieres añadir a casa los eventos que creas");
        nm.createNotificationChannel(canal);
    }

    private static Notification.Builder constructor(Context c) {
        crearCanal(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CANAL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_aviso).setColor(0xFF1F5C3F);
        return b;
    }

    private static int banderas() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    static String describir(long ini, boolean todoElDia) {
        SimpleDateFormat f = new SimpleDateFormat(todoElDia ? "EEEE d 'de' MMMM" : "EEEE d 'de' MMMM, HH:mm", ES);
        if (todoElDia) f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ini));
    }

    @SuppressWarnings("deprecation")
    static void notificar(Context c, long id, String titulo, long ini, boolean todoElDia, String calendario) {
        if (!puedeNotificar(c)) return;
        Intent anadir = new Intent(c, Acciones.class).setAction(Acciones.ANADIR).putExtra("id", id);
        Intent no = new Intent(c, Acciones.class).setAction(Acciones.DESCARTAR).putExtra("id", id);
        PendingIntent pAnadir = PendingIntent.getBroadcast(c, (int) (id * 2), anadir, banderas());
        PendingIntent pNo = PendingIntent.getBroadcast(c, (int) (id * 2 + 1), no, banderas());
        PendingIntent abrir = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), banderas());
        String cuando = describir(ini, todoElDia);
        Notification.Builder b = constructor(c)
                .setContentTitle("¿Añadir a la tablet de casa?")
                .setContentText(titulo + " · " + cuando)
                .setStyle(new Notification.BigTextStyle().bigText(titulo + "\n" + cuando + (calendario.isEmpty() ? "" : " · " + calendario)))
                .setAutoCancel(true)
                .setContentIntent(abrir)
                .addAction(0, "Añadir a casa", pAnadir)
                .addAction(0, "No", pNo);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int) id, b.build());
    }

    static void notificarResultado(Context c, long id, String mensaje) {
        Notification.Builder b = constructor(c).setContentTitle("Casa").setContentText(mensaje).setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(8000);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int) id, b.build());
    }

    static void avisoPrueba(Context c) {
        notificar(c, -1, "Dentista (ejemplo)", System.currentTimeMillis() + 86400000L, false, "Prueba");
    }

    /* ----------------------------- añadir a casa --------------------------- */

    static String anadirACasa(Context c, long id) {
        if (id < 0) return "Era una prueba. Con tus eventos de verdad se añadirá a casa.";
        SharedPreferences p = prefs(c);
        String casa = p.getString("cuentaCasa", "").trim();
        String modo = p.getString("modo", "invitar");
        if (casa.isEmpty()) return "Falta la cuenta de casa en Ajustes";
        if (!tienePermiso(c)) return "Falta el permiso del calendario";

        ContentResolver cr = c.getContentResolver();
        String[] proy = {Events.CALENDAR_ID, Events.TITLE, Events.DTSTART, Events.DTEND, Events.DURATION, Events.ALL_DAY,
                Events.RRULE, Events.EVENT_TIMEZONE, Events.EVENT_LOCATION, Events.DESCRIPTION};
        Cursor cur = cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, id), proy, null, null, null);
        if (cur == null) return "No se puede leer el evento";
        if (!cur.moveToFirst()) {
            cur.close();
            return "El evento ya no existe";
        }
        long calId = cur.getLong(0);
        String titulo = texto(cur.getString(1));
        long ini = cur.getLong(2);
        long fin = cur.isNull(3) ? 0 : cur.getLong(3);
        String duracion = cur.getString(4);
        boolean todo = cur.getInt(5) == 1;
        String rrule = cur.getString(6);
        String zona = cur.getString(7);
        String lugar = cur.getString(8);
        String desc = cur.getString(9);
        cur.close();

        Map<Long, Cal> cals = calendarios(c);
        Cal origen = cals.get(calId);

        // 1) Invitar a la cuenta de casa: el evento sigue siendo uno solo (los cambios llegan a la tablet)
        if ("invitar".equals(modo) && origen != null && "com.google".equals(origen.tipo)) {
            if (yaInvitada(c, id, casa)) return "Ya estaba en el calendario de casa";
            ContentValues v = new ContentValues();
            v.put(Attendees.EVENT_ID, id);
            v.put(Attendees.ATTENDEE_EMAIL, casa);
            v.put(Attendees.ATTENDEE_NAME, "Casa");
            v.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE);
            v.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED);
            v.put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_INVITED);
            cr.insert(Attendees.CONTENT_URI, v);
            ContentValues ev = new ContentValues();
            ev.put(Events.HAS_ATTENDEE_DATA, 1);
            cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), ev, null, null);
            return "Añadido «" + titulo + "». La tablet lo verá en unos minutos.";
        }

        // 2) Copiarlo en el calendario de casa (tiene que estar compartido con este móvil)
        Cal destino = null;
        for (Cal k : cals.values()) if (esCasa(k, casa) && k.acceso >= Calendars.CAL_ACCESS_CONTRIBUTOR) destino = k;
        if (destino == null) {
            return "No encuentro el calendario de casa en este móvil. Pide que te lo compartan con permiso para hacer cambios, o elige «Invitar».";
        }
        ContentValues v = new ContentValues();
        v.put(Events.CALENDAR_ID, destino.id);
        v.put(Events.TITLE, titulo);
        v.put(Events.DTSTART, ini);
        if (rrule != null && !rrule.isEmpty()) {
            v.put(Events.RRULE, rrule);
            v.put(Events.DURATION, duracion != null && !duracion.isEmpty() ? duracion : "P" + Math.max(60, (fin - ini) / 1000) + "S");
        } else {
            v.put(Events.DTEND, fin > ini ? fin : ini + 3600000L);
        }
        v.put(Events.ALL_DAY, todo ? 1 : 0);
        v.put(Events.EVENT_TIMEZONE, todo ? "UTC" : (zona != null && !zona.isEmpty() ? zona : TimeZone.getDefault().getID()));
        if (lugar != null) v.put(Events.EVENT_LOCATION, lugar);
        if (desc != null) v.put(Events.DESCRIPTION, desc);
        cr.insert(Events.CONTENT_URI, v);
        return "Copiado «" + titulo + "» al calendario de casa.";
    }
}
