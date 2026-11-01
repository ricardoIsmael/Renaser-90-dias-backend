# Implementation Plan: Código Renaser con la voz

**Branch**: `030-codigo-renaser-con-audio` (backend) y `030-codigo-renaser-con-voz` (app) | **Date**: 2026-10-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/030-codigo-renaser-con-audio/spec.md`, ya con las decisiones del dueño (030-1 = A, 030-2 = B).

## Summary

Dos cosas en el formulario del Código Renaser, que es uno solo para las cuatro entradas (pantalla obligatoria de los días 1 a 7,
Hoy, Mi Radar y el aviso del teléfono):

1. **Dictar** cada una de las cuatro respuestas de texto y **escuchar** cada pregunta con la voz Dora grabada (sin red).
   Todo en la app. El dictado reutiliza `useDictado` (reconocimiento de voz del teléfono, el mismo del chat de SER), con un seguro nuevo:
   cada instancia solo reacciona a **su** sesión de micrófono. Las voces son cuatro MP3 hechos con el mismo `generar-sonidos.sh` de las
   alarmas.
2. **Marcar el registro como «dictado»** (030-2 = B): una columna booleana en `registros_radar` (V132), un campo opcional `dictado` en
   el pedido y en las respuestas que ya leen la persona, el mentor o guía, el líder y Administración. La app lo muestra a quien
   acompaña como una línea de texto en cada registro dictado.

Nada más cambia: preguntas, horarios, obligatoriedad, un registro por hora, el 409, el semáforo, los puntos, Mi Radar, quién ve qué.

## Technical Context

**Language/Version**: Java 25 / Spring Boot (backend); TypeScript 6 estricto / Expo SDK 57 / React Native 0.86 (app)

**Primary Dependencies**: ya instaladas — `expo-speech-recognition` (dictado), `expo-audio` (reproducir las voces), `react-native-reanimated`
(el `Presionable` de siempre), `expo-haptics` (vía `tacto`). **Ninguna dependencia nueva, ningún código nativo nuevo.**

**Storage**: PostgreSQL, tabla existente `renaser.registros_radar` + una columna (`dictado boolean not null default false`). El audio no
se guarda en ningún lado; el borrador local de la app (AsyncStorage) suma qué respuestas se dictaron.

**Testing**: backend — pruebas unitarias del dominio y del servicio, de contrato de los controladores (`@WebMvcTest`), de persistencia
(Testcontainers) y `ArchitectureTest`; app — `tsc` y Jest (hook de dictado con y sin módulo, la prueba del seguro de sesión, el
formulario, las fichas, el esquema, los MP3 y `sonidosElegibles`); Maestro (flujo escrito, no corrido).

**Target Platform**: Android (la app hoy es solo Android; el código no depende de eso).

**Project Type**: mobile-app + web-service (monolito modular hexagonal).

**Performance Goals**: «Escuchar» suena en menos de 1 s sin red (SC-005): es un archivo local.

**Constraints**: +≈190 KB al APK (los cuatro MP3; el tope de `sonidosElegibles` es 2 MiB y hoy se usan ≈1,65 MiB); el audio nunca sale
del teléfono; todo texto en español de Perú con tuteo; áreas táctiles de 48.

**Scale/Scope**: 1 columna, 3 DTO de respuesta, 1 de entrada; 1 hook compartido ajustado, 2 hooks y 2 componentes nuevos, 1 componente
de fichas ajustado, 4 MP3.

## Constitution Check

*GATE: pasa antes de la investigación y se vuelve a revisar después del diseño.*

| Principio | Cómo se cumple |
|---|---|
| I. Dependencias hacia el dominio | `dictado` entra en `RegistroRadar` (dominio, sin JPA) con un método de intención `comoDictado()`; el controlador solo mapea el pedido al comando; el mapper traduce a la columna. `leadership` lee el dato por `habits/api` (`CodigoRenaserDePersonaFinder`), sin importar tipos de `habits.domain`. |
| II. No se inventan reglas | Las dos decisiones abiertas las dio el dueño. Lo que se asume y se avisa: «dictado» = al menos una respuesta dictada (un dato por registro); la app no puede leer el volumen del sistema (ver `research.md` R5) y lo cubre con una línea de ayuda. |
| III. Pruebas | Dominio sin Spring, servicio, contratos, persistencia con Postgres real, `ArchitectureTest`; en la app, Jest de cada pieza nueva. Sin endpoints nuevos: la autorización negativa ya existe (los mismos endpoints). |
| IV. Historia, no reescritura | V132 es aditiva (`ADD COLUMN … DEFAULT false`), con cabecera que justifica. `registros_radar` sigue append-only: el dato nace con el registro y no se edita. |
| V. Tiempo del participante | No toca horarios ni relojes. |
| VI. Los cinco tabs | Se toca el formulario del Código Renaser (Hoy y Yo → Mi Radar): lo pide el dueño en la spec y queda anotado en `AGENTS.md` con «lo que cambia y nada más». Ningún tab cambia de forma. |
| VII. Documentar | D-297 en `docs/MODULOS_A_AVANZAR.md` §8, `docs/MODULO_HABITS.md`, `AGENTS.md` del frontend, política de privacidad (FR-016). |

**Resultado**: sin violaciones. Complexity Tracking vacío.

## Project Structure

### Documentation (this feature)

```text
specs/030-codigo-renaser-con-audio/
├── spec.md              # con las decisiones del dueño (Clarifications)
├── plan.md              # este archivo
├── research.md          # decisiones R1–R8
├── data-model.md        # la columna y el dato `dictado`
├── contracts/api.md     # el campo `dictado` en cuatro respuestas y un pedido
├── quickstart.md        # cómo comprobarlo de punta a punta
└── tasks.md
```

### Source Code

```text
# Backend (módulos `habits` y `leadership`)
src/main/resources/db/migration/V132__codigo_renaser_dictado.sql
src/main/java/com/renaser/os/habits/
├── domain/model/radar/RegistroRadar.java                     # + dictado, comoDictado()
├── application/ports/in/radar/RegistrarCheckInRadarUseCase.java  # comando + dictado
├── application/services/RadarService.java                    # lo pasa al registro
├── application/services/DiarioYRadarService.java             # SER registra con dictado=false
├── application/services/CodigoRenaserDePersonaService.java   # lo expone al otro módulo
├── api/CodigoRenaserDePersonaFinder.java                     # RegistroDelRadar + dictado
└── infrastructure/adapter/
    ├── in/rest/radar/{RegistrarCheckInRadarRequest,RegistroRadarResponse,RadarController}.java
    └── out/persistence/radar/{RegistroRadarJpaEntity,RegistroRadarPersistenceMapper}.java
