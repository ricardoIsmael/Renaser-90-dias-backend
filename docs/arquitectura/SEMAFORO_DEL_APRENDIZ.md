# Semáforo de cumplimiento del aprendiz — diseño y contratos

**Fecha:** 2026-09-25 · **Decisión:** D-168 (`docs/MODULOS_A_AVANZAR.md` §8) · **Rama:** `semaforo-aprendiz`
(backend) y `semaforo-app` (frontend).

**Origen del pedido:** punto 9 del Excel *PROBLEMAS EN PROGRAMA FORMACIÓN RENASER* («se envía el semáforo a
los que están activos en la formación, para indicar si cumplen o no con las actividades») y el encargo del dueño
del 2026-09-25, con sus respuestas a las preguntas de diseño. Este documento es la **única fuente** de los
contratos: backend, app y agentes trabajan contra lo que dice acá. Si algo cambia, se cambia primero acá.

> **No confundir con:** el semáforo OPERATIVO del mentor (`perfiles_mentor.estado_operativo`, lo mueve el
> líder), la «coherencia» de Hoy (solo objetivos, D-128), el ritmo de objetivos `OK/LENTO/CRITICO`
> (`EstadoRitmoRocas`) ni el punto 10 del Excel (días sin responder mensajes). Son cuatro indicadores distintos.

---

## 1. Reglas de negocio (confirmadas por el dueño)

| Regla | Qué significa |
|---|---|
| **% del día** | `(hábitos cumplidos + objetivos cumplidos) ÷ (hábitos que contaban + objetivos planificados) × 100`, redondeado a entero (mitad hacia arriba). |
| Hábito **opcional** no cumplido | No cuenta ni arriba ni abajo. Opcional cumplido: cuenta arriba y abajo. Es la regla que ya aplica `habits` (`ConteoDiarioHabitos.calificables()`), no se reimplementa. |
| **Objetivo** del día (fila de `rocas_diarias`) | Planificado y no cumplido = pendiente, **sí** baja el %. Un objetivo nunca es opcional. Los pasos (`acciones_diarias`, V61) no cuentan aparte. |
| Día sin nada programado | Estado `SIN_DATOS`: no entra al promedio (no es 0 ni 100). |
| **Semáforo vigente** | Promedio de los % de los **últimos 7 días cerrados** (ayer y los 6 anteriores, en la zona del participante) que tuvieron algo programado, con 1 decimal (mitad hacia arriba). |
| Colores | **Verde** ≥ 80 · **Amarillo** 60 a 79,9 · **Rojo** < 60 · **Sin datos** si ningún día tuvo algo programado. **Nunca verde por falta de datos** (D-128, D-131, CL-07 del SDD 002). |
| Palabras (siempre junto al color) | Verde «Al día» · Amarillo «Requiere atención» · Rojo «Con problemas» · Sin datos «Sin datos». |
| **Semana** | De **sábado a viernes**. Se cierra el **sábado 00:00 hora local** de cada persona (medianoche de viernes a sábado). Al cerrar, el vigente coincide con la semana. |
| Lo completado **tarde** | Hoy la app permite completar días pasados (hábito EXPIRADO→COMPLETADO, objetivo sin límite de fecha). Cuenta para su día **hasta el cierre del sábado**; lo reportado ya no cambia. |
| Qué días se miden | Del **día 1 al día 90** del programa (con los ajustes de día del admin). Día 0 y días después de graduarse, no. |
| Quién se mide | **Aprendiz** con programa activado: obligatorio, no se puede apagar. **Mentor, líder, admin y alquimista con programa propio**: se mide por defecto y lo pueden **pausar con fecha de regreso** (esos días no se miden ni salen en rojo; lo anterior se conserva; al pasar la fecha vuelve solo; no borra nada). |
| Días de intoxicación (8-10, 17-19, 26-28) | `habits` los genera opcionales salvo la publicación diaria en comunidad (D-169). **El semáforo no lo inventa**: cuenta lo que `habits` marque como opcional (`ConteoDiarioHabitos.calificables()`), así que un hábito de esos días sin cumplir no cuenta y uno cumplido suma arriba y abajo. No cambió nada del semáforo para tomarlo. Vale para lo generado desde el 2026-09-25: los registros anteriores conservan su foto. *(Decía: «La especificación los vuelve opcionales, pero `habits` todavía no lo hace (`TipoDia.INTOXICACION` sin uso). […] Cuando `habits` lo implemente, el semáforo lo toma solo.»)* |

