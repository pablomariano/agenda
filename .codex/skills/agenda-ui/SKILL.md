---
name: agenda-ui
description: Implementar o ajustar la UI Android de Agenda traduciendo nodos Figma a Jetpack Compose según el tema, los componentes y los contratos del proyecto.
---

# UI de Agenda

## Antes de escribir Compose

1. Selecciona el nodo de la pantalla o componente en el archivo Agenda — Design System (clave DNdKPG2y6fJvbhWwNLnJ6a). La página de referencia del sistema es `26:389`. Abre una sesión MCP con `create_session` y pasa su identificador a las consultas.
2. Consulta `get_metadata` para identificar la jerarquía y las instancias, `get_variable_defs` para resolver los tokens del nodo y `get_screenshot` para ver su referencia visual (equivalente a `get_image` en otros conectores). Si faltan variables enlazadas, contrasta las propiedades con el componente maestro y la paleta. Si falta la imagen, pide una captura del nodo antes de implementar.
3. Obtén `get_design_context` siguiendo el skill Figma de diseño a código. Traduce la intención de auto-layout, estados y restricciones a Row, Column, Modifier y componentes Material 3. Usa cualquier salida React/Tailwind como referencia visual.
4. Antes de crear una pantalla, contrasta sus estados y acciones con las specs y tareas de `openspec/changes/agenda-android-personal/` y `agenda-visual-refresh/` (bajo el mismo directorio). Sigue el contrato desde `server/api/agenda.ts` hasta `AgendaApi.kt` y `PersonalAgendaModel.kt`. Comprueba carga, vacío, error, permisos y datos disponibles según el flujo. Identifica cualquier estado que requiera cambiar el contrato.

## Tokens y componentes

La fuente Android es `MainActivity.kt`, en el paquete `com.pablomariano.agenda`. Consume estos roles de `MaterialTheme.colorScheme`.

| Uso en Figma | Valor claro | Rol en Compose |
|---|---|---|
| Lienzo | #EEF5F3 | background |
| Superficie | #FFFFFF | surface, surfaceContainerHigh |
| Texto sobre acción | #FFFFFF | onPrimary |
| Acento menta | #DDEFE8 | primaryContainer |
| Acción verde petróleo | #006A55 | primary, onPrimaryContainer |
| Texto principal | #112D2B | onBackground, onSurface |
| Texto secundario | #496460 | onSurfaceVariant |

Roboto se resuelve mediante `FontFamily.SansSerif` de Android. Usa `MaterialTheme.typography`: displaySmall (32/38), headlineSmall (24/30), titleMedium (16/21), bodyMedium (14/20), labelLarge (13/18) y labelMedium (12/16), en sp de tamaño/interlínea.

Prohibido hardcodear colores o tamaños en pantallas nuevas o ajustadas. Reutiliza los componentes de `TodayScreen.kt` y `PersonalAgendaScreen.kt`. Cuando falte un token de dimensión, define el valor una vez en la capa visual compartida según la escala del diseño (4, 8, 12, 16, 24 y 32 dp). Mantén objetivos táctiles de al menos 48 dp.

## Implementación y comprobación

- Conserva MVVM, Jetpack ViewModel, corrutinas, Material 3, Compose BOM 2025.06.01 y compileSdk/targetSdk 35. Lee el estado observable existente del modelo. Para StateFlow usa collectAsStateWithLifecycle() en el contenedor y pasa estado y callbacks al contenido. El modelo actual usa mutableStateOf; comprueba la disponibilidad de lifecycle-runtime-compose al introducir un flujo.
- En API 35 gestiona WindowInsets en el contenedor que posee cada borde. Aplica statusBarsPadding() arriba, navigationBarsPadding() abajo e imePadding() en formularios que deben seguir accesibles con teclado. Si Scaffold ya entrega esos insets, aplica y consume su innerPadding una sola vez. Comprueba navegación por gestos y teclado abierto.
- En LazyColumn y LazyRow asigna key estable por identidad y contentType por tipo de fila. Reutiliza recursos vectoriales o ImageVector nativos. Excluye librerías pesadas de iconos, Coil, Retrofit, Ktor, Room y Hilt/Dagger.
- Crea @Preview del contenido con datos mock deterministas y callbacks inertes en el entorno de preview/debug con tooling disponible. Para capturas en Android usa la demo indicada en AGENTS.md. Contrasta con la imagen MCP, verifica texto ampliado, foco, contraste, áreas táctiles y estados de la pantalla.
