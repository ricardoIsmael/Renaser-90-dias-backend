# Spec 060 — Cumplimiento de tiendas

**Rama**: `060-cumplimiento-tiendas` (backend y app; el manifiesto del widget y los permisos de Android van en las ramas `059-widget-acciones-de-hoy` y `060-config-nativa` de la app)

**Creada**: 2026-11-27

**Estado**: En curso

**Input**: la revisión de la app contra el playbook https://github.com/mjmirza/app-store-compliance (patrones de `data/rejection-patterns.json`), tras los rechazos de la App Store del 24/11 (2.5.4 y 2.5.1). Se leyó solo como datos; no se ejecutó nada de ese repositorio.

## Por qué

Apple y Google rechazan con frecuencia por cosas que no son de código sino de lo que la app dice y declara: no avisar que los datos van a una IA de un tercero (Apple 5.1.2(i)), etiquetas de privacidad incompletas, notas del revisor sin las instrucciones que justifican un permiso, permisos de Android de más y manifiestos de privacidad que faltan. Esta spec junta lo que hay que corregir y dejar escrito para no repetir un rechazo.

## Escenarios de aceptación

### Historia 1 — Aviso de la IA antes del primer envío (P1)

1. **Dado** una persona que ya pasó el onboarding y no aceptó el aviso en ese teléfono, **cuando** abre el chat con el acompañante, **entonces** ve «Antes de hablar con tu acompañante» con el texto que nombra a Google (Gemini) y los datos que viajan, y «Acepto y continuar» / «Ahora no»; no se pide el historial ni se envía nada hasta aceptar.
2. **Cuando** toca «Ahora no», **entonces** el panel se cierra y no se envía nada.
3. **Dado** la misma persona, **cuando** toca el orbe de voz de Hoy por primera vez, **entonces** la voz no empieza: se abre el mismo aviso y, al aceptar, la conversación sigue sola; con «Ahora no» no pasa nada más.
4. **Dado** que ya aceptó, **entonces** no se le vuelve a pedir en esa cuenta y ese teléfono; en otro teléfono se pide una vez.
5. **Dado** una persona nueva en el onboarding, **entonces** el texto de la casilla de datos ya nombra a Google (Gemini) y lo que se le envía.

### Historia 2 — Documento para las consolas (P1)

El dueño o quien suba la versión abre `docs/tiendas/notas-y-declaraciones.md` y copia las notas del revisor, las etiquetas de privacidad, la Data safety, la declaración de red social, el cuestionario de edad y la lista de verificación.

### Historia 3 — Permisos y manifiestos nativos (P1)

Los permisos de Android que la app no usa se bloquean y los manifiestos de privacidad de iOS (app y widget) están completos (FR-010 a FR-022).

## Requisitos funcionales

**Consentimiento de la IA (app, Apple 5.1.2(i), patrón `APPLE-5.1.2-AI-NO-CONSENT-MODAL`)**

- **FR-001**: El panel de SER (`RenasiaPanel`, que montan el botón flotante, el orbe de Hoy y el botón del curso) no muestra ni envía la conversación hasta que la persona acepta el aviso de IA; sin aceptar, «Ahora no» cierra el panel.
- **FR-002**: La voz del orbe (`useVozDelOrbe`) espera al mismo aviso la primera vez; el aviso de la voz es una hoja que monta `RenasiaLauncher` (no se toca ningún tab). Con una sesión de voz ya abierta, tocar sigue siendo «ya terminé» sin preguntar.
- **FR-003**: El aviso nombra al proveedor (Google, Gemini) y dice qué datos viajan: los mensajes con el acompañante, la voz en la conversación en vivo y las respuestas del diagnóstico, junto con el contexto del programa (hábitos de hoy y objetivos); dice que no se envían fotos ni pagos.
- **FR-004**: La aceptación se guarda en el teléfono, por cuenta y por versión del texto (`renaser.ia.consentimiento.<userId>`); sin servidor nuevo. Cualquier valor ilegible cuenta como «sin aceptar».
- **FR-005**: El texto de la casilla de datos del onboarding (`ChapterConsentimiento`) nombra al proveedor y los datos.

**Documento para las consolas**