### 1.1 Quién ve qué

| Rol | Ve |
|---|---|
| Aprendiz (y staff con programa propio) | Su propio semáforo: vigente, sus días y sus semanas. |
| Mentor | Cada aprendiz de los grupos que **acompaña vigentemente** (`community.api.AcompanamientoFinder`), con nombre. |
| Líder de mentores | **Resumen por grupo, sin nombres**: cuántos verdes, amarillos, rojos y sin datos (RL-07 del SDD 002). |
| Admin · Alquimista | Todo: resumen por grupo, el grupo con nombres y la persona. |
| Compañeros de grupo | Solo el propio. |

### 1.2 Avisos del sábado (cierre de semana)

| Destinatario | Canal | Contenido |
|---|---|---|
| Cada persona medida | Push + bandeja (`RESUMEN_SEMANAL`, tipo ya existente y sin uso) | «Tu semana ya cerró: mira tu semáforo». **Sin cifras** (regla de la app: el push no lleva métricas). Ruta `/semaforo`. |
| Cada persona medida | Mensaje del acompañante (`rag.SemaforoEnChatListener`, plantilla sin IA, flag `renaser.ia.acompanante.semaforo-en-chat`, **apagado por defecto**) | Color, palabra y %: «Cerraste la semana en verde, Al día: 86 %». Una plantilla por color en `application.yaml`; la de «sin datos» nunca lleva número. Textos provisorios hasta que el dueño los apruebe (como D-155). |
| Mentor | Bandeja + push (`RESUMEN_SEMANAL`) | Un aviso por grupo: «Tu grupo cerró la semana — El semáforo de Grupo Fénix ya está listo». **Sin cifras** (ver nota). Ruta `/mentor/groups/{grupoId}/semaforo`. |
| Líder, admin, alquimista | Bandeja + push (`RESUMEN_SEMANAL`) | Un aviso general: «Semana cerrada — El semáforo de los grupos ya está listo». **Sin cifras**; los conteos por color viajan en el evento y se ven al abrir la ruta. Ruta `/semaforo/grupos`. |

Los avisos solo salen si el cierre ocurre **dentro del fin de semana** (sábado o domingo local). Si el backend
estuvo caído todo el fin de semana, la semana igual se cierra y queda en el historial, pero sin avisos tardíos.
Deduplicación con `origenEventoId` determinista (`UUID.nameUUIDFromBytes`), igual que los avisos de V46.

> **Corregido 2026-09-25.** Las filas del mentor y del líder decían que el aviso llevaba los conteos
> («5 al día, 2 requieren atención, 1 con problemas»). No puede: `NotificacionService.intentarPush` manda
> **el mismo título y cuerpo** a la bandeja y al push, y el push no lleva métricas ni nombres de personas. Los
> conteos siguen en `ResumenSemanalDelGrupoEvent`/`ResumenSemanalGeneralEvent` y en las vistas de §4.3 y §4.4.

**Cuándo sale cada aviso:**

- **A la persona:** al cerrarse su semana en el barrido horario (§2), solo si la semana tuvo al menos un día
  medido y el cierre cae sábado o domingo en su zona. El mensaje del acompañante sale del mismo evento.
- **Al mentor y al líder/admin/alquimista:** `mentoring.ResumirSemanaDelSemaforoScheduler` corre cada hora en
  el minuto 40 y solo actúa el **sábado entre 00:00 y 05:59 en la zona del grupo**, cuando el barrido del
  minuto 25 ya cerró las semanas de esa madrugada. Un grupo sin ninguna semana cerrada esa madrugada no
  genera aviso (no se manda «tu grupo cerró» de un grupo vacío). Fuera de esa ventana no hay reintento: si el
  backend estuvo caído toda la madrugada del sábado, el mentor igual ve el semáforo al entrar, sin aviso.
