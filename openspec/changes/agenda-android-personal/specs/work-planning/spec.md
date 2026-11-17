# Spec Delta

## Purpose

Organizar proyectos y tareas para elegir prioridades diarias y planificar objetivos semanales sin perder pendientes.

## ADDED Requirements

### Requirement: Proyectos y tareas
La aplicación SHALL permitir crear y editar proyectos con importancia alta, media o baja, y tareas con título, proyecto e impacto alto, medio o bajo.

#### Scenario: Crear y recuperar una tarea
- **WHEN** el usuario guarda una tarea de impacto alto en un proyecto de importancia media y vuelve a abrirla
- **THEN** se conservan su título, proyecto e impacto y los cambios confirmados por el servidor.

### Requirement: Sugerencias explicables y elección manual
La aplicación SHALL sugerir tareas pendientes ordenadas primero por importancia del proyecto y luego por impacto, de mayor a menor. SHALL permitir elegir y ordenar manualmente las prioridades de hoy desde todos los proyectos sin sobrescribir esa elección al recalcular sugerencias.

#### Scenario: Sugerencias y orden manual
- **WHEN** el usuario selecciona una tarea de menor importancia y la coloca primero
- **THEN** su orden permanece y las sugerencias siguen mostrando las etiquetas de importancia e impacto como explicación.

### Requirement: Planificación diaria y semanal
La aplicación SHALL permitir asignar días y horarios opcionales a tareas, así como crear y editar objetivos por semana. SHALL mostrar los objetivos y las tareas de cada día en la vista semanal.

#### Scenario: Semana con tareas sin hora
- **WHEN** el usuario crea un objetivo semanal y asigna una tarea al martes sin horario
- **THEN** ambos aparecen en su semana y la tarea no requiere hora ni genera un aviso horario por defecto.

### Requirement: Arrastre de pendientes
La aplicación SHALL mostrar en el día actual todas las tareas pendientes planificadas en días anteriores, incluidas las bloqueadas, sin duplicarlas ni descartar las ya previstas para hoy. SHALL conservar la fecha anterior como referencia y permitir reprogramarlas y priorizarlas manualmente.

#### Scenario: Varios días sin abrir la agenda
- **WHEN** el usuario abre la agenda el jueves con una tarea pendiente del lunes
- **THEN** aparece una sola vez entre las tareas del jueves, junto a las previstas para ese día, con su procedencia del lunes.

#### Scenario: Horario anterior vencido
- **WHEN** una tarea con hora pendiente se arrastra al día actual
- **THEN** no se reutiliza automáticamente su hora anterior ni se emite un aviso vencido; el usuario puede asignar una nueva hora.

### Requirement: Estado y bloqueo independientes
La aplicación SHALL permitir marcar una tarea pendiente o completada y añadir o quitar un bloqueo por Codex o Gemini. Un aviso de recuperación SHALL NOT completar una tarea ni quitar por sí solo su bloqueo.

#### Scenario: Retomar con otra IA
- **WHEN** el usuario decide continuar con otra IA y quita el bloqueo de la tarea
- **THEN** la tarea sigue pendiente y los recordatorios de recuperación registrados permanecen activos.
