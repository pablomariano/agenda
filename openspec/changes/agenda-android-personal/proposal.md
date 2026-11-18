# Proposal

## Why

El usuario necesita decidir qué hacer hoy entre varios proyectos, capturar ideas y mantener una planificación semanal sencilla. También necesita recordar cuándo puede volver a usar Codex o Gemini al alcanzar sus límites, aunque continúe trabajando con otra IA.

## What Changes

- Crear una aplicación Android personal, distribuida por APK y utilizada con conexión a internet.
- Gestionar proyectos con importancia alta, media o baja y tareas con impacto alto, medio o bajo; sugerir prioridades sin sustituir la elección manual.
- Mostrar en Inicio las prioridades de hoy y los horarios de recuperación de IA. Permitir horarios opcionales, objetivos semanales y asignación de tareas a días.
- Trasladar pendientes al día siguiente sin duplicarlos, mostrando todos para que el usuario decida su prioridad.
- Capturar ideas y convertirlas posteriormente en tareas de un proyecto existente o proyectos nuevos.
- Mantener estados pendiente/completada y bloqueo opcional por IA.
- Configurar recordatorios de tareas, planificación, revisión y recuperación de IA; abrir o posponer desde la notificación y completar dentro de la app.
- Registrar manualmente una hora o duración de espera por IA, con avisos independientes de la tarea y de la decisión de continuar con otra herramienta.
- Investigar por separado la detección automática de límites de Codex/Gemini y sus ventanas semanales sin introducir sus contraseñas en la agenda. La viabilidad y el conector aún no están confirmados; no bloquear la versión manual.
- Definir un design system en Figma, oscuro y sobrio, con verde azulado y prioridades identificables mediante texto además del color.

## Capabilities

### New Capabilities

- `work-planning`: proyectos, tareas, prioridades diarias, objetivos semanales y arrastre de pendientes.
- `idea-inbox`: captura y conversión de ideas conservando su contenido.
- `reminders`: programación y acciones de avisos Android y rutinas configurables.
- `ai-availability`: registro de ventanas de recuperación, estado informativo y avisos por IA.
- `personal-access`: acceso del propietario con código privado de Agenda y persistencia en Neon mediante backend en Vercel.
- `mobile-experience`: Inicio móvil y componentes visuales accesibles y coherentes.

### Modified Capabilities

Ninguna; el repositorio no contiene aplicación ni especificaciones previas.

## Impact

Se añadirán un cliente Android, una API en Vercel, un esquema PostgreSQL en Neon y configuración de identidad y notificaciones. Las credenciales del servidor no se incluirán en el APK. Rust es una alternativa, no un requisito; el diseño propone una base mínima y deja identificadas las validaciones técnicas pendientes.

Esta entrega solo crea documentos de planificación. No crea todavía el archivo de Figma, recursos externos, llaves, aplicación ni despliegues. Se excluyen colaboración, versión web, distribución pública en Google Play, edición sin conexión y generación de tareas mediante IA.