- **Deduplicación:** clave por grupo y semana para el mentor (`ReglasDelResumenSemanal.claveDelGrupo`) y por
  semana para el resumen general (`claveGeneral`); dos corridas o dos instancias producen un solo aviso.

**Fuera de alcance (pedido del dueño):** el link de la mentoría de Darren (punto 8 del Excel) — queda para
después; `ElegibilidadEventoNoOpAdapter` no se toca.

---

## 2. Cómo se calcula (sin procedimiento almacenado — D-168)

- **Al subir un hábito o cumplir un objetivo no se calcula nada.** Cero consultas extra en ese momento.
- Un `@Scheduled` **cada hora** (`points-cerrar-semaforo`, minuto 25, con `@SchedulerLock`) recorre los programas
  activados **paginando** (`TAMANO_LOTE = 500`). Por página: **1 consulta de hábitos + 1 de objetivos** para
  todos (las consultas en lote que ya existen, D-43), más las lecturas de sus propias tablas.
- Para cada persona decide el dominio, en su zona: qué días cerrados faltan o pueden cambiar (los de semanas
  todavía no cerradas) y qué semana toca cerrar. Es **derivado e idempotente**: correrlo dos veces da lo
  mismo y una corrida tarde se pone al día sola (regla 02 §2).
- Cada persona en su propia transacción (a través del proxy, no auto-invocación: ver E-250) y con `try/catch`:
  una que falla no frena el barrido.
- Leer el semáforo = leer filas ya guardadas. Nada se recalcula en una lectura.

**Por qué no un procedimiento almacenado** (el dueño lo propuso y aceptó esta alternativa): no baja las
consultas por debajo de «2 en lote por página, una vez por día cerrado», y dejaría la regla de qué cuenta como
cumplido en Java **y** en SQL — exactamente lo que D-43 y D-63 descartaron.

### 2.1 Tablas (V68, dueño `points`)

- `semaforo_dias (participante_id, fecha)`: conteos del día (`habitos_programados`, `habitos_cumplidos`,
  `objetivos_programados`, `objetivos_cumplidos`) y `calculado_en`. **No guarda % ni color**: se derivan de los
  conteos en un solo lugar (lección de V22).
- `semaforo_semanas (participante_id, semana_hasta)`: la **foto** del cierre del sábado — `semana_desde`,
  `porcentaje` (null = sin datos), `dias_con_datos`, `dias_medidos`, `version_formula`, `cerrada_en`.
  **Append-only**: es lo que se reportó y no se reescribe.
- `semaforo_pausas`: pausas del staff (`usuario_id`, `desde`, `hasta`, `reanudada_el`, `creada_en`,
  `reanudada_en`). `reanudada_el` es el día local en que se volvió a medir antes de tiempo (`DELETE /pausa`);
  `reanudada_en`, el instante en que se pidió. Los dos van juntos o ninguno (CHECK).
  > **Corregido 2026-09-25.** Decía `terminada_en` como única columna de cierre. La migración quedó con
  > `reanudada_el` (fecha, para saber qué días se miden) y `reanudada_en` (instante, para auditoría): con
  > solo el instante habría que convertirlo a la zona de la persona en cada lectura.

---

## 3. Contratos Java entre módulos

### 3.1 `users.api.ProgramasActivadosFinder` (nuevo; implementa `users`)

```java
List<ProgramaActivado> pagina(int offset, int limite);      // ordenado por participante, cualquier rol/estado
Optional<ProgramaActivado> de(UserId participanteId);        // vacío si no tiene programa activado
Map<UserId, ProgramaActivado> deVarios(Collection<UserId>);  // en UNA consulta (tabla de un grupo)
record ProgramaActivado(UserId participanteId, ZoneId zona,
                        LocalDate primeraFecha,              // primer día con día de programa >= 1
                        LocalDate ultimaFecha) {}            // fecha del día 90 (= graduación esperada - 1)
```

### 3.2 `points.api` — lo que `habits` y `rocks` implementan (DIP, como `PorcentajeHabitosFinder`)

