# Investigación de Límites y Recuperación de IA (Codex y Gemini)

**Objetivo:** Determinar la viabilidad técnica de detectar automáticamente los límites de uso de OpenAI (Codex / ChatGPT) y Google Gemini, evaluando APIs oficiales, autenticación, restricciones de seguridad y privacidad.

---

## 1. Productos y Modelos de Suscripción Analizados

El usuario interactúa con asistentes de IA tanto en computadora como en celular a través de interfaces de usuario y suscripciones de consumidor/desarrollador:

1. **Ecosistema OpenAI (ChatGPT Plus/Team/Pro y Codex):**
   * **Planes:** ChatGPT Plus, Team, Enterprise, ChatGPT Pro.
   * **Tipos de límites:**
     * **Ventanas cortas (Rolling limit):** Típicamente cupos de mensajes cada 3 o 4 horas (p. ej., mensajes dinámicos en modelos insignia como GPT-4o).
     * **Ventanas semanales/diarias:** Límites más estrictos para modelos de razonamiento avanzado (familia `o1`, `o1-mini`, `o3`), que imponen cuotas semanales de mensajes o tiempo de cómputo.
   * **Diferenciación API vs. Consumidor:**
     * La **API de Desarrolladores** (`api.openai.com`) utiliza créditos prepagos y expone cabeceras HTTP (`x-ratelimit-remaining-requests`, `x-ratelimit-reset-requests`).
     * Las **suscripciones de consumidor/asistente** (interfaz web, apps móviles y extensiones IDE) **no cuentan con una API pública de consulta de cuotas de usuario final**.

2. **Ecosistema Google Gemini:**
   * **Planes:** Gemini Free, Gemini Advanced (Google One AI Premium).
   * **Tipos de límites:**
     * Cuotas dinámicas basadas en capacidad de servidor y políticas de uso justo (*fair use*).
     * Bloqueos temporales con mensaje en pantalla indicando tiempo estimado de reactivación.
   * **Diferenciación API vs. Consumidor:**
     * Google AI Studio / Vertex AI cuentan con cuotas por minuto (RPM/TPM) y cuotas diarias administrables por Cloud Console.
     * El servicio de consumidor `gemini.google.com` y la app de Android no disponen de endpoints OAuth para que aplicaciones de terceros lean el estado de bloqueo o tiempo de recuperación de un usuario.

---

## 2. Evaluación de Fuentes Autorizadas y Mecanismos de Integración

| Criterio | OpenAI (Codex / ChatGPT) | Google Gemini |
| :--- | :--- | :--- |
| **API Pública de Suscriptor** | ❌ No existe API pública de estado de cuota para cuentas ChatGPT Plus/Pro. | ❌ No existe API pública de estado de cuota para Gemini Web/App. |
| **OAuth de Terceros para Cuotas** | ❌ Los alcances de OAuth no incluyen métricas de uso de chat interactivo. | ❌ Los alcances de Google Identity no exponen estado de cuota de Gemini. |
| **Detección Automática sin Credenciales** | ❌ Inviable sin inyectar extensiones de navegador que lean cookies de sesión o manipulen el DOM. | ❌ Inviable sin inspección de sesión o captura de pantalla/DOM. |
| **Riesgos de Seguridad / Privacidad** | Compartir cookies de sesión (`__Secure-next-auth`) o credenciales viola los términos de servicio y expone conversaciones privadas. | Inyectar tokens de cuenta de Google comprometería servicios críticos del usuario. |

---

## 3. Conclusión de Viabilidad por Producto

1. **Automatización de Detección:** **INVIABLE / RECHAZADA PARA V1.**
   * No existe una vía oficial, segura y autorizada (sin credenciales, cookies ni telemetría invasiva) para consultar programáticamente cuándo se restablece el acceso en las interfaces de consumidor.
   * Forzar un conector no oficial (p. ej. *scraping* o intercepción de peticiones locales) es frágil, inseguro y contraviene el principio de privacidad de la agenda ("nunca contraseñas, cookies ni conversaciones").

2. **Decisión de Producto:**
   * **Se mantiene el modelo de Registro Manual Guiado:**
     * El usuario ingresa la hora esperada o la duración de espera informada por la interfaz de la IA (p. ej. "vuelve en 2 horas y 15 min" o "disponible a las 18:30").
     * La aplicación permite asociar opcionalmente la tarea que quedó bloqueada y la decisión de continuar con la otra IA alternativa.
     * Android programa la alarma correspondiente de manera confiable e independiente.

3. **Recomendación para Futuras Iteraciones:**
   * Si en el futuro OpenAI o Google publican APIs oficiales de identidad con *scopes* de consumo de suscripción, se redactará una propuesta de cambio independiente en OpenSpec.
   * La versión actual no queda condicionada en absoluto a dicha automatización.
