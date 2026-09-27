# Estado del proyecto — Renaser OS

**Fecha:** 2026-09-04
**Alcance:** qué hay construido, qué funciona de verdad, qué está escrito pero desconectado, y qué falta decidir.

Este documento existe porque "está implementado" y "funciona" no son lo mismo, y en este repo la
diferencia es grande en varios lugares. Cada afirmación de acá está verificada contra el código o
contra la base corriendo — no contra la memoria ni contra lo que dicen otros documentos.

> **Documentos hermanos:** [`CLAUDE.MD`](../CLAUDE.MD) (cómo se construye),
> [`MODULOS_A_AVANZAR.md`](MODULOS_A_AVANZAR.md) (qué construir y el registro de decisiones),
> [`BITACORA_ERRORES.md`](BITACORA_ERRORES.md) (errores y su causa real).

---

## 1. En una pantalla

| | |
|---|---|
| Backend | Java 25 · Spring Boot 4.1 · monolito modular hexagonal · **15 módulos** |
| Base | PostgreSQL propia (`pgvector/pgvector:pg16`): RDS en producción, Docker local (puerto 5433) en desarrollo · **94 tablas** · **22 migraciones** Flyway |
| API | **230 endpoints** |
| Pruebas backend | **4889 unitarias + 130 de integración en verde** (`./mvnw clean verify`, 2026-09-27) |
| Pruebas frontend | **113 suites / 972 pruebas** de Jest y 27 de Playwright (rama `evidencia-foto`, 2026-09-27) |
| Cliente | React Native (repo aparte) |
| Entorno desplegado | **EC2 (t3.small) + RDS en AWS** (`docs/DESPLIEGUE_Y_CI.md` §5.3 y §9) |

> **Corregido 2026-09-27.** Esta tabla decía: base «Docker local puerto 5433» (sin producción), «**2224 en verde**
> (`./mvnw clean test`)» (la regla es `clean verify` desde D-114), pruebas frontend «**Ninguna.** Sin script `test`, sin
> un solo archivo `.test.tsx`» y entorno desplegado «**No existe.** El backend corre en la laptop del dueño». Los
> conteos de tablas, migraciones y endpoints son del 2026-09-04 y no se revisaron en esta corrección.

---

## 2. Lo que funciona de verdad

Verificado end-to-end contra el backend corriendo, no solo con tests.

- **El reloj del programa de 90 días.** `dia_programa` se **deriva** de las fechas
  (`(hoy_en_su_zona − fecha_inicio) + 1 − dias_ajuste_programa`, acotado a [0,90]), no se acumula.
  Es idempotente y se pone al día solo tras cualquier caída. Barrido **horario** (D-81).
- **Ajustar el día de un aprendiz** — `PUT /api/v1/admin/trainees/{id}/program-day`. El caso del
  cliente: "viajé, devolveme al día 34". El ajuste persiste, corre la graduación, y queda en una
  bitácora append-only con motivo y autor (D-82). Días 1 a 89; antes del Día 1, 409 (D-194, D-195).
  Al retroceder, los hábitos del Plan que ya corrían siguen (D-196), también los propios y los que
  arrancan después del día destino (D-200), y el barrido del reloj no pisa un ajuste hecho mientras
  corre (D-197).
- **Elegir, quitar y pausar hábitos** — `PUT`/`DELETE`/`PATCH /api/v1/habit-unlocks/{id}`. El
  interruptor ACTIVO/PAUSADO por fin guarda (D-87).
- **Horarios de hábitos**, sin tope de cambios (D-170); todo cambio rige desde el día siguiente (D-91).
  *Corregido 2026-09-27: decía «con cuota semanal de reacomodo y cambios diferidos al día siguiente
  cuando la ventana del día ya arrancó (D-85)». El dueño aclaró el 2026-09-26 que no hay tope, y desde
  D-91 (2026-09-04) se difiere todo cambio, no solo el de una ventana que ya arrancó.*
- **Puntos de liga**: completar un hábito, una evidencia o una roca otorga puntos, síncrono.
- **Almacenamiento S3**: los 37 objetos de cursos y los 13 mp3 de audioterapias están subidos, con
  URLs prefirmadas — el backend nunca toca los bytes. *(Corregido 2026-09-27: con dos excepciones, la
  tarjeta de bienvenida que genera el servidor, D-174, y la foto propia de un grupo, que el servidor
  prepara y sirve, D-212; ver `docs/INFRA_S3_BUCKET.md`.)*
- **Identidad propia**: sesión como token opaco en Redis, credenciales propias, login social
  contra Google/Apple/Facebook. Sin proveedor externo.

---

## 3. Lo que está construido pero NO funciona

Esta es la sección importante. Todo lo de acá existe como código, compila, y no hace nada.

### 3.1 La coherencia y la racha diaria no se calculan (D-83)