```java
record ConteoDelDia(LocalDate fecha, int programados, int cumplidos) {}
interface ConteoDiarioHabitosFinder   { Map<UserId, List<ConteoDelDia>> porParticipanteEntre(Collection<UserId>, LocalDate desde, LocalDate hasta); }
interface ConteoDiarioObjetivosFinder { Map<UserId, List<ConteoDelDia>> porParticipanteEntre(Collection<UserId>, LocalDate desde, LocalDate hasta); }
```

`habits` devuelve `programados = calificables()` (total − opcionales no cumplidos) y `cumplidos = completados`,
reutilizando `ContarRegistrosDiariosHabitsPort`. `rocks` devuelve `total/completadas` de `CargarConteoDiarioRocasPort`.
Un día sin filas no aparece.

### 3.3 `points.api` — lo que consumen `mentoring`, `notifications` y `rag`

```java
enum ColorSemaforo { VERDE, AMARILLO, ROJO, SIN_DATOS; String etiqueta(); }   // «Al día», «Requiere atención», «Con problemas», «Sin datos»
enum EstadoDiaSemaforo { MEDIDO, SIN_DATOS, PAUSADO, PENDIENTE, FUERA_DEL_PROGRAMA }

final class SemanaDelSemaforo {                          // la regla sábado→viernes, en un solo lugar
    static LocalDate ultimaCerradaAl(LocalDate hoyLocal);    // el viernes de la última semana cerrada
    static LocalDate desde(LocalDate semanaHasta);           // el sábado que la abre
    static boolean esCierreValido(LocalDate semanaHasta);    // es viernes
}

record DiaDelSemaforo(LocalDate fecha, EstadoDiaSemaforo estado, Integer porcentaje, ColorSemaforo color,
                      int habitosProgramados, int habitosCumplidos, int objetivosProgramados, int objetivosCumplidos) {}
record VentanaDelSemaforo(LocalDate desde, LocalDate hasta, BigDecimal porcentaje, ColorSemaforo color,
                          int diasConDatos, boolean cerrada, List<DiaDelSemaforo> dias) {}
record SemanaCerrada(LocalDate desde, LocalDate hasta, BigDecimal porcentaje, ColorSemaforo color,
                     int diasConDatos, Instant cerradaEn) {}
record PausaDelSemaforo(LocalDate desde, LocalDate hasta) {}
record DetalleDelSemaforo(boolean aplica, boolean obligatorio, ZoneId zona, PausaDelSemaforo pausa,
                          VentanaDelSemaforo vigente, List<SemanaCerrada> semanas, Instant calculadoEn) {}

interface SemaforoFinder {
    Map<UserId, VentanaDelSemaforo> vigenteDe(Collection<UserId> participantes);                     // sin clave = no se mide
    Map<UserId, VentanaDelSemaforo> semanaDe(Collection<UserId> participantes, LocalDate semanaHasta); // semanaHasta = viernes
    DetalleDelSemaforo detalleDe(UserId participante, int semanas);                                   // semanas: 1..13
}

record SemanaDelSemaforoCerradaEvent(UUID claveDeduplicacion, UUID participanteId, LocalDate desde, LocalDate hasta,
                                     BigDecimal porcentaje, ColorSemaforo color, int diasConDatos, Instant cerradaEn) {
    String rutaApp();   // "/semaforo"
}
```

`SemanaDelSemaforoCerradaEvent` se publica **dentro de la transacción** que inserta la fila de
`semaforo_semanas`, una sola vez por persona y semana, solo si la semana tuvo días medidos y el cierre ocurre
dentro del fin de semana (§1.2).

### 3.4 `mentoring.api` — resúmenes del sábado (los construye `mentoring`, los entrega `notifications`)

```java
record ResumenSemanalDelGrupoEvent(UUID claveDeduplicacion, UUID mentorId, UUID grupoId, String grupoNombre,
                                   LocalDate desde, LocalDate hasta, int verde, int amarillo, int rojo,
                                   int sinDatos, int total, Instant generadoEn) {
    String rutaApp();   // "/mentor/groups/{grupoId}/semaforo"
}
record ResumenSemanalGeneralEvent(UUID claveDeduplicacion, LocalDate desde, LocalDate hasta, int grupos,
                                  int verde, int amarillo, int rojo, int sinDatos, int total, Instant generadoEn) {
    String rutaApp();   // "/semaforo/grupos"
}
```

