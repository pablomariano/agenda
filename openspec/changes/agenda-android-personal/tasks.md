# Tasks

Todas las casillas describen trabajo futuro. Las decisiones técnicas son la propuesta de `design.md`; la investigación de IA puede terminar con una conclusión de inviabilidad y no autoriza construir una integración nueva.

## 1. Prototipo y validaciones iniciales

- [x] 1.1 Confirmar modelo/versión de Android y nombre de la app; registrar compatibilidad mínima y dispositivo de aceptación en la documentación de desarrollo.
- [x] 1.2 Crear en Figma los tokens y componentes mínimos del tema oscuro verde azulado; verificar contraste, etiquetas de prioridad y estados vacío/error/carga, entregando enlace al archivo.
- [ ] 1.3 Revisar con el usuario en Figma las pantallas Inicio, proyectos/tareas, semana, ideas, recuperación de IA y ajustes; confirmar los cuatro flujos descritos en `design.md` antes de trasladarlos a código.
- [x] 1.4 Investigar productos/planes concretos de Codex y Gemini y fuentes autorizadas de límites cortos/semanales; entregar evidencia oficial, datos accesibles, restricciones y decisión de viabilidad por producto. Si procede automatizar, proponer una especificación posterior; no condicionar la versión manual a esa integración.

## 2. Base Android, API e identidad

- [ ] 2.1 Crear cliente Kotlin/Compose y API TypeScript en Vercel con dependencias fijadas; verificar compilación de APK de prueba y respuesta del endpoint de salud.
- [x] 2.2 Preparar variables de entorno de ejemplo, exclusiones de secretos y guía de configuración de Neon/Vercel, código de acceso y firma. Comprobar que los ejemplos carecen de secretos y que la API señala configuración ausente sin mostrar valores.
- [x] 2.3 Implementar acceso con código privado cifrado en Android y autorización del propietario en la API. Comprobar que un código ausente, incorrecto o revocado se rechaza y que el código vigente funciona.
- [ ] 2.4 Crear migraciones iniciales y acceso a Neon con consultas parametrizadas; verificar aplicación sobre una base vacía, integridad de referencias y lectura/escritura autenticada.
- [ ] 2.5 Implementar manejo de red, acceso revocado y cierre de sesión; verificar formularios recuperables, ausencia de confirmaciones falsas y limpieza de datos/avisos locales.

## 3. Proyectos, tareas, ideas y semana

- [x] 3.0 Verificar en un emulador con la API y PostgreSQL locales que se pueda crear un proyecto, crear una tarea, verla en Hoy y recuperarla al reabrir la app. Comprobar reintento tras un fallo de conexión.
- [x] 3.0.1 Editar el título, proyecto e impacto de una tarea, completarla y volverla pendiente. Comprobar persistencia, avance diario y reintentos en la API y Android.
- [ ] 3.1 Implementar creación/edición de proyectos y tareas, niveles y estado/bloqueo; verificar persistencia y rechazo de entradas inválidas mediante pruebas de API.
- [ ] 3.2 Implementar sugerencias y elección/orden manual de prioridades; probar empates, cambio de importancia y conservación del orden elegido.
- [ ] 3.3 Implementar planificación por día, hora opcional y arrastre derivado; probar medianoche, varios días sin uso, completadas excluidas, bloqueadas visibles y no reutilización de horas vencidas.
- [ ] 3.4 Implementar objetivos y vista semanal; verificar un objetivo y tareas con/sin hora, incluido un pendiente arrastrado entre semanas.
- [ ] 3.5 Implementar captura y conversión transaccional de ideas a tarea o proyecto; probar fallo intermedio y reintento idempotente sin pérdida de contenido.
- [ ] 3.6 Documentar y recorrer el flujo idea → tarea → prioridad → completada y la planificación semanal; verificar correspondencia con las especificaciones `idea-inbox` y `work-planning`.

## 4. Recuperaciones manuales de IA

- [ ] 4.1 Implementar registros de Codex/Gemini por ventana corta/semanal, fecha/hora o duración y edición/cancelación; probar esperas distintas de cinco horas, fechas pasadas, medianoche y cambio horario ambiguo.
- [ ] 4.2 Registrar esperar/continuar, IA alternativa y tarea opcional; probar que cambiar de IA o completar una tarea no cancela su recuperación.
- [ ] 4.3 Mostrar resumen en Inicio y detalle de disponibilidad con origen manual y estado desconocido/previsto; probar ventanas simultáneas y que vencer una no declare recuperada la otra ni desbloquee tareas.
- [ ] 4.4 Documentar registro y corrección manual, así como límites de la detección automática; verificar que ninguna pantalla promete acceso conectado o tokens medidos.

## 5. Avisos Android y configuración

- [x] 5.1 Implementar solicitud/estado de permisos y registro local persistente de alarmas; probar permisos concedidos/denegados y documentar las limitaciones del sistema.
- [ ] 5.2 Programar tareas y recuperaciones con identificadores estables; probar edición, cancelación, finalización, reintentos y ausencia de notificaciones duplicadas.
- [ ] 5.3 Implementar días/horarios configurables de planificación y revisión; probar selección de días, desactivación y reprogramación ante cambios de zona/hora y reinicio.
- [ ] 5.4 Implementar abrir destino y posponer desde notificación; verificar que no se completa una tarea ni cambia su planificación al posponer y que la sincronización no restaura un aviso anterior.
- [ ] 5.5 Verificar en el Android del usuario las cuatro clases de aviso con pantalla bloqueada e interfaz cerrada, así como reinicio y revocación de permisos; registrar resultados reales y limitaciones, sin marcar como probado lo que no se ejecutó.

## 6. Integración y entrega personal

- [ ] 6.1 Comprobar coherencia del cliente con Figma, texto ampliado, TalkBack y objetivos táctiles; corregir problemas observados y registrar revisión visual.
- [ ] 6.2 Desplegar API y base personal, generar APK firmado y documentar instalación, respaldo de clave y actualización; verificar acceso del propietario y que el APK no contiene secretos del servidor.
- [ ] 6.3 Ejecutar recorrido integrado: crear idea/proyecto/tarea, planificar semana, arrastrar pendiente, registrar límite de IA, continuar con otra y recibir el aviso original; comprobar también reintento de red y actualización del APK conservando datos.

## Workflow follow-up

- Archivar el cambio cuando la implementación y verificaciones estén terminadas; sincronizar entonces las especificaciones principales. No archivar solo por haber completado la planificación.