> **Corregido el 2026-09-15.** Esta sección se abría con *"Es el hueco más grande del producto"* y
> daba por muerta toda la cadena. Ya no es cierto para las dos primeras filas:
>
> - **La racha** se deriva de los días con hábito cumplido desde el 2026-09-14 (commit `69e7053`).
> - **La coherencia** se calcula desde el 2026-09-15 (**D-128**): acciones diarias cumplidas sobre
>   planificadas en la ventana de 7 días, y una semana sin planificar ya **no vale 100** — no tiene
>   dato. La app muestra `—`.
>
> Lo que sigue vigente: `RegistrarCoherenciaDiariaUseCase` **sigue sin llamadores** (la coherencia
> se deriva en la lectura, no se acumula), `historial_coherencia` **sigue vacía**, expirar un hábito
> **sigue sin restar puntos**, y el **ranking por célula sigue ordenando por la columna**
> `puntajes_participante.coherencia`, que nadie escribe — o sea que sigue ordenando a todos por la
> misma constante. Ese es hoy el hueco que queda, y es el más visible de los cuatro.

| Pieza | Estado | Evidencia |
|---|---|---|
| `RegistrarCoherenciaDiariaUseCase` | **Sin un solo llamador** en `src/main` | Ni scheduler, ni listener, ni controller |
| `points` como consumidor de eventos | **No tiene ningún `@ApplicationModuleListener`** | El módulo no escucha nada |
| `puntajes_participante.coherencia` | Queda en **100 para siempre** | Su único escritor es ese método muerto |
| `historial_coherencia` | **Siempre vacía** | Idem |
| Racha diaria (`rachaActual`/`rachaMaxima`) | **Nunca avanza** | `actualizarRachaTrasDia` solo se invoca ahí adentro |
| Expirar un hábito | **No resta puntos** | `expirarUnoEnTransaccionPropia` solo cambia el estado |
| Ranking por célula (`TipoRanking.CELL`) | Ordena a todos por **la misma constante** | `RankingService:134` ordena por `coherencia` |

**Consecuencia de producto:** en un programa de 90 días, el indicador central no se calcula, y **no
completar los hábitos hoy no tiene ninguna consecuencia**. Un aprendiz que no abre la app termina
con la misma coherencia que uno perfecto.

**Antes de codear hace falta decidir:** ¿cómo se define la coherencia — % de hábitos completados
sobre los esperados del día, acumulada o ventana móvil? ¿Expirar penaliza puntos, o solo coherencia?

### 3.2 La IA: el acompañante ya usa Gemini; lo demás sigue en `NoOp`

> **Corregido 2026-09-27.** El título decía «La IA nunca se llamó» y lo que sigue describe el 2026-09-04. Hoy el
> acompañante (chat de Renasia y Sparkie, embeddings, voz, voz en vivo y resumen de la conversación) llama a Gemini
> y está prendido en producción: los modelos se arman en `GoogleGenAiClientesConfig` con `IA_PROVEEDOR=google`
> (`docs/DESPLIEGUE_Y_CI.md` §6.4). Siguen en `NoOp` la validación de evidencia y la del V90, la recomendación de
> clase, el Espejo de la Sombra y los clasificadores de intención y de riesgo; para ellos vale todavía la
> consecuencia de abajo.

`spring.ai.model.chat`, `embedding` y `vectorstore` están en `none`, las autoconfiguraciones de
Google GenAI están excluidas, y **los adaptadores de IA del repo son `NoOp`**: loguean y devuelven
vacío.

Lo que **sí** está construido y probado es el andamiaje: el contrato async con polling, la cola, el
conteo de reintentos y el fallback a revisión manual. Todo eso funciona hoy contra adaptadores que
no piensan.

**Consecuencia:** la primera vez que se enchufe un proveedor real es la primera vez que se prueba de
verdad. Hay que presupuestar esa integración como trabajo con riesgo, no como cambiar una línea de
configuración.

### 3.3 Autorización declarada pero no aplicada del todo

`SecurityConfig` termina en `.anyRequest().permitAll()` — solo `/api/v1/renasia/**` exige sesión.
El actor se resuelve por sesión **o por el header `X-Actor-Id`** como respaldo de migración, sin
restricción por perfil.

Los 230 endpoints declaran `@RequiresPermission` y hay un test de reflexión que lo verifica, pero
la puerta de entrada todavía no exige sesión para el resto de la API. **Esto no puede llegar a
producción así.**

### 3.4 Columnas y features huérfanas

- `participantes_programa.habitos_escalonados_en` — existe en la base, **nadie la lee ni la escribe**.
- `dia_post_programa` — se fija en 0 al graduarse y **nadie lo avanza**: qué pasa el día 91 sigue
  sin decidirse ([`FEATURE_POST_PROGRAM.md`](FEATURE_POST_PROGRAM.md) §5).
  > **Corregido 2026-09-15 (D-125).** Acá decía que `programa_completado`, `dia_post_programa` y
  > `programa_completado_en` estaban los tres "sin ningún setter ni caso de uso" y que "la
  > graduación no se marca nunca". Era cierto hasta ese día: ahora la marca el barrido horario del
  > reloj al llegar al día 90, sin condiciones.