---

## 4. Contratos REST (JSON en castellano, como `/home` y la semana del alumno)

Todos los campos nuevos son **aditivos**: la app instalada los ignora (sus esquemas Zod usan `.passthrough()`).
Toda ruta nueva exige sesión: `SecurityConfig` la cubre con `.authenticated()` (`/api/v1/me/semaforo`,
`/api/v1/me/semaforo/**`, `/api/v1/semaforo/**`; mentor y admin ya estaban en `/mentor/**` y `/admin/**`) — E-254.

### 4.1 Detalle de una persona — un solo formato para 4 rutas

| Ruta | Quién | Permiso declarado | Guard en el servicio |
|---|---|---|---|
| `GET /api/v1/me/semaforo?semanas=8` | cualquiera (a sí mismo) | `USE_APP` | autoconsulta |
| `GET /api/v1/mentor/groups/{groupId}/learners/{userId}/semaforo?semanas=8` | mentor | `USE_APP` | acompaña vigentemente el grupo **y** el alumno es de ese grupo |
| `GET /api/v1/admin/trainees/{traineeId}/semaforo?semanas=8` | admin, alquimista | `MANAGE_TRAINEES` | ADMIN/ALCHEMIST activo |
| `PUT /api/v1/me/semaforo/pausa` · `DELETE /api/v1/me/semaforo/pausa` | staff con programa propio | `TRACK_PROGRAM_AS_STAFF` | rol ≠ TRAINEE y programa activado |

`semanas` por defecto 8, entre 1 y 13. `PUT` recibe `{"hasta": "2026-10-05"}` (≥ hoy local) y pausa desde hoy;
`DELETE` termina la pausa (se vuelve a medir desde hoy). Los dos devuelven el detalle actualizado.

La primera ruta la arma `points` y las del mentor y de administración, `mentoring`: son dos records
(`DetalleDelSemaforoResponse` en cada módulo) porque un módulo no importa los adaptadores de otro.
`DetalleDelSemaforoMismoFormatoTest` compara el JSON de los dos para el mismo detalle; un campo que cambie en uno
solo rompe esa prueba.

```json
{
  "aplica": true,
  "obligatorio": true,
  "zona": "America/Lima",
  "pausa": null,
  "vigente": {
    "desde": "2026-09-18", "hasta": "2026-09-24",
    "porcentaje": 78.3, "color": "AMARILLO", "etiqueta": "Requiere atención",
    "diasConDatos": 6, "cerrada": false,
    "dias": [
      { "fecha": "2026-09-18", "estado": "MEDIDO", "porcentaje": 75, "color": "AMARILLO",
        "etiqueta": "Requiere atención",
        "habitos": { "programados": 9, "cumplidos": 7 },
        "objetivos": { "programados": 3, "cumplidos": 2 } }
    ]
  },
  "semanas": [
    { "desde": "2026-09-12", "hasta": "2026-09-18", "porcentaje": 82.0, "color": "VERDE",
      "etiqueta": "Al día", "diasConDatos": 7, "cerradaEn": "2026-09-19T05:25:03Z" }
  ],
  "calculadoEn": "2026-09-25T05:25:03Z"
}
```

- `aplica=false` (sin programa activado) → `vigente: null`, `semanas: []`, `pausa: null`.
- `pausa` → `{"desde": "...", "hasta": "..."}` si hay una pausa activa hoy; si no, `null`.
  > **Corregido 2026-09-25.** Decía «si hay una pausa activa o por empezar», y contradecía la nota de pausas
  > de más abajo: las pausas empiezan siempre hoy, así que no existe una «por empezar».
- `vigente.dias` siempre trae **7 días** (del más viejo al más nuevo; el ejemplo de arriba está abreviado).
  `porcentaje` y `color` de un día son `null`/`SIN_DATOS` si `estado ≠ MEDIDO`. Cada día trae su `etiqueta`.
