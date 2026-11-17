# Design

## Context

Ver `proposal.md` para la motivación y `specs/` para los contratos de comportamiento. El repositorio solo contiene OpenSpec y sus skills; no hay aplicación, dependencias, esquema de datos ni pruebas existentes que migrar. El usuario empieza desde cero, usa Android con internet y desea un APK personal con Vercel y Neon. Usa Codex/Gemini tanto en computador como en celular.

Las preferencias confirmadas son requisitos de producto. Las decisiones técnicas y los valores por defecto que siguen son propuestas de implementación, no elecciones expresas del usuario.

## Goals / Non-Goals

**Goals:**
- Separar datos duraderos, planificación y avisos para que un límite de IA no altere automáticamente tareas.
- Implementar una sola app, una API y una base de datos, sin infraestructura de trabajo permanente.
- Mantener verificables las horas, la autorización y los reintentos.

**Non-Goals:**
- Construir un motor de IA para priorizar, un calendario externo o una plataforma multiusuario.
- Añadir un conector de escritorio sin una investigación y especificación posterior.
- Medir tokens restantes o inferir reinicios semanales sin información del proveedor.

## Decisions

### Cliente Android nativo y backend pequeño

Propuesta: Kotlin con Jetpack Compose para el APK; API HTTPS en TypeScript mediante funciones Node.js de Vercel; PostgreSQL en Neon. Compose es el toolkit recomendado de Android y Vercel admite funciones TypeScript. Esta combinación permite usar las funciones del teléfono directamente y mantener el servidor limitado a autenticación y datos. Fuentes: [Compose](https://developer.android.com/compose), [Node.js en Vercel](https://vercel.com/docs/functions/runtimes/node-js).

Alternativas: una PWA no satisface la preferencia final de APK nativo; Flutter/React Native agregan una capa multiplataforma que hoy no se necesita. Rust queda fuera de la base inicial por no existir una necesidad de rendimiento medida ni código que migrar; no se afirma que sea incompatible con Vercel. Se reconsiderará con un problema concreto, sin cambiar los contratos de la API.

### Identidad de la agenda separada de las IA

Propuesta: inicio de sesión con Google mediante Credential Manager y verificación del token de identidad en el backend usando una biblioteca oficial. Validar firma, emisor, audiencia y expiración; autorizar únicamente el identificador estable del propietario configurado. No usar un correo enviado por el cliente como prueba de identidad. Renovar la autenticación cuando expire; cerrar sesión limpia los avisos y datos locales personales. Fuente: [inicio de sesión de Android](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).

Esta autorización identifica al usuario de la agenda; NO concede acceso al consumo de Gemini. La alternativa de códigos por correo requiere otro servicio de entrega y no aporta una ventaja para este único usuario Android.

### Datos mínimos y API

- Usuario/configuración: identificador de propietario, zona horaria IANA, rutinas y días elegidos.
- Proyecto: título, descripción e importancia de tres niveles.
- Tarea: proyecto, título, descripción, impacto, estado pendiente/completada, bloqueo opcional por IA, día planificado, hora opcional y orden manual diario.
- Objetivo semanal: texto, semana y estado de cumplimiento. Propuesta inicial: semanas de lunes a domingo.
- Idea: texto y referencia opcional al proyecto o tarea generado.
- Recuperación de IA: proveedor, tipo de ventana corta/semanal, instante de registro, instante de recuperación, fuente manual, decisión esperar/continuar, IA alternativa y tarea opcionales, cancelación.
- Recordatorio: referencia al elemento o rutina, próxima ocurrencia, versión y estado. No almacenar conversaciones ni credenciales de IA.

Cada recurso pertenece al propietario. Las rutas por recurso validan tipos, tamaños, fechas, enumeraciones y referencias antes de escribir. Transacciones y claves de idempotencia protegen creación y conversión de ideas frente a reintentos. SQL parametrizado y conexión segura desde el servidor; nunca conexión directa del APK a Neon. La configuración de conexión procede de Neon, según su [documentación de conexión](https://github.com/neondatabase/website/blob/main/content/docs/get-started/connect-neon.md).

### Prioridad y arrastre sin procesos diarios

El orden sugerido es lexicográfico: importancia del proyecto, luego impacto de tarea; empate por fecha de creación e identificador estable. El orden elegido por el usuario se almacena aparte y no cambia al recalcular sugerencias. Las tareas bloqueadas permanecen visibles e identificadas.

Propuesta: derivar «Hoy» con tareas pendientes cuya fecha planificada sea igual o anterior al día local; así el arrastre funciona aunque no se abra la app durante varios días y no requiere copiar filas ni cron nocturno. Conservar la fecha original para indicar procedencia. Las completadas no se arrastran. El horario de un día anterior no se convierte en una nueva alarma: se requiere reprogramarlo. La vista semanal presenta el día efectivo actual de los pendientes arrastrados.

### Recordatorios locales para la primera versión

Las notificaciones visibles solicitadas se cubrirán inicialmente con recordatorios programados en Android. No se requiere push remoto para avisos que el propio usuario configura en su único celular. Un futuro evento detectado en el computador sí requeriría un canal remoto; forma parte de la investigación, no de esta implementación.

Mantener en el teléfono solo el registro persistente mínimo de programación, no una réplica editable de la agenda. AlarmManager programa las ocurrencias; usar identificadores estables para reemplazar/cancelar y reconciliar al abrir la app, reiniciar el dispositivo o cambiar hora/zona. Reprogramar rutinas según hora civil local; las recuperaciones son instantes UTC mostrados en la zona elegida. Posponer se maneja en el registro local y se sincroniza al servidor; completar exige la confirmación de la API. Definir corrección de cambios de hora inexistentes/ambiguos antes de guardar con confirmación del usuario.

Solicitar permiso de notificaciones cuando corresponda y permiso de alarmas exactas si se necesita puntualidad; si falta, mostrar limitación y ofrecer ajustes sin fallar. No usar temporizadores en funciones Vercel ni sondeo continuo. Android condiciona las alarmas exactas a permisos y limita ejecución en segundo plano: [alarmas](https://developer.android.com/develop/background-work/services/alarms), [permisos de alarmas exactas](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms). Validar en el teléfono real con la interfaz cerrada; cierre forzado, dispositivo apagado o permisos revocados no permiten una garantía absoluta.

### Recuperación de IA: contrato manual, investigación automática

Guardar el instante informado por el usuario, nunca una duración fija de cinco horas. Puede haber una ventana corta y una semanal simultáneas; cada una tiene su aviso. Llegar a una hora prevista no prueba disponibilidad real. Registrar el cambio a otra IA sin cancelar la recuperación original ni asociar obligatoriamente cada tarea a una herramienta.

Investigar por separado los productos y planes concretos de Codex y Gemini, las fuentes oficiales de límites, los reinicios y permisos disponibles. Evaluar autorización oficial o un conector local que transmita únicamente proveedor, ventana y hora, nunca contraseñas, cookies ni conversaciones. Probar con datos sintéticos antes de conectar una cuenta. No confundir cuotas de APIs con límites de suscripciones. Resultado exigido: evidencia por producto, limitaciones y recomendación de integrar o mantener manual; no desarrollar un conector genérico especulativo.

### Design system y prototipo en Figma

Crear posteriormente un archivo con fundamentos, componentes y flujos. Tema oscuro, acento verde azulado, tokens semánticos de superficie/texto/acento/estado; tipografía legible y espaciado consistente. Reutilizar patrones Material en el cliente en vez de inventar controles. Comprobar contraste (texto normal 4.5:1), objetivos táctiles de 48 dp, texto ampliado y TalkBack; los valores finales de color se eligen con esas comprobaciones.

Componentes mínimos: botón, campo, selector de nivel, tarjeta de tarea/proyecto, tarjeta de recuperación, fila de idea y estados vacío/error/carga. Pantallas: Inicio, proyectos y detalle de tarea, semana, ideas, disponibilidad de IA y ajustes. Flujos del prototipo: idea a tarea/proyecto; elegir prioridades y horario; registrar límite y continuar con otra IA; configurar rutinas. Incluir permisos denegados, recuperación desconocida y tarea arrastrada. No añadir tableros Kanban ni tema claro en esta primera entrega.

## Risks / Trade-offs

- [Límites de IA inaccesibles] → Mantener registro manual y dejar la automatización como resultado de investigación, sin anunciarla como funcional.
- [Alarmas retrasadas por Android o fabricante] → Estado visible de permisos y ensayo en dispositivo real antes de entregar el APK.
- [Sin conexión] → Lectura/edición de la agenda requiere internet; los recordatorios ya programados se conservan localmente. No mostrar éxito ficticio al guardar.
- [Reintentos o conversión parcial] → Transacciones e idempotencia, con pruebas de fallos de red.
- [Pérdida de clave de firma] → Guardar la clave de firma fuera del repositorio con respaldo; la actualización del APK depende de conservarla.

## Migration Plan

No hay datos ni sistema previo que migrar. Crear esquema inicial y entornos separados de prueba y uso personal. Configurar en Vercel `DATABASE_URL`, audiencia de Google e identificador autorizado; en el APK solo URL de API e identificadores públicos de OAuth. Mantener archivo de ejemplo sin valores privados e ignorar secretos locales y keystore.

Desplegar API, verificar acceso permitido/denegado y guardar/recuperar datos. Instalar APK firmado, verificar sus notificaciones y luego utilizarlo con datos reales. Aplicar migraciones aditivas; ante fallo volver al despliegue/API y APK compatibles anteriores, conservando datos y usando respaldo antes de cualquier cambio destructivo.

## Open Questions

- Modelo y versión de Android del teléfono, para fijar compatibilidad mínima y matriz de pruebas antes de compilar.
- Nombre visible de la app y valores visuales finales, a resolver en el prototipo.
- Productos y planes exactos de Codex/Gemini, como entrada de la investigación automática; no bloquean el flujo manual.