src/main/java/com/renaser/os/leadership/infrastructure/adapter/in/rest/RadarDeLaPersonaResponse.java   # + dictado

# App
src/features/renasia/hooks/useDictado.ts          # seguro de sesión propia + texto de error por destino
src/features/renasia/utils/dictado.ts             # mensajeDeErrorDeVoz(codigo, destino)
src/features/radar/
├── config/configRadar.ts                         # (sin cambios de reglas)
├── config/vozDelRadar.ts                         # campo → MP3 + qué dice cada voz
├── vocesDelRadar.json                            # lo que dice cada voz (lo leen la app y el script)
├── hooks/useDictadoDelRadar.ts                   # un micrófono a la vez sobre las cuatro respuestas
├── hooks/useEscucharPregunta.ts                  # una voz a la vez
├── utils/dictadoDelRadar.ts                      # reglas puras: sumar sin pasar de 2 000, marcas de dictado
├── components/BotonDeVoz.tsx                     # 48 de alto, ícono + palabra, estado anunciado
├── components/VozDeLaPregunta.tsx                # fila «Escuchar» + «Dictar» + líneas de ayuda/error
├── components/CodigoRenaserModal.tsx             # integra lo anterior
├── storage/borradorRadar.ts                      # + qué respuestas se dictaron
├── api/radarSchemas.ts, types/radar.types.ts     # + dictado
src/features/mentor/components/CodigoRenaserDelAlumno.tsx   # «Dictado por voz…» en cada registro dictado
assets/sonidos/voz_radar_que_{hago,pienso,siento,evito}.mp3 + app.json
scripts/sonidos/{tabla_de_voces.py,generar_voces.py}        # leen vocesDelRadar.json
public/privacidad/index.html                                # FR-016
.maestro/codigo-renaser/41-dictar-y-escuchar.yaml
```

**Structure Decision**: el cambio vive donde ya vive el Código Renaser (`habits` en el backend, `features/radar` en la app). Lo único
compartido que se toca es `useDictado` (de `features/renasia`), porque es lo que el dueño pidió reutilizar, y se toca de forma
aditiva y probada.

## Complexity Tracking

Sin violaciones que justificar.