- La ventana vigente se calcula **en el momento de la lectura** con el reloj del servidor en la zona de la
  persona: `vigente.hasta` es siempre su «ayer» y su «hoy» es `vigente.hasta + 1`. Entre las 00:00 y el
  barrido de las 00:25, el día de ayer puede venir `PENDIENTE`.
- Pausa: `hasta` es inclusive y **no tiene máximo** (lo elige la persona). `PUT` sobre una pausa en curso le
  cambia la fecha; las pausas empiezan siempre hoy, así que no existe una pausa «por empezar».
- `semanas`: las últimas semanas **cerradas**, de la más vieja a la más nueva (listo para graficar).

### 4.2 Resumen de Hoy — `GET /api/v1/home` (campo nuevo)

```json
"semaforo": { "color": "AMARILLO", "etiqueta": "Requiere atención", "porcentaje": 78.3,
              "diasConDatos": 6, "pausado": false,
              "dias": [ { "fecha": "2026-09-18", "estado": "MEDIDO", "porcentaje": 75, "color": "AMARILLO",
                          "etiqueta": "Requiere atención" } ] }
```

`null` si la persona no se mide. `pausado` = hoy rige una pausa. `dias` son los mismos 7 días de la ventana
vigente, compactos: la tarjeta de Hoy dibuja sus barras con esto, **sin una segunda petición** (agregado el
2026-09-25 a pedido del frontend: con dos lecturas, el número y las barras podían diferir justo en el borde del
barrido). El detalle con gráficos llama a `/me/semaforo`.

### 4.3 Tabla de un grupo — mentor y admin

| Ruta | Quién | Permiso | Guard |
|---|---|---|---|
| `GET /api/v1/mentor/groups/{groupId}/semaforo?semanaHasta=YYYY-MM-DD` | mentor | `USE_APP` | acompaña vigentemente el grupo |
| `GET /api/v1/admin/semaforo/groups/{groupId}?semanaHasta=YYYY-MM-DD` | admin, alquimista | `MANAGE_TRAINEES` | ADMIN/ALCHEMIST activo |

Sin `semanaHasta` = ventana vigente (últimos 7 días cerrados). Con `semanaHasta` (debe ser viernes; si no, 400)
= esa semana sábado→viernes.

```json
{
  "grupoId": "…", "grupoNombre": "Grupo Fénix",
  "desde": "2026-09-18", "hasta": "2026-09-24", "cerrada": false,
  "resumen": { "verde": 5, "amarillo": 2, "rojo": 1, "sinDatos": 0, "total": 8 },
  "aprendices": [
    { "aprendizId": "…", "nombre": "Ana Pérez", "avatarUrl": null,
      "porcentaje": 78.3, "color": "AMARILLO", "etiqueta": "Requiere atención", "diasConDatos": 6,
      "dias": [ { "fecha": "2026-09-18", "estado": "MEDIDO", "porcentaje": 75, "color": "AMARILLO",
                  "etiqueta": "Requiere atención" } ] }
  ]
}
```

Orden: rojo, amarillo, sin datos, verde; dentro de cada color, por nombre. Solo aprendices vigentes del grupo
(nunca el mentor que además cursa). `dias` trae los 7 días con el mismo formato que §4.1, `etiqueta` incluida
(el ejemplo está abreviado).

> **Corregido 2026-09-25.** El ejemplo mostraba los días sin `etiqueta`; `TablaDelSemaforoResponse` siempre
> la manda, como dice §4.1.

Cómo queda hoy, pendiente de confirmar con el dueño:

- Un aprendiz **suspendido** que sigue en el padrón del grupo aparece en la tabla. El barrido no le calcula
  días nuevos (solo recorre cuentas activas), y un día del programa sin cálculo se lee `PENDIENTE`: desde la
  suspensión sus días salen «Pendiente», y cuando en la ventana ya no queda ningún día medido, la fila sale
  «Sin datos».
- Quien **no se mide** (sin programa activado, día 0 o ya graduado) llega igual que quien no tuvo nada
  programado: `SIN_DATOS`, `diasConDatos: 0`, `dias: []`. La fila dice «Sin datos · 0 de 7 días con datos».

