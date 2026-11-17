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

* **Cliente Android:** Kotlin + Jetpack Compose (Material 3 con tokens personalizados oscuro/verde azulado).
* **Autenticación:** Credential Manager con inicio de sesión de Google (OAuth ID Token).
* **Backend:** Node.js / TypeScript sobre Vercel Serverless Functions.
* **Base de Datos:** PostgreSQL en Neon (acceso autenticado mediante consultas parametrizadas).
