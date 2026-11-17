# Spec Delta

## Purpose

Capturar ideas sin exigir planificación inmediata y convertirlas después en trabajo concreto conservando su contenido.

## ADDED Requirements

### Requirement: Captura rápida
La aplicación SHALL permitir guardar una idea con texto no vacío sin exigir proyecto, fecha ni prioridad, y consultarla después en la bandeja de ideas.

#### Scenario: Idea sin clasificar
- **WHEN** el usuario escribe una idea y la guarda sin otros datos
- **THEN** aparece en la bandeja y conserva su contenido al volver a abrir la aplicación.

### Requirement: Conversión sin pérdida ni duplicación
La aplicación SHALL permitir convertir una idea en una tarea de un proyecto existente o en un proyecto nuevo. SHALL conservar el texto original como descripción, enlazar el resultado y marcar la idea como convertida solo tras confirmar el guardado.

#### Scenario: Convertir en tarea
- **WHEN** el usuario elige un proyecto existente y confirma la conversión
- **THEN** se crea una tarea enlazada y la idea deja de aparecer entre las pendientes de organizar.

#### Scenario: Convertir en proyecto
- **WHEN** el usuario elige crear un proyecto a partir de una idea
- **THEN** se conserva su contenido en el nuevo proyecto y puede agregar tareas manualmente.

#### Scenario: Fallo o reintento
- **WHEN** falla la conversión o se reintenta la misma solicitud
- **THEN** la idea sigue siendo recuperable y no se crean resultados duplicados.
