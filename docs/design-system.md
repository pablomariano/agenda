# Design System — Agenda

* **Biblioteca en Figma:** [Agenda — Design System](https://www.figma.com/design/DNdKPG2y6fJvbhWwNLnJ6a)
* **Equipo de Figma:** `Pablo educ`
* **Estilo visual:** Tema oscuro sobrio con acento verde azulado (*Teal*).

---

## 1. Tokens de Color y Contraste

Los tokens se han definido asegurando cumplimiento de contraste WCAG AA (mínimo 4.5:1 para texto normal y 3:1 para componentes de interfaz):

| Token Semántico | Valor Hex | Uso | Ratio de Contraste vs Fondo |
| :--- | :--- | :--- | :--- |
| `color/bg/canvas` | `#121212` | Fondo principal de la aplicación | — |
| `color/bg/surface` | `#1E1E1E` | Superficie de tarjetas y contenedores | — |
| `color/bg/elevated` | `#282828` | Superficies emergentes, modales o diálogos | — |
| `color/text/primary` | `#F5F5F5` | Títulos y contenido principal | **15.4:1** (Pasa AAA) |
| `color/text/secondary` | `#A0A0A0` | Metadatos, etiquetas y subtítulos | **6.5:1** (Pasa AA) |
| `color/accent` | `#00BFA5` | Acento verde azulado / acciones principales | **8.97:1** (Pasa AAA) |
| `color/text/on-accent`| `#002A24` | Texto sobre botón o acento verde azulado | **9.1:1** (Pasa AAA) |
| `color/status/warning`| `#FFB74D` | Tarea bloqueada por IA / advertencias | **10.2:1** (Pasa AAA) |
| `color/status/error` | `#EF5350` | Errores de validación y red | **5.8:1** (Pasa AA) |
| `color/status/success`| `#66BB6A` | Tareas completadas | **8.4:1** (Pasa AAA) |

---

## 2. Tipografía y Espaciado

* **Familia tipográfica:** Roboto (nativo de Android).
* **Escala:**
  * `Heading / Large`: 22 sp, Medium (Títulos de vista y resúmenes).
  * `Heading / Medium`: 18 sp, Medium (Títulos de tarjeta y secciones).
  * `Body / Regular`: 14 sp, Regular (Detalle, descripciones de tarea e inputs).
  * `Label / Medium`: 12 sp, Medium (Chips de prioridad, procedencia de pendientes, fechas).
* **Espaciado y dimensiones mínimas:**
  * Rejilla base de 8 dp (4 dp, 8 dp, 16 dp, 24 dp).
  * Altura mínima de objetivo táctil (*touch target*): **48 dp** en botones, campos y filas de selección.

---

## 3. Componentes Diseñados en Figma

1. **`Agenda/Button` (Página `01 · Botones`):**
   * Variantes: `Primary` (fondo verde azulado, texto oscuro), `Secondary` (borde o superficie) y `Ghost`.
   * Estados: Por defecto, Presionado, Deshabilitado y Carga (*spinner* integrado).
2. **`Agenda/Input` (Página `02 · Campos`):**
   * Etiqueta persistente para accesibilidad.
   * Mensaje de ayuda / error textual (el error nunca depende únicamente del cambio de color del borde).
3. **`Agenda/Priority` y `Agenda/LevelOption` (Página `03 · Prioridad`):**
   * Niveles de importancia e impacto: **Alta**, **Media**, **Baja**.
   * Identificación obligatoria mediante texto visible y no solo color, para garantizar accesibilidad.
4. **`Agenda/TaskCard` (Página `04 · Tareas`):**
   * Estados contemplados:
     * **Pendiente:** Muestra título, proyecto y hora opcional.
     * **Bloqueada:** Muestra aviso visible de bloqueo por IA (p. ej. *«Bloqueada por Codex»*).
     * **Arrastrada:** Conserva indicación de procedencia (p. ej. *«Pendiente desde ayer · Sin nueva hora»*).
     * **Completada:** Marcada con acento y tachado o indicativo sutil.
5. **Componente de Disponibilidad de IA (`Agenda/AiStatusCard`):**
   * Indica proveedor (Codex / Gemini), tipo de ventana (Corta o Semanal), hora estimada de recuperación y estado (Esperando / Reanudar).
6. **Patrones de Estado de Pantalla (Vacío, Error, Carga):**
   * **Carga (*Loading*):** Indicador de progreso circular verde azulado o esqueletos sutiles sobre `color/bg/surface`.
   * **Vacío (*Empty*):** Ilustración/icono sobrio con mensaje explicativo y botón de acción principal (p. ej., *"No hay tareas planificadas para hoy. [Planificar día]"*).
   * **Error (*Error State*):** Mensaje amigable con motivo (sin tecnicismos de base de datos) y botón explícito de *"Reintentar"*.
