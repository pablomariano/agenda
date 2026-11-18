# Spec Delta

## MODIFIED Requirements

### Requirement: Sistema visual accesible
La app SHALL usar un tema claro con fondo menta tenue, superficies blancas y acentos verde petróleo y azul. Las prioridades SHALL incluir etiquetas textuales además del color. Los componentes SHALL admitir texto ampliado, etiquetas para lector de pantalla, contraste mínimo de 4,5:1 para texto normal y objetivos táctiles de al menos 48 × 48 dp.

#### Scenario: Lectura sin distinguir colores
- **WHEN** el usuario consulta una tarea de impacto alto con lector de pantalla o sin distinguir los colores
- **THEN** identifica su impacto, estado y acciones mediante texto o etiquetas accesibles.

#### Scenario: Texto ampliado
- **WHEN** el usuario aumenta el tamaño del texto en Android
- **THEN** las tarjetas permiten envolver texto y las acciones principales siguen accesibles mediante desplazamiento.

## ADDED Requirements

### Requirement: Jerarquía de la jornada
Inicio SHALL distinguir el avance de las prioridades elegidas, las tareas accionables y las recuperaciones previstas. Cuando haya prioridades, el avance SHALL reflejar las completadas sobre el total elegido. El detalle de recuperación SHALL indicar herramienta, ventana y hora prevista o un estado explícito cuando se desconozca.

#### Scenario: Jornada con trabajo y una recuperación
- **WHEN** existe una prioridad completada de tres elegidas y una recuperación futura de Codex
- **THEN** Inicio muestra el avance de una de tres, diferencia las prioridades pendientes del aviso y permite acceder a los detalles de ambos.

#### Scenario: Jornada sin prioridades
- **WHEN** todavía no hay prioridades elegidas
- **THEN** Inicio ofrece planificar el día sin mostrar porcentajes ficticios.

### Requirement: Navegación y selección reconocibles
La navegación principal SHALL identificar Hoy, Semana, Proyectos e Ideas con icono, texto y un estado activo visible. Semana SHALL mostrar los siete días y comunicar cuál está seleccionado. Las pantallas secundarias SHALL ofrecer una forma visible de volver al contexto de origen.

#### Scenario: Revisar un día y regresar de un formulario
- **WHEN** el usuario consulta Semana y abre un detalle o formulario
- **THEN** puede identificar el día seleccionado y al volver retoma la vista de origen.
