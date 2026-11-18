# Sistema de diseño de Agenda

El tema claro es la referencia para la primera versión de Android. Figma incluye prototipos claro y oscuro con la misma jerarquía, tipografía y componentes maestros. Las páginas numeradas conservan el diseño anterior.

- [Sistema de diseño en Figma](https://www.figma.com/design/DNdKPG2y6fJvbhWwNLnJ6a?node-id=27-389)
- [Prototipo claro](https://www.figma.com/design/DNdKPG2y6fJvbhWwNLnJ6a?node-id=21-2)
- [Prototipo oscuro](https://www.figma.com/design/DNdKPG2y6fJvbhWwNLnJ6a?node-id=32-590)
- [Especificación del rediseño](../openspec/changes/agenda-visual-refresh/specs/mobile-experience/spec.md)

## Color y elevación

Usar la colección «Agenda / Colores» en Figma y seleccionar el modo Claro u Oscuro en el marco. La sombra «Agenda Claro / Elevación» distingue las tarjetas accionables. Reservar el gradiente de azul petróleo a verde para el resumen de Hoy.

### Claro

- **Fondos:** lienzo `#EEF5F3`, superficie `#FFFFFF`, acento menta `#DDEFE8`.
- **Texto:** principal `#112D2B`, secundario `#496460`.
- **Acciones:** verde petróleo `#006A55`, presionado `#005443`, fondo deshabilitado `#DDE9E6`. El botón principal usa texto blanco.
- **Información:** azul petróleo `#155C73` sobre azul claro `#E2F1F7`.
- **Espera y bloqueo:** ámbar `#86500E` sobre crema `#FFF0D7`.
- **Error:** rojo `#A23138` sobre rosa claro `#FCE8EA` o blanco.

El texto normal requiere al menos 4,5:1. En los pares principales, el blanco sobre la acción da 6,58:1, el texto secundario sobre menta da 5,37:1 y el ámbar sobre crema da 5,90:1. Complementar el color con texto para prioridad, bloqueo y recuperación.

### Oscuro

- El lienzo `#0C1819`, la superficie `#152729` y el acento profundo `#193936` separan los planos sin depender de la sombra.
- El texto principal usa `#E6F4F0` y el secundario `#A9C5BF`.
- La acción menta usa `#71DCC0` con texto `#082922`. La acción presionada usa `#4DB79D` con el mismo texto.
- Los avisos de información usan `#9CD7EE` sobre `#173541`. La espera usa `#FFD293` sobre `#392C1E`. El error usa `#FFB3B5` sobre `#412529` o sobre la superficie.

Los pares verificados incluyen texto sobre acción (9,40:1), texto secundario sobre tarjeta (8,44:1) y espera (9,59:1).

## Tipografía y dimensiones

Roboto con los estilos de texto «Agenda Claro». Los tamaños siguientes indican tamaño e interlineado en sp.

- **Pantalla:** 32/38, negrita.
- **Sección:** 24/30, negrita.
- **Título de tarjeta:** 16/21, negrita.
- **Cuerpo:** 14/20, regular.
- **Etiqueta:** 13/18, media.
- **Nota breve:** 12/16, media.

Usar la escala de espaciado de 4, 8, 12, 16, 24 y 32 dp. Los controles usan habitualmente 16 dp de radio y las tarjetas destacadas pueden llegar a 26 dp. Cada acción requiere un objetivo táctil de al menos 48 × 48 dp. Las tarjetas deben crecer y el contenido debe poder desplazarse con texto ampliado.

## Componentes y estados

Usar instancias de «Componentes maestros» al extender el prototipo.

- **Botones:** principal, secundario, presionado, foco y deshabilitado. Mantener la etiqueta visible en todos los estados.
- **Campos:** selección, foco y error. La etiqueta permanece visible y el error explica cómo corregirlo.
- **Etiquetas y filas:** representar impacto e importancia con texto de tres niveles. Distinguir tarea pendiente, bloqueada, arrastrada y completada.
- **Recuperaciones de IA:** mostrar herramienta y ventana, indicar el origen manual y distinguir «Hora prevista» de «Hora desconocida».
- **Selección y navegación:** mostrar días activos y destinos con iconos y texto. El fondo menta marca el destino actual.
- **Pantallas:** el prototipo incluye un estado de primer uso. Para Android, prever mensajes de error con reintento y retroalimentación durante la carga.

La validación de TalkBack, texto ampliado y objetivos táctiles se realiza en Android.
