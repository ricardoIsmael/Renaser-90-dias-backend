# Propuesta: Jev (TypeSafe AI) como router rápido del tool calling

**Estado:** **en espera** (decisión del usuario, 2026-09-23). Jev lleva una semana en el mercado
y todavía no tiene evaluaciones independientes ni en español. Se retoma cuando las haya. Mientras
tanto quedó el puerto `ClasificarIntencionPort` con su adaptador `noop` (sin conectar al chat).
El adaptador de embeddings **no se construyó**: se midió antes con Gemini (§8) y no alcanza. Sumar
Jev u otro clasificador después sigue siendo agregar un adaptador, sin tocar el dominio.
> Corregido 2026-09-23: decía "se construye el puerto con un adaptador de embeddings" y "nada de
> este documento está en el código".
**Fecha:** 2026-09-23 · **Origen:** pedido del usuario sobre el plan
`RENASER_OFFLINE_AI_SENIOR_PLAN(3).md` (§5.2 clasificador, §67 tool calling).

## 1. Qué es Jev (verificado en la documentación oficial, 2026-09-23)

Jev es de TypeSafe AI, fundada por ex-OpenAI. Salió en early access el 2026-09-15. Es un
"System One model": **no genera texto**. Recibe `state` (texto o JSON) más preguntas tipadas y
devuelve probabilidades calibradas.

| Tipo | Devuelve | Uso en Renaser |
|---|---|---|
| `choice` | opción elegida + `probabilities` + `confidence` | qué herramienta, qué hábito |
| `noul` | probabilidad de "sí" (0–1) | ¿pide explícitamente marcar algo como hecho? |
| `score` | nivel en una escala de hasta 10 | (no hace falta hoy) |

- Endpoint: `POST https://api.typesafe.ai/v1/systemone`, `Authorization: Bearer <key>`,
  `model: "jev-1.13.0"`.
- Latencia: "la mayoría ~100 ms" según TypeSafe; los reportes externos hablan de 70–500 ms.
- Precio: US$0.042 por millón de tokens de entrada; la salida es gratis.
- Límites: 1 200 req/min y 64k de contexto. **Los límites cambian sin aviso** (lo dice la
  página de modelos).
- Todas las preguntas de una llamada se evalúan **en paralelo** sobre el mismo `state`.
- SDKs oficiales: Python y JavaScript. **No hay SDK Java**; desde Spring va HTTP directo.

## 2. Límites que condicionan el diseño

1. **Solo nube.** No hay pesos ni versión on-device. **Jev no sirve para el modo offline**
   del plan. Offline sigue siendo T0/T1 local (solver, reglas y embeddings).
2. **Solo texto.** No recibe audio. La voz se transcribe antes (STT) y a Jev le llega el
   texto, idealmente con las N mejores hipótesis del STT dentro del `state`.
3. **Español no es su idioma fuerte.** La documentación dice: "English is the primary training
   language… other languages are handled but not equally well; test on your own content".
   **Hay que medir con frases es-PE reales antes de usarlo** (§6).
4. **Malo con números, fechas y conteos** (página *jaggedness* de Jev 1.13). Las fechas, horas
   y cantidades se resuelven en código, nunca en Jev.
5. **Ingenuo ante prompt injection**: trata los datos como no hostiles. Nunca decide
   autorización.
6. **El alias `jev-latest` se mueve solo.** Hay que fijar `jev-1.13.0`, porque los umbrales
   calibrados valen solo para esa versión.
7. **Privacidad**: la retención cero de datos (ZDR) es solo para clientes enterprise. El texto
   del aprendiz sale a un tercero en EE.UU. Hay que revisar el DPA antes de producción.
8. **Sin paper revisado por pares.** Las cifras de "193× más rápido" son benchmarks internos
   de TypeSafe.

## 3. Idea: Jev elige, el LLM solo conversa

Hoy el flujo previsto con Gemini es: el LLM recibe las 3 herramientas de
`CatalogoHerramientasAgente`, razona, pide una y Spring AI la ejecuta. La propuesta agrega un
paso antes:

```text
texto (o N-best del STT)
  → Jev: UNA llamada con varias preguntas en paralelo (~100 ms)
      intencion   : choice {consultar_habitos_del_dia, consultar_puntos_en_juego,
                             marcar_habito_completado, conversar, no_se_entiende}
      habito      : choice {registro_id → nombre del hábito de HOY del aprendiz}
      pide_marcar : noul "¿pide explícitamente registrar que YA lo hizo?"
  → código decide con umbrales por riesgo:
      lectura (R0) y confidence ≥ U_alto → ejecutar directo, sin razonamiento del LLM
      marcar (R2)                         → SIEMPRE confirmación ("¿Marco 'Tomar agua'?")
      confidence media                    → el LLM decide, pero solo ve la tool candidata
      confidence baja / no_se_entiende    → pedir aclaración
```

### Por qué ayuda con la voz mal transcrita

El `choice` de hábito no compara palabras: compara el sentido contra un **conjunto cerrado**,
que son los hábitos de hoy de *esa* persona. "ya tomé awa" contra {Tomar agua, Leer 10 páginas,
Meditar} tiene una sola respuesta sensata, y `confidence` dice cuánta seguridad hay. El
`registro_id` sale de las opciones que armamos nosotros, así que **el modelo no puede
inventarlo**. Ese es justo el riesgo que advierte el Javadoc de `MARCAR_HABITO_COMPLETADO`.

### Lo que NO cambia

