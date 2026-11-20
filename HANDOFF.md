# Traspaso para continuar Agenda

Actualizado el 20 de noviembre de 2026. Este archivo contiene contexto y un prompt para continuar en otro equipo; las especificaciones y el código son la fuente de verdad si algo cambia después.

## Prompt para el siguiente asistente

> Continúa el desarrollo de Agenda en español. Primero revisa `git status`, los commits recientes y `openspec instructions apply --change agenda-android-personal --json`; lee los archivos de contexto que indique OpenSpec. Respeta los cambios existentes y no incluyas archivos ajenos en commits. Si está disponible `codebase-memory-mcp`, úsalo para descubrir código antes de buscar en archivos, y recurre a búsquedas de texto para configuración, documentación o cuando el grafo no baste.
>
> El último avance fue una primera versión de recordatorios de tareas para Android. Ayúdame a comprobarla en mi teléfono: instalación sobre la versión anterior, permisos de notificaciones y alarmas exactas, aviso con la app cerrada, apertura de la tarea, posposición, edición y finalización, y reinicio. No afirmes que se probó en un dispositivo real hasta que yo lo confirme. Investiga y corrige los fallos observados; después continúa con las tareas pendientes de OpenSpec según las prioridades de la conversación. Actualiza en la propia Agenda la tarea del proyecto correspondiente cuando esté realmente completada, conservando el resto de mis datos.
>
> Mantén el alcance sencillo, sigue el diseño claro y usa OpenSpec para la planificación. No implementes actualización automática del APK por ahora. No introduzcas autenticación con Google. Antes de usar Neon o Vercel, comprueba la configuración sin imprimir secretos; usa una base PostgreSQL local desechable para pruebas destructivas. No regeneres el código de acceso salvo que yo lo solicite: revocaría el actual. No archives ni marques tareas incompletas como terminadas.

## Producto y decisiones

- Agenda personal para Android, instalada mediante APK y utilizada con conexión a internet. Diseño claro y verde azulado; el modo oscuro queda para más adelante.
- Proyectos con importancia y tareas con impacto (alto, medio, bajo), planificación por fecha y hora opcional, y pendientes de días anteriores visibles en Hoy. El usuario comprobó la planificación en su teléfono.
- Se desea completar planificación semanal, ideas, prioridades elegidas por el usuario y registro de límites de Codex y Gemini. La primera versión de recuperación de IA será manual: hora o duración, con aviso independiente y opción de continuar con otra IA. No pedir contraseñas ni prometer lectura automática de cuotas; revisar [la investigación](docs/ai-limits-research.md) y actualizarla si se vuelve a investigar.
- El acceso usa un código privado propio, no Google. La actualización automática del APK se dejó de lado expresamente.

## Estado al traspasar

- Código de referencia: commit `23192fb` (`Add Android task reminders`); consultar `git log` para detectar avances posteriores.
- OpenSpec: cambio `agenda-android-personal`, 8 de 29 tareas marcadas completas en el momento del traspaso. Consultar [tareas](openspec/changes/agenda-android-personal/tasks.md) y especificaciones antes de implementar. La tarea 3.3 sigue abierta porque incluye casos aún no completos, como tareas bloqueadas por IA. Las tareas 5.2 y 5.4 siguen abiertas: no dar por cumplidos todos sus criterios por existir una implementación parcial.
- API TypeScript desplegada en `https://agendias.vercel.app`, PostgreSQL en Neon. Migraciones `001_projects_and_tasks.sql` y `002_task_time.sql` ya aplicadas a la base personal. La aplicación tiene datos reales: no borrar ni reemplazar tablas o registros para hacer pruebas.
- Android `0.4.0-preview`, `versionCode` 4, paquete `com.pablomariano.agenda.preview`: hay alarmas locales para tareas con hora, cancelación al completar, opción de desactivar, apertura y posposición, manejo de permisos y restauración tras reinicio. La verificación de recordatorios se hizo en emulador, no consta aceptación en el teléfono real. El usuario confirmó la versión anterior con planificación por fecha y hora.
- En el emulador se comprobaron aviso con la interfaz cerrada, permiso concedido y denegado, persistencia, restauración tras reiniciar, posposición y cancelación. Ver [pruebas aisladas](android/test-fixtures/reminders/README.md). Las rutinas de planificación/revisión y avisos de recuperación de IA aún no están implementados.
- Los enlaces de descarga anteriores apuntaban a un servidor temporal en la red local del equipo original y no deben considerarse permanentes.
- El usuario pidió mantener al día las tareas del proyecto de Agenda dentro de la app. La tarea `App 5.1` se marcó completada. Identificar cada tarea por su título y actualizarla solo después de verificar sus criterios; evitar cambios masivos o sobrescribir ediciones concurrentes.

## Preparar otro equipo

1. Clona el repositorio de GitHub configurado como `origin` y consulta `git status` y `git log`. Los archivos no seguidos del equipo original, si los hubiera, no viajan automáticamente: no asumir que están disponibles ni añadirlos sin revisarlos.
2. Instala Node.js 24, JDK 17 o 21, Android SDK API 35 y Build Tools 35.0.0. En `server/`, ejecuta `npm ci`. La guía completa está en [desarrollo](docs/development.md).
3. Si necesitas acceso a la base existente, recupera `DATABASE_URL` por un canal seguro y configúrala localmente a partir de `server/.env.example`; el despliegue mantiene su propia variable en Vercel. No subas `.env`, claves ni el código de acceso al repositorio. Para pruebas usa `TEST_DATABASE_URL` apuntando a PostgreSQL local desechable, base `agenda_test`; no uses la base personal para pruebas destructivas. `npm test` sin esa variable omite la prueba de persistencia.
4. Conserva y transfiere **de forma segura** la clave de firma con la que se instaló el APK anterior. La versión de prueba se firmó en el equipo original con la clave de depuración local de Android (normalmente `~/.android/debug.keystore`); valida que sea la misma antes de compilar. Una clave distinta impide instalar el nuevo APK como actualización sobre la app existente. No la subas a Git. Ver también [guía de firma](docs/development.md).
5. Para compilar la app conectada, desde la raíz:

   ```bash
   cd android
   ./gradlew :app:assembleDebug -PagendaApiUrl=https://agendias.vercel.app
   ```

   El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk` (ruta relativa a la raíz del repositorio). Compilar sin `agendaApiUrl` genera la demo separada. Si se usó el `init.gradle` del fixture de pruebas, recompila sin él antes de entregar el APK y comprueba que no contiene el receptor de prueba.
6. Recupera el código privado existente mediante un canal seguro si necesitas volver a ingresar en la app. Si el teléfono conserva la instalación, puede conservar el acceso al actualizar con la misma firma. No ejecutes `access:generate` solo para configurar el nuevo equipo: revoca el código vigente.

## Referencias

- [Guía de desarrollo y configuración](docs/development.md)
- [Sistema visual y referencias de Figma](docs/design-system.md)
- [Flujos de producto propuestos](docs/screen-flows.md): describe objetivos de diseño, no implica que todas las pantallas estén implementadas.
- [Diseño técnico de OpenSpec](openspec/changes/agenda-android-personal/design.md)
- [Especificación de recordatorios](openspec/changes/agenda-android-personal/specs/reminders/spec.md)
- [Pruebas de recordatorios en emulador](android/test-fixtures/reminders/README.md)
