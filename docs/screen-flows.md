# Flujos de Pantalla y Prototipo — Agenda

Este documento describe la arquitectura de pantallas y los cuatro flujos esenciales definidos en `design.md` y las especificaciones de OpenSpec.

---

## 1. Estructura de Navegación y Pantallas

La aplicación utiliza navegación inferior (*Bottom Navigation*) con 4 destinos principales y 2 pantallas modales/secundarias:

```
[ Navegación Principal ]
  ├── 1. Inicio (Dashboard diario + recuperación IA)
  ├── 2. Proyectos & Tareas (Gestión jerárquica)
  ├── 3. Semana (Objetivos semanales + vista por días)
  └── 4. Ideas (Buzón de captura rápida)

[ Modales / Detalle ]
  ├── Detalle y Edición de Tarea
  ├── Registro de Límite de IA
  └── Ajustes y Rutinas
```

---

## 2. Los Cuatro Flujos de Interacción

### Flujo 1: Captura de Idea → Conversión a Tarea o Proyecto
1. **Entrada rápida:** En la pantalla **Ideas**, el usuario escribe un pensamiento o iniciativa en un campo superior simple con botón *"Guardar"*.
2. **Listado de ideas:** La idea queda registrada con fecha y hora.
3. **Conversión:**
   * Al pulsar sobre una idea, se despliega la opción:
     * *Convertir en Nuevo Proyecto:* Solicita título (prellenado con el texto de la idea) e importancia (Alta, Media, Baja).
     * *Convertir en Tarea de Proyecto Existente:* Muestra selector de proyecto e impacto (Alto, Medio, Bajo).
4. **Garantía:** La conversión es atómica y no elimina ni trunca el texto original si ocurre un fallo de conexión.

### Flujo 2: Planificación Diaria, Prioridades y Arrastre
1. **Vista de Inicio:** Muestra las tareas para *"Hoy"*.
2. **Sugerencia vs. Elección:**
   * La app calcula una lista sugerida (orden lexicográfico: importancia del proyecto primero, luego impacto de la tarea).
   * El usuario puede arrastrar y reordenar manualmente sus prioridades del día; este orden manual tiene precedencia visible sobre las sugerencias.
3. **Horario opcional:** Se puede asignar una hora específica (p. ej., 10:00 AM) que programa un recordatorio local en Android.
4. **Arrastre automático:**
   * Si una tarea de un día anterior queda sin completar, al llegar la medianoche se muestra automáticamente en *"Hoy"*.
   * Aparece con el distintivo *«Pendiente desde [fecha original]»*.
   * La hora vencida anterior no se reprograma como alarma para evitar notificaciones indeseadas a deshoras.

### Flujo 3: Límite de IA y Continuidad de Trabajo
1. **Registro:** El usuario alcanza el límite de uso en Codex o Gemini y abre el diálogo *"Registrar límite de IA"*.
2. **Parámetros:**
   * Selecciona proveedor (`Codex` o `Gemini`).
   * Tipo de ventana (`Ventana corta` de pocas horas o `Ventana semanal/diaria` para modelos de razonamiento).
   * Modo de entrada: *"Disponible a las [HH:mm]"* o *"Esperar [X] horas [Y] min"*.
   * Decisión de trabajo: *"Esperar"* o *"Continuar con [otra IA]"*.
   * Asociación opcional a una tarea activa (marcando la tarea como *«Bloqueada por IA»*).
3. **Comportamiento:**
   * Se programa un recordatorio local independiente para el instante exacto de recuperación.
   * La tarea bloqueada permanece visible con aviso ámbar/amarillo.
   * Cambiar a la otra IA o trabajar en otra tarea no cancela la alarma de recuperación original.

### Flujo 4: Rutinas de Planificación y Avisos
1. **Configuración:** En **Ajustes**, el usuario define:
   * Horario y días para planificar el día (p. ej., Lunes a Viernes a las 08:30).
   * Horario y día para revisión semanal (p. ej., Domingos a las 19:00).
2. **Notificación en Android:**
   * El aviso muestra dos acciones directas: *"Abrir"* o *"Posponer (15 min)"*.
   * Posponer no altera la planificación en el servidor ni marca tareas como completadas.
3. **Seguridad y permisos:** Si los permisos de alarma exacta o notificación son denegados por Android 14/15, la app muestra un aviso no bloqueante explicando la limitación del sistema y un botón para abrir los Ajustes del sistema.
