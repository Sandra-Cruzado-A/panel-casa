# Panel de casa · v3

Un panel para la tablet vieja de casa y una app para los móviles de la familia. Diseño en verde, títulos en **Lexend** y texto en **Nunito Sans** (las letras van dentro de la carpeta `fuentes/`, así que funcionan también sin internet).

**En la tablet** (app instalable, a pantalla completa), con pestañas a la izquierda:

- **Inicio:**
  - arriba a la izquierda la hora y a la derecha el tiempo en Teruel (con avisos de helada, nieve, viento fuerte o lluvia);
  - la franja **Toda la familia** con los planes de todos y los cumpleaños de hoy y mañana;
  - cuatro columnas: **Papá** (azul oscuro), **Mamá** (morado), **Sandra** (amarillo) y **Óscar** (verde), con lo de hoy y lo próximo de cada uno;
  - el **tablón de mensajes** con las últimas notas.
- **Tiempo:** ahora, próximas horas, 7 días y los avisos de las próximas 24 horas.
- **Gasolina:** gasolina 95 y gasóleo en Teruel, de más barata a más cara, con favoritas ★.
- **Compra:** la lista compartida, **ordenada por secciones del súper**.
- **Calendario:** el calendario principal de casa en vista de mes; desde aquí se añaden eventos y cumpleaños.
- **Volver a Inicio:** desde cualquier pestaña, desliza hacia arriba desde la barrita de abajo. Si nadie la toca, vuelve sola a los 3 minutos.
- **Modo noche** de 00:00 a 6:30: pantalla negra con el reloj y un resumen de mañana.

**En los móviles Android** (app «Casa»):

- Compra por secciones, agenda de casa con los colores de cada uno, tablón y cumpleaños.
- **+ Evento** para apuntar algo en el calendario de casa para una o varias personas, o para toda la familia.
- **Aviso automático:** cuando creas un evento en tu calendario, sale una notificación *«¿Añadir a la tablet de casa?»*. Con un toque queda en el calendario de casa.

**En iPhone:** la misma página, guardada en la pantalla de inicio. El aviso automático no es posible en iPhone (ver el paso 5).

## Cómo funciona

```
 Tablet (app)  ─┐                         ┌─ Calendario de casa (Google)
 Móviles (app) ─┼──▶  Apps Script  ───────┼─ Hoja «Panel de casa – listas» (compra, cumpleaños, tablón)
 iPhone (web)  ─┘   (cuenta de casa)      ├─ Open-Meteo (tiempo)
                                          └─ Ministerio (gasolineras)
```

- Las apps cargan la web publicada en GitHub Pages, así que **los cambios llegan sin reinstalar** (igual que en Discos). Si arrancan sin internet, usan una copia que llevan dentro.
- Los datos de la familia solo pasan por el Apps Script, cuya URL no se publica en ningún sitio. Ni la web ni las apps contienen datos vuestros.

---

## Paso 1 · La cuenta de Google de casa

Recomendado: crear una cuenta de Gmail solo para la casa (por ejemplo `casa.apellido@gmail.com`). Será «la cuenta de la tablet»:

- su calendario es **el calendario principal de casa**;
- en ella van el Apps Script y la hoja de la compra;
- cuando alguien pulsa «Añadir a casa» en el móvil, el evento se comparte con esta cuenta.

Después, en Google Calendar con la cuenta de casa: *Configuración → (el calendario) → Compartir con determinadas personas* → añade el correo de cada uno con **«Hacer cambios en eventos»**. Así cada uno ve el calendario de casa en su móvil y puede apuntar cosas directamente en él.

> ¿Prefieres no crear otra cuenta? Pon `CALENDARIO_CASA: ''` en `Code.gs` y el script creará un calendario «Casa» en tu propia cuenta. En ese caso, en la app del móvil elige **«Copiar en el calendario de casa»**.

## Paso 2 · El Apps Script (con la cuenta de casa)

