# Propuesta: conversación por voz en tiempo real con Gemini Live

**Estado:** aprobada por el dueño el 2026-09-24, con las decisiones de §8 tomadas. En construcción por fases (§5).
**Avance (2026-09-24):** Fase 1 (backend) y la parte de backend de la Fase 3 (historial, cuota, D-162)
hechas, detrás del interruptor apagado. Probado contra Gemini Live real con herramientas. Falta la
app (Fase 2) y la prueba en un teléfono real.
**Fecha:** 2026-09-24 · **Origen:** pedido del dueño ("la voz tiene que salir a la par del texto").

## 1. Por qué

Hoy el orbe encadena tres servicios uno detrás de otro (D-157 a D-159, E-231, E-232):

```text
voz → STT del teléfono → texto → chat (Gemini, streaming) → oración → TTS (Gemini Kore) → audio
```

La voz no puede salir a la par del texto, porque el TTS recién arranca cuando llega el texto. Se
optimizó hasta donde da el enfoque:
- recorte de silencios;
- la primera oración sola;
- el resto agrupado;
- descarga completa para que no haya cortes.

Aun así la primera palabra llega **~3–4 s** después de que la persona terminó de hablar, y la voz
queda **~2 s** detrás del texto. Además no se la puede interrumpir.

**Gemini Live** es un solo modelo que escucha y habla: recibe el audio de la persona y devuelve
audio mientras lo genera, con la transcripción de las dos partes. Es la forma estándar de hacer un
asistente de voz conversacional.

## 2. Qué da Gemini Live (verificado en la documentación oficial, 2026-09-24)

| Punto | Dato |
|---|---|
| Modelo | `models/gemini-3.8-live` (audio nativo) |
| Conexión | WebSocket `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent` |
| Audio de entrada | PCM 16 bits, 16 kHz, mono (`audio/pcm;rate=16000`) |
| Audio de salida | PCM 16 bits, **24 kHz**, mono, en pedazos de ~40 ms |
| Voz | `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName = "Kore"` (la misma que ya eligió el dueño) |
| Texto | `outputAudioTranscription` e `inputAudioTranscription`: transcripción de las dos partes |
| Interrupción | Sí. Si la persona habla, el modelo se calla (`serverContent.interrupted`) |
| Detección de voz | Automática (`realtimeInputConfig.automaticActivityDetection`, `silenceDurationMs`) |
| Herramientas | `toolCall.functionCalls[{id,name,args}]` → `toolResponse.functionResponses[{id,name,response}]`; no bloqueantes por defecto |
| Límites | Sesión de solo audio: **15 min**. Contexto de 128k tokens |
| Idioma | 99 idiomas; español incluido |
| Costo | ~US$0,84 por hora de audio de entrada, más la salida (25 tokens/s de audio) |

## 3. Arquitectura propuesta: el backend como intermediario (proxy)

```text
App (micrófono 16 kHz con cancelación de eco)
  ⇄ WebSocket propio  /api/v1/renasia/voz/en-vivo  (sesión de la app, X-Auth-Token)
     Backend (rag): arma la sesión, ejecuta herramientas, guarda el historial, cuenta la cuota
  ⇄ WebSocket Gemini Live (API key solo en el servidor)
```

### Por qué proxy y no conexión directa con token temporal

Google ofrece tokens efímeros para que la app se conecte directo. Se descarta como primera opción:

| | Proxy (propuesto) | Directo con token efímero |
|---|---|---|
| API key | nunca sale del servidor | sale un token de un solo uso |
| Herramientas | se ejecutan en el backend, con el actor de la sesión | la llamada llega **a la app**, que tendría que pedirle al backend que la ejecute: más saltos y una superficie que la app puede falsificar |
| Prompt y reglas | armados en el servidor, como hoy | hay que bloquearlos en el token (`lock_additional_fields`) |
| Historial y cuota | el backend ve todo | la app tendría que informarlo |
| Latencia extra | un salto más (~20–50 ms en la misma región) | ninguna |
| Audio 24 kHz → 16 kHz | lo hace el backend | la app |

La diferencia de latencia no se siente frente a los segundos que se ganan. Las reglas del repo
salen solas: el controller no hace lógica, no hay llamadas a IA dentro de `@Transactional` (C-1) y
cada escritura sigue siendo una propuesta con botón (D-153).

### Piezas del backend (módulo `rag`, hexagonal)

