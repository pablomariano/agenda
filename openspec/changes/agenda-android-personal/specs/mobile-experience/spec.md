# Spec Delta

## Purpose

Ofrecer una experiencia Android personal centrada en las prioridades de hoy y en la recuperación de las herramientas de IA.

## ADDED Requirements

### Requirement: Inicio orientado al día
La pantalla principal SHALL mostrar las prioridades elegidas para hoy y un resumen de las recuperaciones registradas de Codex y Gemini. SHALL permitir acceder a todos los pendientes de hoy, proyectos, semana, ideas, disponibilidad de IA y ajustes.

#### Scenario: Prioridades y recuperaciones
- **WHEN** el usuario abre la app con tareas seleccionadas y una recuperación futura de Codex
- **THEN** ve ambos resúmenes y puede abrir sus detalles.

#### Scenario: Primer uso
- **WHEN** no hay tareas ni recuperaciones registradas
- **THEN** se muestran estados vacíos con acciones para crearlas, sin datos ficticios ni disponibilidad de IA asumida.

### Requirement: Sistema visual accesible
La app SHALL utilizar un tema oscuro y sobrio con verde azulado como acento. Las prioridades SHALL incluir etiquetas textuales además del color. Los componentes SHALL admitir texto ampliado, etiquetas para lector de pantalla y contraste legible.

#### Scenario: Lectura sin distinguir colores
- **WHEN** el usuario consulta una tarea de impacto alto con lector de pantalla o sin distinguir los colores
- **THEN** identifica su impacto, estado y acciones mediante texto o etiquetas accesibles.

### Requirement: Distribución Android personal
La primera versión SHALL entregarse como APK firmado instalable para el propietario, con instrucciones de instalación y actualización que conserven los datos confirmados en el servidor.

#### Scenario: Actualizar APK
- **WHEN** se instala una versión posterior compatible firmada con la misma clave
- **THEN** el usuario puede acceder a sus datos existentes sin recrear proyectos ni tareas.
