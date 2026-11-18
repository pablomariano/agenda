# Design

## Context

El cambio base `agenda-android-personal` define Kotlin y Compose, y contiene los flujos del producto. El archivo de Figma ya tiene un prototipo oscuro. Las dos referencias del usuario proponen superficies claras, color concentrado, cabeceras con profundidad y controles de selección visibles.

## Goals / Non-Goals

- Hacer que el estado del día y la siguiente acción sean visibles en pocos segundos.
- Aplicar el mismo lenguaje visual a planificación, recuperación de IA e ideas.
- El alcance de este cambio es la presentación y navegación. El registro de recuperación de IA sigue siendo manual según el cambio base.

## Decisions

- **Identidad.** Roboto, fondo menta `#EEF5F3`, blanco `#FFFFFF`, texto `#112D2B`, texto secundario `#496460`, acción `#006A55` y azul petróleo `#155C73`. Ámbar representa espera y bloqueo, acompañado de texto. La cabecera de Inicio combina petróleo y verde con un motivo abstracto y avance basado en datos. Las demás vistas reservan el color intenso para selecciones y acciones.
- **Profundidad.** Tres planos visuales: fondo, tarjetas con sombra suave y panel destacado. Radios de 16–26 dp y ritmo de 8 dp. La barra inferior utiliza icono y etiqueta con un fondo menta para el destino activo.
- **Pantallas.** Hoy incluye el avance, las prioridades y recuperaciones. Semana presenta siete días y objetivo semanal. Proyectos y su detalle conectan importancia e impacto. Ideas permite capturar y abrir la conversión. El registro separa herramienta, ventana, modo de entrada y decisión. Ajustes agrupa rutinas, permisos y cuenta.
- **Figma.** El [sistema de diseño vigente](https://www.figma.com/design/DNdKPG2y6fJvbhWwNLnJ6a?node-id=27-389) reúne los fundamentos y componentes maestros que usa el [prototipo claro](https://www.figma.com/design/DNdKPG2y6fJvbhWwNLnJ6a?node-id=21-2). Las páginas numeradas del mismo archivo conservan el diseño oscuro. Se puede iniciar en «Agenda · Hoy» o «Agenda · Primer uso». Los campos y selectores representan estados de ejemplo y los enlaces muestran los recorridos entre pantallas.
- **Android.** Trasladar los roles de color a Material 3 y usar contenedores con altura adaptable, insets de sistema y componentes semánticos de Compose. La tarjeta con más jerarquía es el resumen en Hoy y el objetivo en Semana.

## Risks / Trade-offs

- Los paneles de resumen pueden desplazar las tareas en pantallas pequeñas. Mantener la cabecera compacta y el contenido desplazable.
- Las horas de recuperación pueden confundirse con disponibilidad confirmada. Usar «prevista» y mostrar el origen manual en el detalle.
- Figma comprueba composición y destinos del prototipo. El texto ampliado y TalkBack se validan en la app Android.

## Migration Plan

Aplicar este diseño al implementar la interfaz de `agenda-android-personal`. Para sincronizar OpenSpec, incorporar primero el cambio base para establecer el requisito «Sistema visual accesible» y aplicar después esta modificación y sus requisitos nuevos.
