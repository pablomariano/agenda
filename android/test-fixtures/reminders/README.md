# Prueba aislada de recordatorios

Solo en un emulador de pruebas sin datos personales, compila con `-I test-fixtures/reminders/init.gradle`, instala el APK, concede `POST_NOTIFICATIONS` y `SCHEDULE_EXACT_ALARM`, y ejecuta:

```sh
adb shell am broadcast -n com.pablomariano.agenda.preview/com.pablomariano.agenda.ReminderTestReceiver
adb logcat -d -s AgendaReminderTest:I '*:S'
```

El receptor verifica conversión de hora, cambio horario, desactivación, restauración, posposición, reconciliación y cancelación; deja un aviso de prueba para comprobar la entrega. Busca `AgendaReminderTest: PASS` y la notificación `Prueba`. El receptor de prueba se incorpora únicamente al compilar con el archivo `init.gradle`: recompila sin `-I` antes de distribuir el APK.

Las alarmas exactas requieren acceso especial en Android 12 y posteriores; sin él pueden llegar tarde. En Android 13 y posteriores se requiere además permiso para notificaciones. La aplicación no puede garantizar avisos si Android los restringe; tras forzar la detención de la app, Android no ejecuta sus alarmas hasta que el usuario la vuelva a abrir. Los horarios usan America/Santiago; horas inexistentes o repetidas por cambio horario se rechazan al guardar.