| Pieza | Capa | Qué hace |
|---|---|---|
| `ConversacionEnVivoPort` | `application/ports/out/ia` | "abrí una conversación en vivo": mandar audio, recibir audio, texto, pedidos de herramienta y fin |
| `GeminiLiveAdapter` | `infrastructure/adapter/out/ia` | cliente WebSocket contra Gemini Live (setup, `realtimeInput`, `toolResponse`) |
| `SesionDeVozService` | `application/services` | arma el setup (prompt del acompañante en modo voz, herramientas del catálogo existente, voz Kore), ejecuta las herramientas con `HerramientasAgenteService`, guarda la transcripción en `mensajes_renasia`, controla la cuota |
| `VozEnVivoWebSocketHandler` | `infrastructure/adapter/in/websocket` | el WebSocket de la app: autentica, sin lógica, delega en el caso de uso |

> **Corregido 2026-09-24 (al construirlo).** La ruta del diagrama decía `/api/v1/renasia/live`; la que
> vale es la de §5.ter, `/api/v1/renasia/voz/en-vivo`. `SesionDeVozService` quedó repartido en
> `ConversacionEnVivoService` (caso de uso `ConversarEnVivoUseCase`), `SesionDeVozEnVivo` (una
> conversación viva), `TiempoDeVozEnVivo` (cuota) y `TurnosDeVozEnVivo` (historial), en
> `application/services/vozenvivo`. El prompt lo arma el adaptador (`PromptDeVozEnVivo`), igual que en
> el chat. Detalle en D-162 de `docs/MODULO_RAG.md`.

**Se reusa todo lo que ya existe:**
- las **mismas herramientas** (`HerramientaAgente`), incluidas `buscar_huecos_para_habitos` y la agenda;
- el **mismo prompt** del acompañante con el bloque de modo voz (D-158);
- las **mismas propuestas con botón**: la voz nunca confirma nada. El orbe dice "te dejé la
  propuesta en el chat", como hoy.

## 4. Piezas de la app

- **Audio nativo:** `@speechmatics/expo-two-way-audio`, un módulo Expo que graba PCM de 16 kHz
  **con cancelación de eco** (sin ella, el micrófono escucha al propio orbe y se interrumpe solo) y
  reproduce PCM. **Requiere un binario nuevo** (`npm run android` y reinstalar).
- `useConversacionEnVivo`: abre el WebSocket, manda el audio del micrófono, reproduce el que llega
  y muestra la transcripción a la par.
- El orbe reusa sus fases: escuchando, pensando, hablando. Tocarlo mientras habla lo interrumpe.
- **Respaldo:** si el binario no trae el módulo nuevo (una app vieja, sin actualización por aire) o
  el WebSocket falla, queda el flujo actual (STT, chat y TTS), que ya funciona.

## 5. Fases

| Fase | Qué | Cómo se verifica |
|---|---|---|
| 0 | Prueba de concepto en el backend: un script abre Gemini Live con la key, manda un WAV y mide el primer audio | tiempo real medido; la voz Kore suena en español |
| 1 | Backend: puerto, adaptador y servicio con herramientas; WebSocket propio con auth | pruebas unitarias; IT con un Gemini falso; 403 sin sesión y para una cuenta suspendida |
| 2 | App: módulo de audio nativo, hook y orbe; binario nuevo | emulador: latencia, interrupción, eco |
| 3 | Historial, cuota y documentación (D-162) | `clean verify`; prueba en un teléfono real |

Todo detrás de un interruptor (`renaser.ia.voz.en-vivo`, apagado por defecto), en la rama
`acompanante-ia`.

> **Corregido 2026-09-24.** El interruptor es `renaser.ia.voz.en-vivo.activa` (`IA_VOZ_EN_VIVO`): en
> YAML `en-vivo` no puede ser a la vez un valor y el bloque con `modelo`, `voz` y la cuota.

## 5.bis Fase 0 hecha: medición real (2026-09-24)

Con la key del proyecto, `models/gemini-3.8-live`, voz Kore y un script de Python por WebSocket:

| Medición | Resultado |
|---|---|
| Abrir la sesión (`setup` → `setupComplete`) | 3,8 s. Se hace una sola vez, al abrir el orbe, no en cada pregunta |
| Pregunta en texto → primer audio | **1,2 s** |
| **Audio real a 16 kHz → fin del habla → primer audio** (incluye la detección de fin de habla) | **1,4 s** |
| Transcripción de entrada y de salida | Llegan a la par del audio y en español |

Contra el flujo actual (~3–4 s a la primera palabra y la voz ~2 s detrás del texto), se confirma la
mejora que justifica el cambio.

## 5.ter Contrato entre la app y el backend (WebSocket propio)

`wss://…/api/v1/renasia/voz/en-vivo`, autenticado con el mismo `X-Auth-Token` en el handshake
(`USE_APP`; una cuenta suspendida se rechaza).

- **Audio:** frames **binarios** en los dos sentidos, PCM 16 bits mono **16 kHz**. El backend baja
  a 16 kHz el audio de 24 kHz de Gemini, para que la app use un solo formato (el de
  `expo-two-way-audio`).
