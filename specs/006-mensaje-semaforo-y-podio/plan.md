# Implementation Plan: Mensaje del semáforo con sticker y descarga del podio

**Branch**: `006-mensaje-semaforo-y-podio` (a crear en los dos repos desde `origin/master`) | **Date**: 2026-10-19 |
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-mensaje-semaforo-y-podio/spec.md`

## Summary

1. **Mensaje del semáforo (backend + app)**: el módulo `points`, que ya calcula el semáforo, elige cada día un mensaje
   del catálogo (115 filas sembradas en V110) para cada persona. La elección se guarda por persona, fecha y grupo
   (color o caso) —y, desde D-281, por canal—, para mantenerla durante el día y no repetirla en 14 días. El servidor escribe el texto con las
   variables ya reemplazadas y lo manda dentro de `semaforo.mensaje` de `GET /api/v1/home`. La app solo lo pinta, con
   su sticker local (15 WebP), en `TarjetaSemaforoHoy`.
2. **Descarga del podio (backend mínimo + app)**: se agrega un permiso nuevo, `EXPORT_RANKING`, que el servidor
   publica como `capabilities.canExportRanking` en `GET /api/v1/mentor/context`. Con esa capacidad, la app muestra
   «Descargar» en Ranking, dibuja fuera de pantalla una tarjeta 9:16 con los datos que ya tiene, la captura con
   `react-native-view-shot` y la comparte con `expo-sharing`. En la web la baja como archivo. Las dos dependencias ya
   están instaladas, así que no se agrega ninguna.
3. **Mensaje en la tarjeta diaria del chat (backend, D-281, 2026-10-19)**: el chat de soporte (D-223) manda a las 23:50
   la imagen del color y «Hoy llevas N % de tus hábitos.»; ahora el texto suma debajo un mensaje del mismo catálogo.
   `points` ofrece `MensajeDelChatFinder` (canal `CHAT`, sin `{faltan}`, distinto del de Hoy ese día, 14 días entre los
   dos canales); `chat` lo pide solo cuando el texto va a salir y, si falla, manda el texto de siempre. Sin cambios en la
   app. La columna `canal` entra en V110 (aún no integrada).

## Technical Context

**Language/Version**: Java 25 (Spring Boot, hexagonal + Spring Modulith) · TypeScript estricto (Expo SDK 57, React
Native)

**Primary Dependencies**: Flyway, JPA, Testcontainers · zod, `react-native-view-shot` 5.1.0 y `expo-sharing` ~57.0.22
(ya instalados), `expo-image`

**Storage**: PostgreSQL, schema `renaser`, migración **V110** reservada (las V98–V108 son de la fase 2 de Soporte TI y
la V109 de la spec 005). Dos tablas: el catálogo y lo elegido.

**Testing**: JUnit sin Spring para el dominio; Testcontainers para el adaptador y el controller (incluye 403 negativos);
Jest para la app; Maestro + mobile-mcp en el emulador antes de subir.

**Target Platform**: Android (APK nuevo) y web (Vercel); backend en AWS.

**Project Type**: mobile app + API

**Performance Goals**: el mensaje no agrega espera visible a `/home`: una consulta indexada por persona y fecha, y como
mucho una escritura al día por persona. La imagen del podio, en menos de 3 s.

**Constraints**:
- Los APK publicados ignoran el campo nuevo.
- La app nueva contra un backend viejo, sin `mensaje` ni capacidad, se ve como hoy.
- El push del sábado no cambia.

**Scale/Scope**: unos 300 aprendices; 115 mensajes; 15 stickers (~1 MB en el APK).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo se cumple | Estado |
|---|---|---|
| I. Dependencias al dominio | Elección y redacción en `points/domain/model/semaforo/mensaje/` (puro). El controller de `/home` no cambia de forma: el caso de uso `HomeAgregadoService` arma el mensaje. El cumplimiento por hábito sale de un finder nuevo en `habits/api/`, sin SQL a tablas ajenas. La app no recalcula nada. | ✅ |
| II. No inventar reglas | Las reglas son decisiones del dueño del 2026-10-19 (spec, Clarifications). Las cuatro asumidas (14 días, mensaje nuevo si cambia el color, variables en cada lectura, web baja archivo) y `{faltan}` = hábitos + objetivos (D-168) quedaron confirmadas el 2026-10-19. D-nn al implementar. | ✅ |
| III. Pruebas en verde | Unitarias de elección, redacción y `faltan`; IT del repositorio y de `/home`; 403 negativos para la capacidad; `clean verify`; Jest; tsc; Maestro. | ✅ |
| IV. Historia | `mensajes_semaforo_elegidos` es append-only: una fila por persona, fecha, canal (D-281) y grupo, nunca se reescribe. V110 con cabecera. El catálogo se desactiva, no se borra. | ✅ |
| V. Tiempo del participante | La fecha es `clock.now()` en la zona de la persona (`ParticipacionPrograma.zona()`). No hay `@Scheduled`: se elige perezosamente en la lectura. | ✅ |
| VI. Cinco tabs | Toca Hoy (tarjeta del semáforo) y Comunidad (Ranking). Autorización del dueño en esta conversación; se anota en `AGENTS.md` con «lo que cambia y nada más». | ✅ (anotar) |
| VII. Documentar | D-nn (catálogo y regla de elección, capacidad de exportar), E-nn si aparece un error, `AGENTS.md` y `specs/README.md` del frontend. | ✅ |

**Soporte TI**: el rol `IT_SUPPORT` está en `origin/master` (fase 1, V100–V102; el `master` local estaba atrasado).
La fase 2 quedó en pausa (2026-10-19), así que la rama sale de `origin/master` ahora, con IT_SUPPORT incluido.

**Riesgo de migraciones**: las V103–V108 de la fase 2 y la V109 de la spec 005 no están integradas, y Flyway no tiene
`outOfOrder`. Regla acordada entre sesiones: antes de integrar, la migración tiene que ser mayor que la más alta de
`origin/master`; quien integra después renumera la suya.

## Project Structure

### Documentation (this feature)

```text
specs/006-mensaje-semaforo-y-podio/
├── spec.md
├── plan.md                 # este archivo
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── home-semaforo-mensaje.md
│   └── capacidad-exportar-ranking.md
├── data/
│   ├── mensajes-semaforo.json   # catálogo validado (115)
│   └── stickers/*.webp          # 15 stickers 512×512, fondo transparente
└── checklists/requirements.md
```

### Source Code

```text
Renaser-90-dias-backend/src/main/java/com/renaser/os/
├── points/
│   ├── domain/model/semaforo/mensaje/
│   │   ├── GrupoDelMensaje.java          # VERDE|AMARILLO|ROJO|INICIO|SIN_PROGRAMA|PAUSA|CUENTA_PAUSADA|GRADUADO
│   │   ├── MensajeDelSemaforo.java       # id, grupo, texto, variables, sticker, activo
│   │   ├── DatosDelMensaje.java          # nombre, dia, fase, habitoFlojo, habitoFuerte, faltan (opcionales)
│   │   ├── EleccionDelMensaje.java       # elige: candidatos con datos, sin vistos en 14 días, al azar
│   │   ├── RedaccionDelMensaje.java      # reemplaza variables; nunca deja llaves
│   │   ├── GrupoDelDia.java              # color/estado/participación → grupo
│   │   └── HabitosQueFaltan.java         # cálculo de {faltan}
│   ├── application/
│   │   ├── ports/out/semaforo/mensaje/   # CatalogoDeMensajesPort, MensajesElegidosPort, AzarPort
│   │   └── services/MensajeDelSemaforoService.java
│   ├── infrastructure/adapter/out/persistence/semaforo/mensaje/  # JpaEntity + repos + mappers
│   └── infrastructure/adapter/in/rest/home/ResumenHomeResponse.java  # + MensajeResponse
├── points/api/CumplimientoPorHabitoFinder.java   # nuevo: por hábito en un rango de fechas (lo implementa habits; antes decía habits/api, ver E-630)
├── users/api/ (se lee nombre y programaCompletado vía finder existente o ampliado)
├── shared/domain/Permission.java                 # + EXPORT_RANKING
└── users/api/UserRole.java                       # concesión a MENTOR_LEAD, ADMIN, ALCHEMIST (+ IT_SUPPORT)
src/main/resources/db/migration/V110__mensajes_del_semaforo.sql

Renaser-90-dias-frontend-/
├── assets/stickers/semaforo/*.webp               # los 15
├── src/features/semaforo/
│   ├── api/semaforoSchemas.ts                    # + mensaje opcional y tolerante en semaforoDeHoy
│   ├── components/MensajeDelSemaforo.tsx         # sticker + texto, fundido corto
│   ├── components/TarjetaSemaforoHoy.tsx         # lo monta debajo de la etiqueta
│   └── data/stickersDelSemaforo.ts               # nombre → require(...)
├── src/features/ranking/
│   ├── components/TarjetaDelPodioParaCompartir.tsx  # 1080×1920 fuera de pantalla
│   ├── hooks/useCompartirPodio.ts                # captureRef + shareAsync / descarga web
│   └── utils/podioParaCompartir.ts               # top 10, recorte de nombres, fecha
├── src/features/mentor/api/mentorSchemas.ts      # + canExportRanking nullish
├── src/features/admin/hooks/useCapacidades.ts    # + exportarRanking
└── src/screens/ComunidadScreen.tsx               # botón «Descargar» en la sección Ranking (solo eso)
```

**Structure Decision**: Backend hexagonal dentro de `points`, porque el semáforo ya vive ahí. Lo de hábitos entra por
un finder en `habits/api/`. En la app, por feature (`semaforo`, `ranking`); de `ComunidadScreen` solo se toca el botón.

> **Corregido 2026-10-19 (E-630, D-277).** Esto decía «un finder en `habits/api/`». `habits` ya depende de `points`
> (otorga puntos), así que `points` no puede importar `habits`: el finder se declara en `points.api` y lo implementa
> `habits`, igual que `ConteoDiarioHabitosFinder`. Tampoco hay `*JpaEntity` en los adaptadores nuevos: usan `JdbcClient`
> con mapeo a mano, como los otros tres del semáforo. Y no se creó el índice `(participante_id, fecha desc)`: la PK
> `(participante_id, fecha, grupo)` ya sirve la lectura de los 14 días.

## Fases de implementación (orden)

1. **Backend A**: V110 (catálogo sembrado desde `data/mensajes-semaforo.json` y tabla de elegidos) y dominio puro con
   pruebas.
2. **Backend B**: `CumplimientoPorHabitoFinder` en `habits` y el cálculo de `faltan` con `SemaforoDelDiaFinder`
   (*corregido 2026-10-19, SC-004: con lo de hoy que `/home` ya leyó; ver D-277*).
3. **Backend C**: servicio y adaptadores; `semaforo.mensaje` en `/home`; IT con el reloj en la madrugada UTC.
4. **Backend D**: permiso `EXPORT_RANKING` y `canExportRanking` en `/mentor/context`, con 403 y pruebas por rol.
5. **App A**: stickers, esquema tolerante y `MensajeDelSemaforo` en la tarjeta (Jest).
6. **App B**: capacidad, tarjeta 9:16, compartir y descarga web (Jest).
7. **Docs**: D-nn y E-nn, `AGENTS.md`, `specs/README.md`, `graphify update .`.
8. **E2E**: cuando la máquina esté libre (coordinado con las otras sesiones), backend local + base clonada + emulador.

## Complexity Tracking

Sin violaciones que justificar.
