# Agenda

## Herramientas MCP

- Para implementar o ajustar UI Android, lee [.codex/skills/agenda-ui/SKILL.md](.codex/skills/agenda-ui/SKILL.md) y sigue su flujo de Figma MCP y OpenSpec.
- Usa `modern-jetpack-compose` y `figma-to-compose` como referencias complementarias. Ante diferencias, prevalece `agenda-ui` para tokens, componentes y contratos de estado. Conserva MVVM con Jetpack ViewModel y corrutinas; adapta los ejemplos a las dependencias existentes.
- Con Android ADB MCP, usa exclusivamente la demo para validación visual y capturas. Desde `android/`, compila con `./gradlew :app:assembleDebug -PagendaApiUrl=`. Verifica que el APK declara `com.pablomariano.agenda.demo` antes de instalarlo en el dispositivo de prueba. Las alarmas y notificaciones requieren comprobación manual en un dispositivo real antes de darlas por verificadas.
- PostgreSQL MCP y pruebas de base de datos solo pueden usar `TEST_DATABASE_URL` de PostgreSQL local. Si falta o el destino no es local, detén esa operación. Nunca consultes ni modifiques `DATABASE_URL` ni ejecutes `npm run access:generate`.