- El algoritmo de **escalonamiento por lotes** del repo viejo (`habitStaggering.ts`, ~1470 líneas) no
  se portó. `ElegirHabitoUseCase` es un alta simple con desbloqueo inmediato.

---

## 4. Lo que falta decidir (bloquea trabajo)

| # | Decisión | Por qué bloquea |
|---|---|---|
| 1 | **Cómo se define la coherencia** (§3.1) | Sin esto el indicador central del producto no existe |
| 2 | **Ancla de semana**: ¿calendario (L-D) o programa (días 1-7, 8-14)? | Hay **dos anclas distintas conviviendo** en el código. Sin elegir una no se puede implementar el bloqueo semanal de la selección de hábitos |
| 3 | **¿Se puede correr la hora límite de hoy** para completar un hábito que ya venció? | Es lo que pidió el dueño. Riesgo: si el límite se puede correr siempre, deja de ser un límite |
| 4 | **Mínimo 3 hábitos opcionales** — ¿la regla vive en el backend o solo en la pantalla? | Definido como piso sin techo, pero sin implementar |

---

## 5. Lo que falta construir

**Selección de hábitos (lo más pedido).** Los cimientos ya están —agregar, quitar y pausar
persisten—, falta la pantalla:

- Modal de selección de hábitos
- Momentos (MAÑANA / TARDE / NOCHE) colapsables
- Mínimo 3 opcionales, sin techo
- `dia_desbloqueo` como parámetro (elegir hoy para el día 2). El esquema ya lo soporta:
  `smallint CHECK BETWEEN 1 AND 90`. Falta exponerlo — el caso de uso hoy hardcodea "hoy"

**Infraestructura:**

- ~~**Dónde se hostea** la base en producción (RDS / Cloud SQL / VPS)~~ **Resuelto: RDS** (`docs/DESPLIEGUE_Y_CI.md` §9).
  *Corregido 2026-09-27.*
- Gateway/proxy de transición
- Dashboard de p50/p99 por endpoint, para validar los SLO con datos y no con supuestos
- **Tests en el frontend** — ~~hoy no hay ninguno~~ hoy hay 113 suites / 972 pruebas de Jest y 27 de Playwright
  (2026-09-27). *Corregido 2026-09-27: decía «hoy no hay ninguno».*

---

## 6. Riesgos abiertos

| Riesgo | Detalle |
|---|---|
| **Credenciales en texto plano** | `.run/RenaserOsApplication.run.xml` tiene 5 secretos reales (AWS secret key, Google GenAI, OAuth client secret, SMTP password). Ya está en `.gitignore`, pero **conviene rotarlas**: el archivo vive en una carpeta sincronizada con OneDrive |
| **El frontend ~~sin tests~~ con pocas pruebas de pantalla** | Hoy tiene 113 suites / 972 pruebas de Jest, casi todas de lógica, y 27 de Playwright del panel web; las pantallas del teléfono se siguen verificando en el emulador. Ya hubo bugs que solo aparecieron probando contra el backend real. *Corregido 2026-09-27: decía «sin tests» y «Todo cambio de pantalla se verifica a ojo».* |
| ~~**Sin entorno desplegado**~~ | ~~Los crons diarios dependen de que la laptop esté prendida. Fue la causa práctica de que el reloj no avanzara (E-91)~~ *Corregido 2026-09-27: ya no es un riesgo; producción corre en EC2 + RDS y los crons corren en el servidor.* |
| **Bucket policy de avatares** | Falta la lectura pública anónima sobre `avatares/*` (D-55): la URL es correcta pero responde 403 |
| **`registros_habito.dia_programa` es un snapshot** | No se recalcula nunca. Mover el reloj deja registros viejos con el día anterior — deliberado, pero hay que tenerlo presente |

---

## 7. Lecciones que ya costaron caro

Están en `BITACORA_ERRORES.md` con el detalle; acá las que se repiten:

1. **La medianoche local no existe a una hora UTC fija.** Un cron diario que depende del día del
   usuario está mal por construcción: va cada hora, y el dominio decide. Costó que un aprendiz
   pasara su Día 1 entero viendo "día 0" (E-91).
2. **Derivar, no acumular.** Un contador incrementado desde un cron pierde para siempre cualquier
   corrida que no ocurra.
3. **Un hallazgo de zona horaria en un módulo es un hallazgo del sistema.** `habits` ya había
   documentado la trampa un día antes; nadie la aplicó al cron de `users`.
4. **El fixture puede tapar el bug.** Los tests del reloj fijaban la hora en 10:00 UTC — la única
   franja donde el bug no aparece.
5. **Verificar antes de construir.** Dos veces en la misma sesión se estuvo por crear una tabla que
   ya existía. `desbloqueos_habito` ya era "qué hábitos lleva este aprendiz".
