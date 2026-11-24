# Spec Delta

## Purpose

Avisar sobre tareas, planificación diaria, revisión y recuperación de IA, con control explícito de horarios y acciones.

## ADDED Requirements

### Requirement: Horarios y rutinas configurables
La aplicación SHALL permitir configurar, modificar y desactivar recordatorios de tareas con hora y rutinas de planificación y revisión con días y horarios elegidos por el usuario. SHALL mostrar la zona horaria utilizada.

#### Scenario: Configurar rutinas
- **WHEN** el usuario elige planificar lunes y miércoles a las 09:00 y revisar esos días a las 18:00
- **THEN** se programan únicamente esas ocurrencias y la configuración persiste.

### Requirement: Acciones desde notificaciones
Las notificaciones de tarea SHALL ofrecer abrir la tarea y posponer el aviso a una nueva hora elegida. Completar una tarea SHALL realizarse dentro de la agenda; posponer SHALL NOT modificar su fecha planificada ni su estado.

#### Scenario: Posponer rápidamente
- **WHEN** el usuario elige 10 o 20 minutos en la notificación de una tarea
- **THEN** el aviso se vuelve a programar para ese intervalo desde la elección, sin cambiar la fecha, la hora ni el estado de la tarea. La opción de elegir otra hora sigue disponible.

#### Scenario: Posponer
- **WHEN** el usuario pospone un aviso
- **THEN** se reemplaza su siguiente ocurrencia y la tarea permanece pendiente con su planificación original.

### Requirement: Consistencia de avisos
La aplicación SHALL cancelar avisos futuros de tareas completadas y reemplazar los de horarios editados. SHALL evitar duplicados al reintentar, reiniciar el teléfono o volver a abrir la aplicación.

#### Scenario: Completar antes del aviso
- **WHEN** se confirma la finalización de una tarea antes de su recordatorio
- **THEN** ese recordatorio no se emite.

#### Scenario: Reinicio y edición
- **WHEN** se cambia un horario y después se reinicia el teléfono
- **THEN** solo queda programado el aviso vigente, sin repetir el anterior.

### Requirement: Permisos y entrega observable
La aplicación SHALL solicitar los permisos necesarios y mostrar si los avisos están deshabilitados o su puntualidad está limitada. SHALL emitir los avisos programados con la interfaz cerrada bajo permisos concedidos y funcionamiento normal de Android, sin prometer entrega si el sistema la impide.

#### Scenario: Permiso denegado
- **WHEN** el usuario deniega notificaciones
- **THEN** puede seguir usando la agenda, ve que no recibirá avisos y dispone de acceso a la configuración pertinente.

#### Scenario: Interfaz cerrada
- **WHEN** vence un recordatorio futuro con permisos concedidos y la aplicación fuera de primer plano
- **THEN** aparece una notificación con destino al elemento correcto sin requerir abrir antes la agenda.
