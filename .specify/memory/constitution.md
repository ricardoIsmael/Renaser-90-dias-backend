<!--
Sync Impact Report
- Version change: (plantilla sin completar) → 1.0.0
- Principios: los siete son nuevos (primera adopción).
  I. Las dependencias apuntan al dominio
  II. No se inventan reglas de negocio
  III. Nada está terminado sin pruebas en verde
  IV. La base de datos guarda historia, no la reescribe
  V. El tiempo es del participante
  VI. Los cinco tabs no se tocan sin autorización
  VII. Documentar en el mismo cambio
- Secciones agregadas: Restricciones técnicas, Flujo de trabajo y calidad, Gobierno.
- Secciones quitadas: ninguna.
- Plantillas: plan-template.md, spec-template.md y tasks-template.md leen esta constitución en
  tiempo de ejecución (Constitution Check). No hizo falta modificarlas.
- TODO pendientes: ninguno.
-->

# Constitución de Renaser 90 días

Rige el backend (`Renaser-90-dias-backend`, Java 25 / Spring Boot, hexagonal + Spring Modulith) y
la app (`Renaser-90-dias-frontend-`, Expo / React Native / TypeScript). Las reglas detalladas viven
en `.claude/rules/00..05` del backend y en `AGENTS.md` del frontend; esta constitución resume lo que
no se negocia y gana ante cualquier contradicción de una spec, un plan o una tarea.

## Core Principles

### I. Las dependencias apuntan al dominio

- `domain/` MUST NOT importar Spring, JPA ni Jackson. Las reglas de negocio viven ahí, con datos y
  comportamiento; las raíces de agregado mutan solo por métodos con nombre de intención, nunca por
  setters.
- Un módulo MUST importar únicamente `<otro>/api/`. Ningún módulo manda SQL contra una tabla ajena.
- El controller MUST limitarse a deserializar, validar, autorizar, invocar **un** caso de uso y
  mapear la salida. Sin repositorios, sin `@Transactional`, sin `if` de negocio.
- Ninguna llamada a IA dentro de `@Transactional`.
- Tamaños objetivo: método ≤ 20 líneas (techo 40), clase ≤ 150 (techo 300), parámetros ≤ 3,
  anidamiento ≤ 2, públicos ≤ 7.
- En el frontend, el código nuevo vive por feature en `src/features/<feature>/` (`screens/`,
  `components/`, `api/`, `hooks/`, `types/`, `utils/`, `data/`) y reusa los componentes
  compartidos (`FormField`, `GoldButton`, `Icon`, `HojaDesdeAbajo`, `ControlSegmentado`,
  `Interruptor`, `ConfirmacionEnLinea`, `Presionable`…). El servidor decide permisos y datos; la app
  no los recalcula.

*Por qué:* `ArchitectureTest` rompe el build si se viola, y es lo que permite cambiar un adaptador
sin tocar reglas.

### II. No se inventan reglas de negocio

- Valores de enums, fases, permisos por rol y cualquier regla que cambie el resultado MUST
  confirmarse con el dueño del producto y quedar escritas (decisión D-nn o `clarifications` de la
  spec). Nunca se rellenan con supuestos.
- Duda menor → se decide, se avanza y se avisa qué se asumió. Duda que cambia el resultado → se
  pregunta.
- No se amplía el alcance por cuenta propia: un segundo bug se reporta, no se arregla en silencio.

### III. Nada está terminado sin pruebas en verde

- Backend: toda tarea que toque código MUST terminar con `./mvnw clean verify` en verde, leyendo las
  líneas `Tests run:` de surefire y failsafe (el código de salida no basta).
- Toda regla de dominio nueva lleva prueba unitaria sin Spring; todo adaptador nuevo, integración
  con Testcontainers (Postgres real).
- Todo endpoint nuevo declara `@RequiresPermission` o `@PublicEndpoint` y lleva pruebas de
  autorización negativa: rol sin permiso → 403 y cuenta `SUSPENDED` → 403.
- Todo comportamiento diario se prueba con el reloj en la madrugada UTC (día local anterior en Lima).
- Frontend: `node node_modules/typescript/bin/tsc --noEmit` con 0 errores y
  `node node_modules/jest/bin/jest.js` en verde. Una compilación no prueba nada visual.
- Un fallo se reporta con su salida; lo que quedó sin verificar se dice y por qué.

### IV. La base de datos guarda historia, no la reescribe