- **Eventos:** frames de **texto** JSON `{"tipo": …}`.

| Dirección | `tipo` | Campos | Cuándo |
|---|---|---|---|
| backend → app | `listo` | `segundosRestantes` | la sesión con Gemini quedó abierta; queda esa cuota del día |
| backend → app | `oido` | `texto` | transcripción de lo que dijo la persona (se acumula) |
| backend → app | `dicho` | `texto` | transcripción de lo que dice el acompañante, a la par del audio |
| backend → app | `interrumpido` | — | la persona habló encima: la app corta lo que suena |
| backend → app | `turnoCompleto` | — | terminó de responder |
| backend → app | `propuesta` | `id`, `resumen`, `venceEn` | igual que en el SSE del chat (D-153); se confirma con el botón |
| backend → app | `cuotaAgotada` | — | se acabaron los 10 min del día; la app vuelve al flujo anterior |
| backend → app | `error` | `valor` | texto apto para mostrar; después se cierra |
| app → backend | `fin` | — | la persona cerró el orbe |

Se guarda en `mensajes_renasia` (agente COMPANION) cada turno completo: lo que dijo la persona
(`oido`) y lo que respondió (`dicho`).

**Agregado al construirlo (2026-09-24), sin cambiar lo de arriba:**

- **Cierres.** 1000 normal; 1000 con motivo `cuota-agotada` (después de `cuotaAgotada`); 1013
  `no-disponible` (voz en vivo apagada o Gemini que no abre, después de `error`): la app vuelve al
  flujo anterior; 1011 `error` (después de `error`).
- **Autenticación.** El handshake es un `GET` con el header `X-Auth-Token`. Sin sesión, o con una
  cuenta suspendida o sin `USE_APP`, es **403** y no hay socket.
- **Cuándo mandar audio.** Después de `listo`. Frames de ~100 ms (3200 bytes); el backend junta los
  frames que Tomcat entrega partidos, hasta 1 MB (E-234).
- **`dicho` también lleva el texto de apoyo** cuando se repite el malestar, igual que el chat (va
  separado por un salto de párrafo y no se dice en voz alta).
- **`turnoCompleto` llega una vez por respuesta**, aunque en el medio el modelo haya usado una
  herramienta (Gemini manda dos; el backend filtra el intermedio).

## 6. Riesgos

- **Modelo nuevo en una API en vivo:** la forma de los mensajes puede cambiar. Por eso va detrás
  de un puerto propio y con respaldo al flujo actual.
- **Eco en teléfonos baratos:** la cancelación de eco varía por equipo. Hay que probar en un
  teléfono real, no solo en el emulador.
- **Sesiones de 15 min:** alcanza para una conversación del orbe. Si se corta, se reconecta.
- **WebSockets largos en una sola EC2:** cada conversación mantiene dos sockets. A esta escala está
  bien; hay que vigilarlo si crece.
- **Privacidad:** el audio de la persona va a Google, igual que el texto hoy. El audio no se
  guarda; se guarda solo la transcripción.

## 7. Lo que no cambia

- El chat escrito sigue igual.
- Las propuestas se siguen confirmando con botón (D-132, D-153).
- Crisis y límites clínicos del prompt: iguales.
- Producción: nada, hasta que el dueño decida pasar la rama.

## 8. Decisiones del dueño (tomadas el 2026-09-24)

1. **Pasando por el backend (proxy).** La key no sale del servidor, las herramientas y la cuota se
   controlan ahí y el historial se guarda solo.
2. **Cuota: 10 minutos de conversación por voz, por persona y por día.** Pasado el límite, el orbe
   vuelve al flujo actual (STT, chat y TTS).
3. **Se guarda la transcripción** de lo que se habla en `mensajes_renasia`, solo el texto. El
   audio no se guarda nunca.

## Fuentes

- [Gemini Live API: overview](https://ai.google.dev/gemini-api/docs/live-api)
- [Live API: capabilities](https://ai.google.dev/gemini-api/docs/live-api/capabilities)
- [Live API: WebSocket](https://ai.google.dev/gemini-api/docs/live-api/get-started-websocket)
- [Ephemeral tokens](https://ai.google.dev/gemini-api/docs/ephemeral-tokens)
- [Precios de la API de Gemini](https://ai.google.dev/gemini-api/docs/pricing)
- [expo-two-way-audio (Speechmatics)](https://github.com/speechmatics/expo-two-way-audio)
- [Foro de Google: reproducir el PCM de 24 kHz de Live en Expo](https://discuss.ai.google.dev/t/best-practices-for-playing-gemini-live-apis-24khz-pcm-audio-stream-in-expo-react-native/95569)
