# Qué falta comprobar · backend · 14 de septiembre de 2026

Acompaña a `docs/pendientes/PARA_COMPROBAR_2026-09-14.md` del repo del frontend, que tiene el
cuadro completo. Acá va solo lo de este repo.

Rama: `avance-diseno-y-backend-completo`.

---

## Verificado

- **3011 pruebas unitarias**, 0 fallos, 394 clases.
- **52 pruebas de integración** con Testcontainers sobre Docker (Postgres y Redis reales), 0 fallos.
- **`ArchitectureTest` 8/8** — sin ciclos de Spring Modulith. Fue el que obligó a que
  `DiasConHabitoCumplidoFinder` viva en `points.api` y lo implemente `habits`: al revés hay ciclo,
  porque `habits` ya depende de `points` para otorgar puntos.
- **`EndpointAuthorizationDeclarationTest` 4/4** — el endpoint nuevo declara `@RequiresPermission`.
- **CORS acepta `http://localhost:8081`** con credenciales, comprobado con un preflight real contra
  la instancia corriendo. No hace falta tocar nada.

---

## Lo que hay que hacer antes de creerle a la app

1. **Reiniciar el backend.** Mientras corra el build viejo, la racha sigue saliendo de la fila que
   nadie actualiza (se ve `0 días` con `récord 3`) y `POST .../messages/share-wall-post` responde
   404.
2. **Probar compartir de punta a punta**: compartir una foto del Muro → abrir la conversación → ver
   la foto → **volver a abrirla al día siguiente**. Ese último paso es el único que prueba el
   arreglo: antes la foto moría a los 15 minutos (`X-Amz-Expires=900`). Ver E-182.
3. **Confirmar los parámetros de AWS** antes de desplegar: `CORS_ORIGENES`, `RESET_PASSWORD_URL`,
   `ACTIVATE_ACCOUNT_URL`. Si falta alguno **no falla el arranque**: hereda el default y el problema
   aparece en la cara del usuario.
4. **`STORAGE_PROVEEDOR=s3`**, o ninguna subida funciona.

---

## No se hizo

| Qué | Estado |
|---|---|
| ~~**Graduación al día 90**~~ | ✅ **Hecho el 2026-09-15 (D-125).** `ParticipacionPrograma.graduarSiLlegoAlDiaNoventa`, llamado desde `sincronizarDiaDelPrograma` — el mismo barrido horario del reloj, sin un segundo cron. Derivada del día, no atada al instante: una noche caída no le cuesta la graduación a nadie, y las filas que ya estaban en el día 90 se gradúan en la corrida siguiente. Reglas y lo que sigue abierto: [`FEATURE_POST_PROGRAM.md`](FEATURE_POST_PROGRAM.md) |
| **Clasificar el patrón de cada mensaje del agente** | `mensajes_renasia` guarda quién, el rol, el texto completo, con qué agente y cuándo — pero **nada dice de qué patrón se trata** (victimismo, procrastinación, apego, excusa, disciplina, miedo a vender, recaída). Sin esa columna hay texto pero nada agregable, y el informe 80/20 no se puede construir |
| **Coherencia diaria** | El hueco más grande. `RegistrarCoherenciaDiariaUseCase` **no tiene un solo llamador**: `historial_coherencia` está vacía, las rachas no avanzan por esa vía, el ranking ordena a todos por la misma constante y el snapshot por cohorte lanza excepción. **No está bloqueado por código sino por una decisión**: falta definir cómo se calcula |

---

## Sueltos

- **`docs/FEATURE_POST_PROGRAM.md` no existe**, pero el javadoc de `ParticipacionPrograma` lo
  referencia. Ahí deberían vivir las reglas del post-programa.
- **`core.longpaths` quedó activado globalmente en git** (2026-09-14). Sin eso **no se puede crear
  un worktree de este repo en Windows**: 21 archivos pasan el límite de 260 caracteres, porque los
  nombres hexagonales son largos (`ConsultarProgresoParticipanteCalendarPersistenceAdapterTest.java`
  = 154 caracteres de ruta relativa) y un worktree cuelga un nivel más abajo. Verificado después de
  activarlo: los 2590 archivos se materializan bien.
- **`JAVA_HOME` de esta máquina apuntaba a `C:\Program Files\Android\Android Studio\jbr`**, no al
  JDK 25. Con esa ruta, `./mvnw test` termina en **exit 0 sin correr una sola prueba** (E-111). La
  buena es `C:\Program Files\Java\jdk-25.0.2`, y la verificación no es el código de salida sino la
  línea `Tests run:`.