### 4.4 Resumen por grupos — líder, admin, alquimista (sin nombres de aprendices)

`GET /api/v1/semaforo/groups?semanaHasta=YYYY-MM-DD` · permiso `USE_APP` · guard: rol MENTOR_LEAD, ADMIN o
ALCHEMIST **y** cuenta activa (el permiso no alcanza: MENTOR/ADMIN/ALCHEMIST pasan el interceptor por A-1).

```json
{
  "desde": "2026-09-18", "hasta": "2026-09-24", "cerrada": false,
  "totales": { "verde": 40, "amarillo": 12, "rojo": 8, "sinDatos": 2, "total": 62 },
  "grupos": [
    { "grupoId": "…", "grupoNombre": "Grupo Fénix", "mentorNombre": "Luisa Ramírez",
      "resumen": { "verde": 5, "amarillo": 2, "rojo": 1, "sinDatos": 0, "total": 8 },
      "promedio": 76.4 }
  ]
}
```

Grupos regulares con mentor vigente (`gruposConMentorVigente`, sin recepción). `promedio` = promedio de los %
de sus aprendices con datos, 1 decimal, o `null`. `promedio` **no trae color ni palabra**: los umbrales son de
personas, y el cliente no los aplica; la app lo muestra neutro.

Orden de `grupos`: alfabético por `grupoNombre` y, ante nombres iguales, por `grupoId` (estable).

Cómo queda hoy, pendiente de confirmar con el dueño:

- `mentorNombre` es el **nombre completo** del mentor.
- `totales` es la **suma de los grupos**: un aprendiz que está en dos grupos (D-139) cuenta en los dos. La
  lectura del semáforo sí se hace una sola vez por persona.

> **Corregido 2026-09-25.** El ejemplo decía `"mentorNombre": "Luisa R."` (abreviado); el servicio devuelve el
> nombre completo. Este apartado tampoco fijaba el orden de los grupos ni decía que el promedio va sin color.

### 4.5 Rutas de los avisos (deep links)

`/semaforo` (persona) · `/mentor/groups/{grupoId}/semaforo` (mentor) · `/semaforo/grupos` (líder/admin).
La app instalada ignora rutas que no conoce y solo se abre: no rompe nada.

---

## 5. Pautas de experiencia (app)

- Público de 50 a 60 años: cuerpo 16 px, controles de 48 px, **palabra además de color** (RL-30), número
  siempre con su denominador («7 de 9»), fecha de corte visible. Nunca 0 % ni verde por falta de datos.
- **No sobrecargar**: en Hoy, una sola tarjeta compacta (color + palabra + % + 7 barritas). Todo lo demás, en el
  detalle. Respetar el tema existente (crema, carbón, dorado), Jost 400/500/700, un único scroll por pantalla.
- Gráficos con `react-native-svg` (ya instalado), **sin dependencias nuevas**. Componentes chicos y
  reemplazables: el dueño va a pedir después animación, imagen o gráfico distinto, y tiene que poder cambiarse
  sin tocar las pantallas.
- El frontend **no recalcula** nada: pinta lo que manda el servidor.
- Hoy es pestaña protegida (`AGENTS.md`). Lo autorizado el 2026-09-25 es la tarjeta del semáforo con su
  detalle, la tarjeta del líder de mentores (solo la ve ese rol, debajo de su bandeja de tickets) y las rutas
  de los avisos del sábado. Para cualquier otra persona Hoy no cambia.
  > **Corregido 2026-09-25.** Decía «**solo** la tarjeta del semáforo». La vista del líder (alcance «Vista de
  > líder y admin») se abrió desde Hoy con el mismo patrón de su bandeja de tickets; `AGENTS.md` del frontend
  > ya lo registra.

---

## 6. Qué queda afuera, a propósito

- Link de la mentoría de Darren (punto 8 del Excel): pedido del dueño, después.
- Verdugo por semáforo rojo (RF-28): no se pidió.
- Días de intoxicación como opcionales: es de `habits` (ver §1), y ya está hecho allá (D-169) sin tocar el semáforo.
- El «semáforo» del punto 10 del Excel (días sin responder): otro indicador.