- **FR-006**: `docs/tiendas/notas-y-declaraciones.md` trae las notas para el revisor de Apple y de Google: qué es la app, la cuenta de demostración (sin escribir la contraseña), el widget, EAS Update, los interruptores del servidor, el paso a paso de «Correr con GPS», «Mi ingreso» sin cobro, la Pastilla como reproductor del sistema y Salud de Apple / Health Connect solo lectura de pasos y distancia. [sin prueba automática: es un documento]
- **FR-007**: Trae las etiquetas de privacidad de Apple y la Data safety de Google con todos los datos que la app pide, incluidos teléfono/WhatsApp, dirección, fecha de nacimiento y DNI. [sin prueba automática: se contrasta con el código y la política en cada subida]
- **FR-008**: Trae las respuestas de la declaración de red social de Apple y del cuestionario de edad nuevo. [sin prueba automática: es un documento]
- **FR-009**: Trae la lista de verificación antes de cada subida, con el patrón del playbook en cada punto, incluidos declarar `SCHEDULE_EXACT_ALARM` en Play Console y restringir la clave de Maps por paquete y huella. [sin prueba automática: es un documento]

**Permisos de Android (rama `060-config-nativa`, prueba `src/__tests__/permisosDeAndroid.test.ts`)**

- **FR-010**: Se bloquean `SYSTEM_ALERT_WINDOW` y `WRITE_EXTERNAL_STORAGE`.
- **FR-011**: No hay permisos `READ_MEDIA_*`.
- **FR-012**: Siguen pedidos el audio, el primer plano, la alarma exacta y Health Connect, este solo con `READ_STEPS` y `READ_DISTANCE`.
- **FR-013**: No se bloquean la cámara, el GPS, la actividad ni las notificaciones.
- **FR-014**: La orientación sigue vertical.
- **FR-015**: El GPS solo se pide en primer plano.

**Revisión del manifiesto del AAB**

- **FR-016**: Antes de subir el AAB se revisa su manifiesto final (`bundletool dump manifest`) contra FR-010 a FR-015 y se anota el resultado en el PR. [sin prueba automática: se hace sobre la build]

**Manifiesto de privacidad del widget (rama `059-widget-acciones-de-hoy`, prueba `src/__tests__/manifiestoDePrivacidadDelWidget.test.ts`)**

- **FR-020**: La app declara UserDefaults con las razones CA92.1 y 1C8F.1 en `ios.privacyManifests`.
- **FR-021**: La extensión `ExpoWidgetsTarget` lleva su propio `PrivacyInfo.xcprivacy` (UserDefaults 1C8F.1, sin rastreo ni datos recolectados), puesto por `plugins/withPrivacidadDelWidget.js`.
- **FR-022**: El plugin va antes de `expo-widgets` en `app.json`.

**Transversal**

- **FR-030**: Textos en español de Perú, con tuteo y tipo oración, sin emojis; toques de 48 de alto.
- **FR-031**: Quien marca la casilla de datos del onboarding (que ya trae el aviso de IA) queda con el aviso aceptado, con la misma versión del texto y por cuenta, sin servidor nuevo: no ve el consentimiento dos veces.

## Criterios de éxito

- **SC-001**: Ningún dato sale hacia Gemini desde la app sin que la persona haya aceptado el aviso en ese teléfono.
- **SC-002**: El documento de las consolas coincide con el código y la política de privacidad en la fecha de cada subida.
- **SC-003**: El E2E de Maestro `.maestro/consentimiento-de-ia/01-aviso-antes-del-primer-chat.yaml` corre en verde en el emulador.

## Lo que no cubre

- **Denunciar y bloquear en Comunidad** (Apple 1.2, patrón `APPLE-1.2-UGC-24H-ACTION`): **pendiente de la autorización del dueño**, porque Comunidad es uno de los cinco tabs. Propuesta con el modelo de Skool: «Denunciar a Administración» en cada publicación y comentario; bandeja solo para Administración y el Alquimista; la publicación se oculta sola con 3 denuncias; y bloquear a una persona. No se hace hasta que el dueño lo autorice y se anote en `docs/decisiones/`.
- Guardar el consentimiento de la IA en el servidor (hoy es del teléfono; en otro teléfono se pide una vez más).
- Un aviso de IA distinto para el análisis 80/20 (se genera en el servidor sin que la persona escriba nada; lo cubre la política y el texto del onboarding).
