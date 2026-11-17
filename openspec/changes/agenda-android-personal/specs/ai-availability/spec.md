# Spec Delta

## Purpose

Registrar cuándo se espera recuperar el acceso a Codex o Gemini y recordar ese momento sin depender del estado de una tarea.

## ADDED Requirements

### Requirement: Recuperación por herramienta y ventana
La aplicación SHALL permitir registrar Codex o Gemini, tipo de ventana corta o semanal y una fecha/hora de recuperación o duración positiva de espera. SHALL mostrar origen manual, momento de registro, zona horaria y próxima recuperación. SHALL rechazar fechas pasadas y entradas ambiguas sin confirmación.

#### Scenario: Duración de espera
- **WHEN** el usuario registra a las 10:00 una espera de cinco horas para Codex
- **THEN** se guarda la recuperación a las 15:00 del mismo día y se programa su aviso, sin asumir esa duración para otros límites.

#### Scenario: Ventana semanal
- **WHEN** se registra una recuperación semanal y otra de ventana corta para la misma IA
- **THEN** ambas se conservan con sus propios horarios; recuperar una no declara resuelta la otra.

#### Scenario: Hora sin fecha suficiente
- **WHEN** la hora introducida ya pasó en el día actual
- **THEN** se solicita una fecha futura explícita en lugar de asumir automáticamente mañana.

### Requirement: Esperar o continuar sin perder el aviso
La aplicación SHALL permitir registrar la decisión de esperar o continuar con otra IA junto al límite, con asociación opcional a una tarea. SHALL conservar el recordatorio de la IA original aunque se cambie de herramienta, se complete una tarea o no haya tarea asociada.

#### Scenario: Cambio a Gemini
- **WHEN** el usuario registra un límite de Codex y decide continuar con Gemini
- **THEN** esa decisión queda registrada y el aviso de recuperación de Codex sigue programado.

### Requirement: Corrección y estado honesto
La aplicación SHALL permitir corregir o cancelar una recuperación y reemplazar o cancelar su aviso. Al vencer el horario SHALL indicar que se alcanzó la hora registrada, sin afirmar que los tokens o el acceso se verificaron con el proveedor.

#### Scenario: Recuperación corregida
- **WHEN** se cambia la recuperación de las 15:00 a las 16:00
- **THEN** solo queda activo el aviso de las 16:00.

#### Scenario: Hora alcanzada
- **WHEN** llega la hora registrada manualmente
- **THEN** se avisa al usuario y se muestra como recuperación prevista alcanzada, sin desbloquear tareas ni inventar una cuota disponible.

### Requirement: Alternativa manual y privacidad
La versión inicial SHALL funcionar sin conectar cuentas de IA ni recibir contraseñas, cookies de sesión o conversaciones de esos servicios. SHALL mostrar la detección automática como no disponible hasta verificar una integración y SHALL NOT calcular cuotas o reinicios semanales a partir de supuestos no confirmados.

#### Scenario: Sin integración automática
- **WHEN** no existe una fuente verificada del límite semanal de Gemini
- **THEN** se muestra como desconocido y se permite registrar manualmente su próxima recuperación.
