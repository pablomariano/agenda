# Spec Delta

## Purpose

Mantener la agenda personal protegida y persistente, con acceso autenticado desde la aplicación Android.

## ADDED Requirements

### Requirement: Acceso solo del propietario
El sistema SHALL usar un código privado de Agenda generado aleatoriamente para el propietario, sin depender de una cuenta externa. La API SHALL exigir el código por HTTPS y verificarlo en cada operación. El propietario SHALL poder revocarlo sin perder sus datos.

#### Scenario: Acceso ajeno
- **WHEN** una solicitud sin código válido o con un código revocado intenta consultar o modificar tareas
- **THEN** se rechaza sin exponer información de la agenda.

#### Scenario: Reabrir la agenda privada
- **WHEN** el propietario introduce un código válido y vuelve a abrir la app en el mismo teléfono
- **THEN** la app usa el código guardado cifrado y vuelve a comprobar el acceso con el servidor.

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
