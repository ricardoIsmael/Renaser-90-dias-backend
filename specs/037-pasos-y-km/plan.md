# Implementation Plan: Los pasos del teléfono llenan los kilómetros diarios

**Branch**: `037-pasos-y-km` (backend y app) | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md)

## Summary

La app lee de **Health Connect** (Android 14 o más) el agregado del día de pasos y distancia, sin contar lo escrito a mano; con la meta alcanzada ofrece «Registrar». El servidor recibe km, pasos, fuente y hora de lectura
en un endpoint nuevo que **valida y cierra el hábito por `RegistroService.completar`** (puntos, ventana, evento y racha siguen en un solo lugar). Lo guarda en `registros_habito` (V161: cinco columnas; sin tablas),
la meta en `habitos.meta_medicion`. Mentor, líder y Administración ven la fuente y «por revisar»; el ranking marca «auto». Android menor a 14, sin Health Connect y el iPhone web se quedan con la captura.

## Technical Context

**Language/Version**: Java 25 / Spring Boot (según `pom.xml`); app Expo SDK 57, React Native 0.86.3, TypeScript estricto.
**Primary Dependencies (backend)**: ninguna nueva. **(app)**: `react-native-health-connect@4.1.3` (exacta) y `expo-build-properties@~57.0.22`; revisadas en `research.md`.
**Storage**: PostgreSQL, **V161** (columnas en `registros_habito` y `habitos`). Sin tablas.
**Testing**: JUnit 5 + AssertJ + Mockito, `@WebMvcTest`, IT con Testcontainers, `ArchitectureTest`; app: `tsc --noEmit` + Jest (`jest-expo`); Maestro escrito sin correr.
**Target Platform**: Android 14+ para los pasos; el resto de la app sigue en Android 8+ (`minSdk` 26) y web.
**Constraints**: ningún servicio en segundo plano; una lectura por apertura/foco; el servidor no confía en el teléfono.

## Constitution Check (reglas del repo)

| Regla | Cómo se cumple |
|---|---|
| Hexagonal (01) | La regla vive en `habits/application/politica/PoliticaKilometros` (pura); el caso de uso `RegistrarMedicionAutomaticaUseCase` en `application/ports/in`; el controller solo deserializa, valida y llama **un** caso de uso; la meta pasa por un puerto `out` (`MetaDeMedicionPort`) con adaptador JDBC. Otros módulos leen por `habits.api` (`ObligacionHabito.medicion`) y por `points.api.MedicionAcumuladaFinder`. |
| Tiempo y zonas (02) | «El día del registro» es `fechaEjecucion` en la zona del participante; la lectura debe ser de ese día (`leidoEn >= inicio del día en su zona`); D-259 lo impone `RegistroService.completar`. Pruebas con el reloj en la madrugada UTC (00:00–05:00). |
| Pruebas (03) | Unitarias de la política y del servicio, IT del endpoint completo (404/403/409/400, idempotencia, rol), `ArchitectureTest`. Las IT no suponen base vacía y limpian lo que siembran. |
| Migraciones (04) | V161 con cabecera que justifica; CHECK solo con datos de la fila; nada de editar migraciones aplicadas. |
| Documentación (05) | D-310 en `docs/MODULOS_A_AVANZAR.md` §8; `CLAUDE.MD` apunta a la spec. |
| Alcance (00) | Sin Recording API, sin B/C, sin iPhone. Los supuestos van a la vista en la spec. |

## Estructura

```text
backend  habits/domain/model/medicion/    OrigenMedicion (+HEALTH_CONNECT), MedicionDiaria (+lectura), LecturaAutomatica, DispositivoDeMedicion
         habits/application/politica/     PoliticaKilometros (+meta, +validación automática)
         habits/application/ports/in/medicion/   RegistrarMedicionAutomaticaUseCase, MetaDeMedicionAdminUseCases
         habits/application/ports/out/medicion/  MetaDeMedicionPort
         habits/application/services/     MedicionAutomaticaService, MetaDeMedicionAdminService, MedicionesDelDia (+meta, +origen)
         habits/infrastructure/adapter/in/rest/registro/  AutoMeasurementRequest, HabitTrackController (+endpoint)
         habits/infrastructure/adapter/in/rest/habitoadmin/ MetaDeMedicionAdminController
         habits/infrastructure/adapter/out/persistence/   columnas nuevas en RegistroHabitoJpaEntity + mapper; MetaDeMedicionJdbcAdapter
         habits/api/ObligacionHabito (+medicion); mentoring/api/ObligacionDelDia (+medicion); points/api/MedicionAcumuladaFinder (+automáticos)
         src/main/resources/db/migration/V161__pasos_automaticos_y_meta_de_kilometros.sql
app      src/features/pasos/             api, utils (puras), hooks, components
         plugins/withHealthConnect.js    (manifest + actividad de la política de privacidad)
         app.json, eas.json, package.json, public/privacidad/index.html
```

## Decisiones técnicas

1. **Un gesto, no un bypass.** El cierre automático llama a `completar` con `GestoCompletar.PROPIO_DEL_HABITO` (la política del gesto genérico exige foto y número escrito; este camino trae su propia validación en `PoliticaKilometros`). El DTO del endpoint genérico no puede producir este gesto.
2. **La validación es una función pura** (`PoliticaKilometros.validarMedicionAutomatica`) que devuelve una decisión y las banderas; el servicio la orquesta con el reloj y la zona.
3. **Idempotencia** por estado del registro, no por clave de idempotencia: el registro del día es único (`UNIQUE` participante, hábito, fecha) y un `COMPLETADO` no se reabre.
4. **El «por revisar» no bloquea.** Se guarda y el mentor lo ve (supuesto 6 del dueño).
5. **Meta por hábito, no global:** `habitos.meta_medicion`; un `NULL` es 5 km. Cada formación (spec 023) tiene su propio hábito, así que su propia meta.
6. **La app decide la fuente de la hoja, el servidor guarda la etiqueta.** La etiqueta es texto de hasta 60 caracteres que lee el mentor; no se usa para ninguna decisión.
7. **Ninguna llamada de red en segundo plano.** La lectura ocurre cuando Training está a la vista, al volver a primer plano y al deslizar, con 30 s de enfriamiento.

## Build nativo (la app)

- `react-native-health-connect` es **código nativo**: hace falta un build nuevo (no llega por aire; no sirve Expo Go ni el APK publicado).
- `app.json`: plugins `expo-build-properties` (`android.minSdkVersion: 26`) y el plugin local `./plugins/withHealthConnect` (reemplaza a `app.plugin.js` de la librería: agrega la actividad de la política de privacidad **nuestra** en vez de apuntar a `MainActivity`); permisos `android.permission.health.READ_STEPS` y `READ_DISTANCE`.
- **`minSdk` 24 → 26** (lo exige `androidx.health.connect:connect-client:1.1.0`). Deja fuera Android 7.x (API 24–25).
- **EAS:** perfil `debug-pasos` (`gradleCommand: :app:assembleDebug`, distribución interna): produce el mismo APK de depuración que `expo run:android`, que carga el JS desde Metro. **No se agrega `expo-dev-client`**: cambiaría cómo arrancan `expo start` y `expo run:android` y los flujos de Maestro (`launchApp` caería en el selector de servidores). El perfil `development` ya existente declara `developmentClient: true` y **no puede construirse hasta que `expo-dev-client` esté instalado**; si el dueño prefiere ese camino, es sumar `expo-dev-client@~57.0.20`.