- Migraciones Flyway `V<n>__...sql` MUST llevar cabecera que justifique el cambio (qué resuelve, por
  qué no alcanza el modelo actual, por qué ese nombre). `V1` y toda migración aplicada son
  inmutables.
- Los registros de hábito son snapshot histórico; los ajustes de día y de puntos son append-only.
  Nada se recalcula hacia atrás.
- Un dato derivable se deriva en un solo lugar, no se duplica en columnas generadas.
- Persistencia con `*JpaEntity` separado del dominio y mapper a mano cuando hay traducción; nunca
  MapStruct hacia la respuesta HTTP.

### V. El tiempo es del participante

- El dominio MUST leer la hora por el puerto `Clock`; nunca `now()` estático.
- El día de una persona es `clock.now()` en **su** zona. Un trabajo "al empezar el día" corre por
  hora y decide el dominio. Lo que es función del calendario se deriva de fechas, no se acumula.
- Todo `@Scheduled` nuevo lleva `@SchedulerLock`, pagina, aísla fallos por participante y no usa
  una transacción global.

### VI. Los cinco tabs no se tocan sin autorización

- Hoy, Plan, Training, Comunidad y Yo MUST NOT alterarse sin autorización explícita del dueño. Toda
  excepción se anota en `AGENTS.md` con fecha, alcance y «lo que cambia y nada más»; una excepción
  no abre el tab.
- Pantallas: un solo scroll, márgenes de `useResponsive()`, áreas táctiles de 48–52 px, Jost
  (nunca pesos ultrafinos), tipo oración, contraste del tema en claro y oscuro, validación antes de
  avanzar, `useSystemBackHandler` en toda vista hija u hoja.
- Textos en español de Perú con tuteo. El voseo es una regresión.

### VII. Documentar en el mismo cambio

- Todo avance se documenta en el mismo cambio. Las decisiones van a `docs/MODULOS_A_AVANZAR.md` §8
  (D-nn) y todo error, por pequeño que sea, a `docs/BITACORA_ERRORES.md` (E-nn), verificando antes
  el último número usado.
- Los documentos no pueden contradecirse: al corregir uno se deja a la vista qué decía y por qué
  cambió (`> **Corregido AAAA-MM-DD.** …`).

## Restricciones técnicas

- Backend: Java 25 (sdkman, Temurin), Spring Boot según `pom.xml`, PostgreSQL (schema `renaser`)
  con Flyway, Redis, Spring Modulith con outbox. Un solo rol por usuario (`usuarios.rol`), permisos
  en `shared.domain.Permission` + la matriz de `UserRole`.
- Frontend: Expo SDK 57 (leer https://docs.expo.dev/versions/v57.0.0/ antes de escribir código),
  TypeScript estricto, zod con esquemas tolerantes (`passthrough`) para que un campo nuevo del
  servidor no rompa un APK publicado. La app no se actualiza por aire: un cambio de app necesita un
  APK nuevo, y la app nueva debe tolerar el backend anterior.
- Sin librerías visuales nuevas ni dependencias nativas sin justificarlo en el plan.

## Flujo de trabajo y calidad

- Flujo spec-kit: constitución → `specify` → `clarify` → `plan` → `checklist` → `tasks` →
  `analyze` → `implement`. El plan MUST pasar el Constitution Check antes y después del diseño.
- Commits solo cuando se piden, en español, imperativo, qué cambia y por qué; nunca atribución a IA.
  Pedido estando en `master` → rama primero. No se hace reset, stash ni limpieza del trabajo ajeno.
- Después de modificar código se ejecuta `graphify update .` en el repo tocado.

## Governance

- Esta constitución gana ante specs, planes y tareas. Las reglas detalladas de `.claude/rules/` y
  `AGENTS.md` la desarrollan; si chocan, se corrige la que esté mal en el mismo cambio.
- Enmienda: propuesta escrita, aprobación del dueño, versión nueva y nota de impacto.
  Versionado semántico: MAJOR quita o redefine un principio; MINOR agrega uno o lo amplía; PATCH
  aclara redacción.
- Cumplimiento: cada plan lleva su Constitution Check; cada revisión verifica los siete principios.
  Toda complejidad que los tensione se justifica en la tabla *Complexity Tracking* del plan.

**Version**: 1.0.0 | **Ratified**: 2026-10-12 | **Last Amended**: 2026-10-12
