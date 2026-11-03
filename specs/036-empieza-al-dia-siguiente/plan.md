# Implementation Plan: El programa empieza al día siguiente, sin elegir el Día 1

**Branch**: `036-empieza-al-dia-siguiente` (backend y app) | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md)

## Summary

El servidor deja de ofrecer una ventana de tres fechas y pasa a una sola regla: **el Día 1 es mañana en la zona de la persona**. Es un cambio de dominio chico
(`ParticipacionPrograma.activarPrograma` y `opcionesDeActivacion`), de contrato (la fecha del `POST` pasa a ser opcional y, si viene, tiene que ser mañana) y de textos.
La app reemplaza el selector por una pantalla corta («Tu programa empieza mañana … Entendido») y renombra todas las entradas. **Sin tabla ni migración nueva** (la V160 reservada no se usa).

## Technical Context

**Language/Version**: Java 25 / Spring Boot (según `pom.xml`); app Expo SDK 57 / TypeScript estricto.
**Storage**: ninguna tabla ni columna nueva. `participantes_programa.fecha_inicio` y `programa_activado_en` se escriben igual que hoy; solo cambia qué fecha se acepta.
**Testing**: JUnit 5 + AssertJ (dominio y servicio sin Spring, con `FixedClock` en la madrugada UTC), `@WebMvcTest` del controller, IT con Testcontainers y el `RelojDePrueba` compartido (regla 03, E-649: ninguna IT declara su propio `Clock`), `ArchitectureTest`; app: `tsc --noEmit` y Jest.
**Constraints**: reglas de `.claude/rules/` (02 tiempo y zonas: el día de alguien sale de `clock.now().atZone(participacion.timezone())`, nunca de `clock.today()`).

## Constitution Check

- **Hexagonal**: el cambio vive en `users` (dominio → caso de uso → controller); ningún módulo ajeno se toca. El controller sigue tonto: deserializa y llama a un caso de uso.
- **Tiempo (02)**: «mañana» se deriva de `hoyEnMiZona(clock).plusDays(1)`, la misma función que ya usaba `activarPrograma`. El test de dominio incluye el reloj entre 00:00 y 05:00 UTC.
- **No inventar reglas**: la regla es del dueño (D-309); los supuestos están en `spec.md`.
- **Documentación (05)**: D-309 en `docs/MODULOS_A_AVANZAR.md`; «Corregido» en D-66/D-261 y en `docs/MODULO_ONBOARDING.md`.

## Decisiones técnicas

1. **Un `startDate` opcional que, si viene, debe ser mañana (400 si no).** Alternativa descartada: ignorarlo en silencio. Con un APK anterior que guardó las fechas antes de medianoche, ignorar activaría un día
   distinto al que la persona vio; rechazar con 400 hace que ese APK relea las fechas (E-594) y la app nueva se refresque.
2. **`validStartDates` se conserva** (una sola fecha) en vez de un campo nuevo: así el APK anterior, que dibuja la lista, no se rompe, y la app nueva usa `validStartDates[0]` también contra un servidor anterior (donde es mañana por orden).
3. **El plazo no viaja del servidor**: la app ya tiene la formación de la sesión (`useFormacionDeLaSesion`) y `plazoDelObjetivo`. No hace falta un campo en el `GET`.
4. **`POST /mentor/activate-tracking` no se toca** (FR-007).
5. **Se borra `MAX_DIAS_ESPERA_ACTIVACION`**; `opcionesDeActivacion` queda como `List.of(inicioAlActivar(clock))` para que el caso de uso y el controller no cambien de forma.

## Cambios por archivo (backend)

| Archivo | Cambio |
|---|---|
| `users/domain/.../ParticipacionPrograma.java` | `inicioAlActivar(clock)` (mañana en su zona); `activarPrograma(fechaPedida?, clock)`; `opcionesDeActivacion` de una fecha; fuera `MAX_DIAS_ESPERA_ACTIVACION` y sus javadocs |
| `users/application/.../ActivateProgramUseCase.java` | `startDate` opcional en el comando |
| `users/application/.../ConsultarActivacionProgramaUseCase.java` | javadoc: una fecha |
| `users/infrastructure/.../ActivarProgramaRequest.java`, `ParticipacionProgramaController.java` | cuerpo y `startDate` opcionales; javadoc del contrato |
| `docs/MODULOS_A_AVANZAR.md`, `docs/MODULO_ONBOARDING.md`, `docs/MODULO_USERS.md` | D-309 y «Corregido» |

## Pruebas (backend)

- `ParticipacionProgramaTest`: mañana en la madrugada UTC, viernes, fin de mes y de año; una sola opción; fecha ≠ mañana → `IllegalArgumentException`; sin fecha activa a mañana; idempotencia; zona distinta a Lima.
- `RelojProgramaServiceTest`: `consultarEstado` devuelve una fecha; `activarPrograma` sin fecha; el personal sin fila.
- `ParticipacionProgramaControllerTest`: `GET` con una fecha; `POST` sin cuerpo y con `{}`; 400 y 409.
- IT (`ElegirDiaUnoDelPersonalIT`, `ElegirDiaUnoDelPersonalSinFilaIT`, `ActivacionProgramaConcurrenteIT`, `MapaDelPersonalAutenticacionIT`): ajustadas; una IT nueva `EmpiezaAlDiaSiguienteIT` con HTTP real, `RelojDePrueba` y limpieza de lo que siembra.
- `ArchitectureTest` completo.

## App (resumen; detalle en el AGENTS.md del frontend, 2026-11-02)

`ActivarProgramaScreen` → pantalla corta; `onboardingApi`/tipos con `startDate` ya presente; `BotonElegirDiaUno` → «Empezar mañana»; `filaDelPrograma`, `PlanSinPrograma`, `EntradaAlProgramaPropio`, `InvitacionProgramaPropio`, `TrainingScreen`,
`vistaDeMiRadar`/`miRadarEnYo`, `AvisoConsultaDiaUno`, `textosDelDiagnostico`; pruebas de Jest, `e2e/dia-uno/` y `.maestro/**`.
