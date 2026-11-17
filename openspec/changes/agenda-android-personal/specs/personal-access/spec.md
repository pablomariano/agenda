# Spec Delta

## Purpose

Mantener la agenda personal protegida y persistente, con acceso autenticado desde la aplicación Android.

## ADDED Requirements

### Requirement: Acceso solo del propietario
El sistema SHALL autenticar al usuario y permitir acceso únicamente al propietario configurado. SHALL validar sesión y propiedad en cada operación de datos, sin confiar en identificadores enviados por el cliente.

#### Scenario: Acceso ajeno
- **WHEN** una solicitud sin sesión válida o de una cuenta no autorizada intenta consultar o modificar tareas
- **THEN** se rechaza sin exponer información de la agenda.

### Requirement: Persistencia confirmada y errores recuperables
El sistema SHALL guardar los datos en Neon mediante una API en Vercel. La app SHALL confirmar éxito únicamente tras aceptar el servidor la operación y conservar los campos introducidos en pantalla ante fallos recuperables. No se requiere edición sin conexión.

#### Scenario: Error de red
- **WHEN** falla la conexión durante el guardado de una tarea
- **THEN** se informa que no se confirmó el guardado y se permite reintentar sin perder el formulario ni duplicar el registro.

### Requirement: Secretos fuera del cliente
El sistema SHALL mantener las credenciales de base de datos y secretos del servidor fuera del APK, repositorio y registros. SHALL permitir cerrar sesión y cancelar avisos personales locales al hacerlo.

#### Scenario: Sesión cerrada
- **WHEN** el usuario cierra sesión
- **THEN** ya no puede consultar la agenda sin autenticarse y no quedan avisos personales programados por la app.