- `EjecutarHerramientaAgenteUseCase.ejecutar(actorId, …)`: el actor sigue viniendo del JWT.
- La confirmación R2 del plan (§67G). Un 0.99 no reemplaza el "sí" del usuario.
- La autorización y la validación del dominio.

## 4. Dónde entraría en el código (hexagonal)

| Pieza | Capa | Nota |
|---|---|---|
| `ClasificarIntencionPort` | `rag/application/ports/out/ia/` | por intención de negocio, no "JevPort" |
| `IntencionClasificada` (record) | `rag/domain/model/herramienta/` | herramienta, hábito, confidence |
| `TypeSafeJevIntencionAdapter` | `rag/infrastructure/adapter/out/ia/` | `RestClient`, timeout ~800 ms |
| `NoOpIntencionAdapter` | idem | default; mantiene el comportamiento actual |
| Umbrales | config `renaser.ia.router.*` | ajustados con el dataset (§6), no a ojo |

Reglas del repo que aplican: **ninguna llamada a Jev dentro de `@Transactional`** (C-1), un
timeout o un 429/529 **cae al flujo LLM actual** sin fallar el turno, y feature flag apagado
por defecto.

## 5. Offline: qué hacer en lugar de Jev

Jev no corre en el teléfono. El equivalente local es el T1 del plan: un embedding on-device
(p. ej. EmbeddingGemma 308M, <200 MB de RAM cuantizado) con similitud contra los ejemplos de
cada tool y los hábitos del día, más un umbral de abstención. Usar el **mismo contrato**
(`IntencionClasificada`) en los dos modos permite comparar las métricas.

## 6. Antes de decidir: medir con datos propios

`scripts/ia/jev_eval.py` corre un dataset de frases es-PE (incluye errores típicos de STT) y
reporta exactitud, exactitud en las respuestas con alta confianza, tasa de abstención y
latencia p50/p95. Requiere `TYPESAFE_API_KEY`.

Criterio propuesto para seguir adelante (el usuario lo confirma o lo ajusta):
- `marcar_habito_completado` con confianza alta y hábito equivocado: **0 casos**.
- Exactitud ≥ 95% en las respuestas que superan el umbral de ejecución directa.
- p95 < 500 ms desde Lima.

## 7. Decisiones pendientes (del usuario, no del agente)

1. ¿Se acepta enviar texto del aprendiz a TypeSafe (EE.UU.) sin ZDR? Requiere revisar el DPA.
2. ¿Hay cuenta y API key de early access?
3. ¿Qué umbrales? Salen de §6, no se fijan antes de medir.

## Fuentes

- Documentación oficial: https://docs.typesafe.ai/ (API: `/api`, modelos: `/models`,
  confianza: `/confidence`, function calling: `/cookbooks/function_calling`, routing:
  `/patterns/confidence-routing`, límites: `/model-jaggedness/jev-1.13`, legal: `/legal`)
- Simon Willison, "Jev introduces a new shape of LLM" (2026-09-21):
  https://simonwillison.net/2026/Sep/21/jev/
- TechCrunch (2026-09-18): https://techcrunch.com/2026/09/18/a-new-kind-of-ai-model-from-a-chatgpt-inventor-is-thrilling-developers/
- LangChain, guía de Jev: https://www.langchain.com/blog/building-a-harness-with-jev
- N-best del STT para intención (+14–25% relativo): https://arxiv.org/abs/2001.05284
- Menos tools → mejor function calling en edge: https://arxiv.org/abs/2411.15399

## 8. Medición real del router con embeddings (2026-09-23)

`scripts/ia/router_eval.py`, `gemini-embedding-001` a 768 dimensiones con `taskType=SEMANTIC_SIMILARITY`,
24 frases es-PE (incluye errores de voz y trampas) contra 6 intenciones y 4 hábitos de fixture.
Mismo algoritmo que `ComparacionSemantica`.

| Métrica | Resultado |
|---|---|
| Exactitud total | 18/24 (75%) |
| Margen ≥ 0.06 (umbral seguro) | decide solo en 10/24, 0 errores, 0 marcas equivocadas |
| Margen ≥ 0.02 | decide en 19/24, 2 errores |
| Latencia | ~2.3 s por llamada de embeddings (3 lotes en 7 s) |

**Falla en lo que más importaba, la voz mal transcrita:** "ya tome awa" salió `conversar` y el hábito
resuelto fue "Leer" (margen 0.005); "ya ley" y "ya medite" tampoco se reconocieron como marcar. La
variante `RETRIEVAL_DOCUMENT` (la que usa hoy el backend) no se pudo medir: la key devolvió **429**
por cuota.

**Decisión: el router NO se conecta al chat.** Con un umbral seguro decide en menos de la mitad de
los mensajes, se equivoca justo en los casos de voz, y tarda lo mismo que un turno corto del modelo
de chat, que ya tiene las herramientas y resuelve esos casos con contexto. Queda el contrato
(`ClasificarIntencionPort`, adaptador `noop`) y el script para volver a medir si aparece algo mejor
(Jev con evaluaciones independientes, u otro modelo).

**Lo que sí ataca el problema de la voz:** sesgar el reconocedor con los nombres de los hábitos de la
persona (`contextualStrings` / `initialPrompt`, plan v2.1 §3.5), para que "awa" ni llegue a
transcribirse. Va en la app.

**Riesgo operativo detectado:** la key del entorno local respondió 429 con pocas llamadas. Si es la
misma cuota que usa el chat en producción, varios usuarios a la vez verán "el asistente está
saturado". Revisar el plan de facturación en Google AI Studio.