1. [script.google.com](https://script.google.com) → **Nuevo proyecto** → nombre «Panel de casa – datos».
2. Pega todo `Code.gs`.
3. Arriba, en `PERSONAS`, ya están los cuatro con sus colores. Solo falta **el correo con el que cada uno usa Google Calendar** (sirve para saber de quién es cada evento):
   ```js
   PERSONAS: [
     { nombre: 'Papá',   color: '#1F3A68', email: 'correo.de.papa@gmail.com' },
     { nombre: 'Mamá',   color: '#6B3FA0', email: 'correo.de.mama@gmail.com' },
     { nombre: 'Sandra', color: '#E9B824', email: 'correo.de.sandra@gmail.com' },
     { nombre: 'Óscar',  color: '#3A9D5D', email: 'correo.de.oscar@gmail.com' }
   ],
   ```
   - El orden de la lista es el orden de las columnas en la tablet.
   - `NOMBRE_FAMILIA` y `COLOR_FAMILIA` son los de la franja «Toda la familia».
4. *Configuración del proyecto* → zona horaria **Europe/Madrid**.
5. Elige la función **`instalar`** → **Ejecutar** → acepta los permisos (calendario, hojas de cálculo y servicios externos). Si sale «Google no ha verificado esta aplicación»: *Configuración avanzada → Ir a Panel de casa – datos*. En el registro verás el ID del calendario de casa y el enlace a la hoja con las pestañas *Compra*, *Cumpleaños* y *Tablón*.
6. **Implementar → Nueva implementación → Aplicación web**. Ejecutar como **Yo**; quién tiene acceso: **Cualquier usuario**. Copia la URL (acaba en `/exec`).

> Si más adelante cambias el código: *Implementar → Gestionar implementaciones → lápiz → Versión: nueva versión*. La URL se mantiene.
>
> **Si vienes de la v2:** pega el `Code.gs` nuevo, vuelve a ejecutar **`instalar`** (crea la pestaña *Tablón* y añade la columna *sección* a la compra) y publica una nueva versión como se indica arriba.

## Paso 3 · GitHub (web + compilación de las apps)

1. Crea el repositorio **`panel-casa`**, público y en minúsculas (las apps llevan esa dirección escrita por dentro).
2. Sube todo **manteniendo las carpetas**:
   ```
   index.html  movil.html  manifest.webmanifest  manifest-movil.webmanifest
   icon-192.png  icon-512.png  icon-movil-192.png  icon-movil-512.png  README.md
   fuentes/            (carpeta entera: las letras Lexend y Nunito Sans)
   android/            (carpeta entera)
   .github/workflows/apps.yml
   ```
   - Descomprime el zip **antes** de arrastrar; si arrastras desde dentro del zip, se pierden las subcarpetas.
   - La carpeta `.github` está oculta en Windows. Lo más fácil: en GitHub, *Add file → Create new file*, escribe como nombre `.github/workflows/apps.yml` y pega su contenido.
3. *Settings → Pages → Branch: main / (root) → Save*.
4. *Settings → Secrets and variables → Actions → New repository secret*: crea **`KEYSTORE_PASSWORD`** y **`KEYSTORE_BASE64`** con lo que hay en `secretos-github.txt` (ese archivo no se sube). Es la firma de las apps: con ella, las versiones nuevas se instalan encima sin desinstalar.
5. *Actions → Compilar apps → Run workflow*. En unos 5 minutos aparecen en **Releases** los dos APK:
   - `panel-casa-tablet.apk`
   - `casa-movil.apk`

Enlace directo para descargarlos siempre en su última versión:
`https://github.com/Sandra-Cruzado-A/panel-casa/releases/latest`

## Paso 4 · La tablet

1. **Fecha y hora automáticas**: si la tablet tiene la hora mal, no carga nada por internet.
2. *Ajustes → Seguridad →* activa **Orígenes desconocidos**.
3. En la tablet, abre el enlace de Releases, descarga **`panel-casa-tablet.apk`** e instálalo.
4. Abre «Panel de casa» → icono de ajustes (abajo del todo en la barra de la izquierda) → pega la URL del Apps Script (mándatela por correo a la tablet para copiarla y pegarla).
5. Déjala enchufada. La app ya se encarga de:
   - mantener la pantalla encendida;
   - ir a pantalla completa;
   - abrirse sola al encender la tablet.

> **Batería:** una tablet de 2014 siempre al 100 % puede hincharse con los años. Revisa de vez en cuando que no se abombe; un enchufe con temporizador que la cargue unas horas al día le alarga la vida.

## Paso 5 · Los móviles

### Android (app «Casa»)

1. Instala **`casa-movil.apk`** desde el mismo enlace de Releases.
2. Ábrela → **Ajustes**:
   - pega la URL del Apps Script y pulsa *Guardar* (debajo saldrá «Conectado»);
   - elige **¿Quién eres?** y vuelve a pulsar *Guardar* (sirve para firmar la compra y las notas del tablón, y como persona por defecto en los eventos).
3. En **Avisos de eventos nuevos**:
   - **Dar permisos** (calendario y notificaciones);
   - activa **Preguntarme cada vez**;
   - marca qué calendarios vigilar (desmarca, por ejemplo, el del trabajo);
   - **Guardar avisos** → **Probar aviso**.
4. Si los avisos tardan o no llegan: *Ajustes del móvil → Apps → Casa → Batería →* **Sin restricciones**.

Cómo se usa: creas un evento en Google Calendar como siempre → te llega la notificación → **Añadir a casa**. El evento se comparte con la cuenta de casa y la tablet lo muestra en unos minutos. Si luego le cambias la hora en tu calendario, también cambia en la tablet. No hace falta abrir la app «Casa».

### iPhone

En Safari, abre `https://sandra-cruzado-a.github.io/panel-casa/movil.html` → *Compartir → Añadir a pantalla de inicio*. Ábrela desde el icono nuevo (guarda sus ajustes aparte de Safari) → *Ajustes* → pega la URL del Apps Script → *Guardar* → elige **¿Quién eres?** → *Guardar*.

Tiene la compra, la agenda, el tablón, los cumpleaños y el botón **+ Evento**. iOS no deja a una app vigilar el calendario en segundo plano, así que en iPhone el aviso automático no existe. Las alternativas son:

- crear el evento directamente en el calendario de casa (está compartido contigo);
- o añadir la cuenta de casa como invitada al evento.

---

## Uso diario

- **De quién es cada evento** (en qué columna sale):
  - si se apuntó desde la tablet o el móvil con **Para quién**, sale en la columna de esas personas;
  - si no, en la de quien lo creó (por su correo de `PERSONAS`; por eso conviene rellenarlos);
  - si no se sabe de quién es, o es para los cuatro, sale en la franja **Toda la familia**.
- **Lista de la compra:**
  - se apunta desde la tablet o desde los móviles, y cada producto se coloca solo en su sección (fruta y verdura, panadería, carne y pescado, lácteos y huevos, despensa, congelados, bebidas, limpieza, higiene y farmacia, otros);
  - si la sección no es la buena, toca la correcta antes de pulsar **Añadir**: la próxima vez ese producto ya irá ahí, en todos los dispositivos;
  - al tocar un producto se marca como comprado; lo comprado desaparece solo al día siguiente;
  - debajo del cuadro de texto salen como sugerencia las cosas que más se apuntan.
- **Tablón:** notas cortas para la familia («llego tarde», «hay lentejas en la nevera»). Se escriben desde la tablet (**+ Nota**) o desde el móvil; en Inicio salen las tres últimas y se borran solas a las dos semanas. Para borrar una antes, toca la ✕ dos veces.
- **Avisos del tiempo:** helada (0° o menos), nieve, rachas de viento de 60 km/h o más y lluvia probable. Son una estimación a partir de la previsión de Open-Meteo, **no los avisos oficiales de AEMET**.
- **Cumpleaños:** se añaden desde el calendario de la tablet o desde el móvil. Para meter muchos de golpe, abre la hoja **«Panel de casa – listas»** (pestaña *Cumpleaños*) y escribe nombre, día, mes y año (el año es opcional). Salen en la franja «Toda la familia» el día antes y el mismo día, y en el calendario.
- **Calendario:** toca un día para ver sus eventos y **+ Añadir evento** para apuntar uno en el calendario de casa, para una o varias personas o para toda la familia.
- **Gasolineras:** en la pestaña *Gasolina* puedes marcar favoritas ★.
- **Volver a Inicio:** desliza hacia arriba desde la barrita de abajo. Deslizar en el centro de la pantalla solo mueve las listas.
- **Modo noche:** de 00:00 a 6:30. Se cambia en Ajustes, en tramos de media hora.
- La tablet se actualiza cada 5 minutos, y la compra, el tablón y los cumpleaños cada 2.

## Personalizar

| Qué | Dónde |
|---|---|
| Personas, colores y correos | `PERSONAS` en `Code.gs` |
| Nombre y color de la franja de la familia | `NOMBRE_FAMILIA`, `COLOR_FAMILIA` en `Code.gs` |
| Palabras que deciden la sección de cada producto | `PALABRAS_SECCION` en `Code.gs` (y la misma lista en `index.html` y `movil.html`) |
| Otros calendarios en la tablet (festivos…) | `OTROS_CALENDARIOS` en `Code.gs` |
| Calendario principal | `CALENDARIO_CASA` en `Code.gs` |
| Combustibles y municipio | `COMBUSTIBLES`, `MUNICIPIO`, `PROVINCIA_ID` en `Code.gs` |
| Proteger con clave | `CLAVE` en `Code.gs` y `?clave=…` al final de la URL del script |
| Horario del modo noche | Ajustes de la tablet |

## Si algo falla

| Síntoma | Qué mirar |
|---|---|
| Punto rojo «El Apps Script no responde» | La implementación debe tener acceso **Cualquier usuario**. Abre la URL en el ordenador: tiene que salir texto con datos. |
| «Compilar apps» falla en GitHub | Abre la ejecución en *Actions* y copia el error. Lo más habitual es que falte alguna carpeta de `android/` al subirla. |
| No sale el aviso en el móvil | Permisos dados, «Preguntarme cada vez» activado, batería **Sin restricciones**. Prueba con **Probar aviso**. |
| Pulsé «Añadir a casa» pero no aparece | Espera 5 minutos. Si sigue sin salir, en la app elige **Copiar en el calendario de casa** (necesita el calendario de casa compartido contigo y sincronizado en el móvil). |
| No salen gasolineras | Abre la URL del script con `?debug=gasolineras` y comprueba que en `municipios` aparece «Teruel». |
| Para actualizar una app pide desinstalar | Faltan los secretos de firma del paso 3. |
| Las letras se ven distintas (más simples) | Falta la carpeta `fuentes/` en GitHub o alguno de sus archivos. |
| Un evento sale en la columna equivocada | Revisa los correos de `PERSONAS` en `Code.gs`. Si es de varios, al crearlo elige a todos en **Para quién**. |

Datos del tiempo: [Open-Meteo](https://open-meteo.com). Precios: Ministerio para la Transición Ecológica (Geoportal de gasolineras).
