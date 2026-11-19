# Documentación de Desarrollo — Agenda

## 1. Dispositivo y Entorno de Aceptación

* **Nombre visible de la aplicación:** `Agenda`
* **Identificador de paquete (Application ID):** `com.pablomariano.agenda`
* **Dispositivo de aceptación y prueba:** Android 14 / 15 (API level 34 / 35).
* **Especificaciones del SDK de Android:**
  * `minSdk`: **26** (Android 8.0 Oreo) — Garantiza compatibilidad con `NotificationChannel`, `java.time` desugaring y gestión moderna de alarmas.
  * `targetSdk`: **35** (Android 15) — Cumplimiento de las directivas actuales de Google Play y de la plataforma para permisos y ejecución en segundo plano.
  * `compileSdk`: **35** (Android 15).

## 2. Permisos y Comportamiento del Sistema

Dado que el dispositivo de aceptación corre Android 14/15, se deben manejar explícitamente:
1. **Notificaciones (`POST_NOTIFICATIONS`):** Obligatorio solicitar en tiempo de ejecución a partir de API 33 (Android 13).
2. **Alarmas exactas (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`):**
   * A partir de Android 14 (API 34), `SCHEDULE_EXACT_ALARM` está restringido y el usuario debe concederlo en Ajustes si la app no califica para exención.
   * La aplicación debe verificar `AlarmManager.canScheduleExactAlarms()` antes de invocar alarmas exactas y ofrecer una degradación elegante o acceso directo a la configuración si no está concedido.
3. **Inicio al encender (`RECEIVE_BOOT_COMPLETED`):**
   * Requerido para reprogramar alarmas tras reinicio del dispositivo.

## 3. Stack Tecnológico de la Solución

* **Cliente Android:** Kotlin + Jetpack Compose y Material 3 con los tokens del [sistema claro de Agenda](design-system.md).
* **Acceso personal:** Código privado de Agenda. Android lo cifra con el Keystore y la API comprueba su huella en PostgreSQL.
* **Backend:** Node.js / TypeScript sobre Vercel Serverless Functions.
* **Base de Datos:** PostgreSQL en Neon (acceso autenticado mediante consultas parametrizadas).

## 4. APK de prueba de Hoy

La demo de Kotlin y Compose se encuentra en `android/`. Abre en el tema claro y permite consultar el avance, las prioridades y las horas previstas de Codex y Gemini con datos de ejemplo. Los avisos de la demo son informativos. Las pestañas de Semana, Proyectos e Ideas se muestran como referencia visual.

Para compilar, configura JDK 17 o 21 y el SDK de Android con API 35 y Build Tools 35.0.0. Usa `ANDROID_HOME` o el archivo local `android/local.properties` para indicar la ruta del SDK.

```bash
cd android
./gradlew :app:assembleDebug
```

El archivo se genera en `app/build/outputs/apk/debug/app-debug.apk`. Se firma con la clave local de depuración y usa el paquete `com.pablomariano.agenda.demo`, separado de la futura app personal.

Para probarlo, transfiere el APK al teléfono y ábrelo. Si Android pide permiso de instalación para la app que abrió el archivo, sigue el aviso del sistema. Luego abre Agenda y toca una prioridad o «Ver avisos».

## 5. Agenda con datos reales

1. Crea una base PostgreSQL en Neon y configura su conexión TLS como `DATABASE_URL` en un archivo local `server/.env.local`. Usa `server/.env.example` como plantilla.
2. Desde `server/`, instala las dependencias con Node.js 24 y aplica la migración. Genera el código desde tu terminal y guárdalo en tu gestor de contraseñas:

   ```bash
   npm ci
   npm run migrate
   npm run access:generate
   ```

   Volver a generar el código revoca el anterior. Los proyectos y las tareas siguen siendo del mismo propietario.
3. En Vercel, usa `server` como directorio raíz del proyecto, el preset **Other**, el runtime **Node.js 24.x** y configura `DATABASE_URL` para la misma base. Tras desplegar, `GET /api/health` debe responder con `status: "ok"` y `configured: true`. Ese resultado indica que Vercel recibió la configuración, y el acceso desde Android comprueba la conexión a la base.
4. Compila el APK de vista previa con el origen HTTPS de Vercel. Introduce el código en la app tras instalar el APK.

   ```bash
   cd android
   ./gradlew :app:assembleDebug -PagendaApiUrl=https://tu-proyecto.vercel.app
   ```

La compilación conectada de depuración usa `com.pablomariano.agenda.preview`. Para generar la demo, ejecuta el comando sin `agendaApiUrl`. El APK contiene el origen público de la API. La conexión de Neon queda en el servidor y el código se guarda cifrado en el teléfono después de confirmar el acceso.

La compilación de prueba se firma con la clave de depuración de Android. Para distribuir el APK personal definitivo, usa una clave de publicación respaldada fuera del repositorio y conserva la misma clave para las actualizaciones.

En la versión conectada, crea un proyecto en **Proyectos**, abre **Nueva tarea** y guarda. La tarea queda planificada para el día actual en la zona horaria de Santiago y se muestra en **Hoy**. Los reintentos del mismo formulario conservan su identificador. Las comprobaciones del servidor se ejecutan con `npm test`; para la prueba de persistencia configura `TEST_DATABASE_URL` con una base local desechable llamada `agenda_test`.

Desde el detalle de una tarea puedes editar título, proyecto e impacto, marcarla completada o devolverla a pendiente. Hoy muestra el avance de las tareas pendientes para hoy y las completadas ese día. Las tareas completadas anteriores se pueden abrir desde su proyecto.
