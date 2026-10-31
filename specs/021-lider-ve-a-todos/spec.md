# Feature Specification: El líder de mentores ve a todos los aprendices, sin grupo

**Feature Branch**: `lider-ve-a-todos` (backend; la app se programa en paralelo con este contrato)
**Created**: 2026-10-31
**Status**: Implementada y probada (ver «Pruebas»)
**Input**: Pedido del dueño (2026-10-31), textual: «el líder de mentores puede ver la ficha de todos los aprendices sin
necesidad de estar en grupo, solo para este rol».

**Hoy** el líder (`MENTOR_LEAD`) solo lee a una persona entrando por un grupo con mentor
(`GET /api/v1/leadership/groups/{g}/people/{p}/week`, `/radar`, `/huecos` y `/cierres`; `AccesoDeLiderazgo` +
`PersonasDelGrupo`). A quien no tiene grupo, está en la recepción o tiene un grupo sin mentor no lo puede ver.

## Alcance

Solo lectura. Una lista de **todos** los aprendices que se muestran y las mismas cuatro lecturas de una persona, sin
exigir grupo. Sin escrituras nuevas, sin tablas nuevas y **una sola migración** (V124, ver «Datos»).

**Quién**: `MENTOR_LEAD`, `ADMIN` y `ALCHEMIST` con la cuenta activa — la misma regla que `AccesoDeLiderazgo`. La entrada
nueva de la app es solo para el líder; Administración ya ve a todos por su ficha y puede usar estas rutas igual.
Rechazados con **403**: `MENTOR`, `TRAINEE_GUIDE`, `TRAINEE`, Soporte TI y cualquier rol personalizado que no herede de uno
autorizado (el guard mira el rol base, `UserSummary.role()`; uno que hereda de `MENTOR_LEAD` pasa, uno que hereda de
`MENTOR` no). Una cuenta suspendida, **403** aunque su sesión siga viva. El 403 va antes que el 404: quien no tiene
permiso no averigua qué ids existen.

**Interruptor de Soporte TI**: las rutas nuevas llevan la misma funcionalidad que las del grupo, `lider.cumplimiento`.

## Contrato

### 1. `GET /api/v1/leadership/people?q=&page=0&size=30`

La lista de TODOS los aprendices que se muestran.

- **Quiénes**: rol aprendiz (`TRAINEE`), cuenta activa, con fila en el programa; **no** las cuentas de prueba de «ver
  como» ni las suspendidas (las cerradas quedan suspendidas). Es el criterio de «se muestra» de `PersonasDelGrupo`
  (spec 005, FR-002 y SC-006), sin exigir grupo. El mentor o la guía con programa propio **no** entran: no son aprendices.
- **Búsqueda**: `q` filtra por nombre, sin tildes ni mayúsculas; cada palabra escrita tiene que estar en el nombre.
  Vacío o ausente no filtra.
- **Orden**: por nombre (sin tildes ni mayúsculas); ante homónimos, por id, para que dos páginas seguidas no repitan ni
  salten a nadie.
- **Paginación**: `page` desde 0, `size` de 1 a 100 (30 por defecto); fuera de rango, **400**. Una página más allá del
  final viene vacía y con el `total`.
- **Respuesta**:
  ```json
  { "items": [ { "personId": "uuid", "nombre": "…", "photoPath": "…|null", "grupoId": "uuid|null",
                 "grupoNombre": "…|null", "mentorNombre": "…|null", "diaPrograma": 12 } ],
    "page": 0, "size": 30, "total": 57 }
  ```
  `total` cuenta lo que coincide con la búsqueda, no solo la página. `diaPrograma` es `null` si todavía no eligió su Día 1.
  `photoPath` es la foto de la persona (la que las demás listas llaman `avatarUrl`).
- **No devuelve el correo** ni ningún dato de contacto (la fila se arma a mano; una prueba fija sus campos).
- **No registra consulta**: abrir una lista no es abrir la ficha de nadie.

**Qué grupo se muestra**: la persona puede estar en dos grupos a la vez (D-139) o en ninguno. Se elige el grupo regular
**con mentor** primero (el único al que se le puede avisar), después el regular sin mentor y al final la recepción; a igual
categoría, el principal de la persona y, si no, el de nombre menor (`GrupoDeLaPersona.elegir`). La lista y la ficha usan
la misma regla, así que la misma persona cae siempre en el mismo grupo. La recepción sale con su nombre y sin mentor;
quien no está en ningún grupo, con `grupoId`, `grupoNombre` y `mentorNombre` en `null`.

### 2. `GET /api/v1/leadership/people/{personId}/week?semanaHasta=`

El **mismo cuerpo, campo por campo**, que `/groups/{g}/people/{p}/week` (la ventana, los días, las evidencias,
`primeraSemana`, `programacion`…), sin exigir grupo. Para alguien con grupo con mentor es idéntico al de la ruta por grupo
(una prueba compara los dos JSON). La fecha se pide con **`semanaHasta`**, como en la ruta por grupo (ajuste del contrato
del 2026-10-31: la app ya arma esa URL); **`hasta`** se acepta como alias, y si vienen los dos manda `semanaHasta`. Un día
que no es viernes o una semana futura, **400**.

- `grupoId` es el grupo con que se la mira (regla de arriba) o `null`; `mentor` es el mentor de ese grupo o `null`.
- **Campo nuevo, opcional y aditivo: `puedeAvisarAlMentor: boolean`** (`mentor != null`). `true` si la persona tiene un
  grupo regular con mentor al que se le puede avisar; `false` si no tiene grupo, está en la recepción o su grupo no tiene
  mentor. **Se agrega también a la respuesta de la ruta por grupo**, con `true` donde hoy se ofrece «Avisar a su mentor».
  Una app anterior lo ignora.
- **Registra la consulta** (spec 005, FR-016), igual que la del grupo: quién, con qué rol, a quién, desde qué grupo y a qué
  mentor, la ventana y cuándo. Para quien no tiene grupo, el grupo y el mentor del registro van en `NULL`.

### 3. `GET /api/v1/leadership/people/{personId}/radar`, `/huecos?semana=` y `/cierres?semana=`

Los mismos cuerpos que sus versiones `/groups/{g}/people/{p}/…`, sin exigir grupo, con los mismos nombres de parámetro
(`cursor` en el Radar, `semana` —el lunes— en huecos y cierres). Quién deja constancia de la consulta es lo mismo que en el
grupo: **huecos y cierres sí; el Radar no** (spec 012, FR-015: la ficha ya registra la suya).

### Errores

| Caso | Respuesta |
|---|---|
| Sin sesión | 401 |
| Rol que no es del liderazgo, o cuenta suspendida | 403 |
| La persona no existe, no es un aprendiz del programa, está suspendida o es una cuenta de prueba | 404 (sin distinguir cuál) |
| `page` negativa, `size` fuera de 1–100, fecha que no es viernes / lunes o es futura | 400 |

Los caminos que terminan en 403, 404 o 400 no leen nada ni dejan registro.

## Datos

**V124**: `consultas_de_ficha.grupo_id` pasa a aceptar `NULL`. Es la única forma honesta de registrar la consulta de alguien
sin grupo (un uuid cero o el id de la persona serían un dato falso en una tabla de auditoría). `DROP NOT NULL` no dispara
el trigger append-only (es por fila y por `TRUNCATE`) y no reescribe filas. El resumen de las 20:00 al mentor lee solo
filas con `mentor_id`, y quien no tiene grupo no tiene mentor: esas consultas se registran pero no avisan a nadie.

## Cómo se hizo (sin duplicar)

- `PersonasDelPrograma` (nuevo, hermano de `PersonasDelGrupo`): quiénes se muestran, el grupo con que se mira a cada uno y
  la lista paginada. Lee por APIs públicas (`AcompanamientoFinder.gruposOperativos`, `ParticipacionProgramaFinder`,
  `UserSummaryFinder`, `CuentasDePruebaFinder`); el padrón se lee entero (cientos de aprendices) y se filtra y ordena en
  memoria, y lo caro (programa y grupo) se pide solo para la página.
- `Mirada` (persona + grupo, que puede faltar) es lo único que cambia entre entrar por un grupo o sin él. `SemanaDePersonaService`,
  `HuecosDePersonaService`, `CierresDePersonaService` y `RadarDePersonaService` suman un método (`…DeUnaPersona`) que resuelve
  la `Mirada` y sigue por **el mismo camino** de lectura, armado y registro que el del grupo.
- `PersonasDelProgramaController` con los cinco GET bajo `/api/v1/leadership/people`.
- Cero endpoints de escritura, cero cambios en `mentoring`, `rocks`, `habits` ni `points`.

## Pruebas

`BusquedaPorNombreTest`, `GrupoDeLaPersonaTest` (reglas puras); `LiderVeATodosServiceTest` (la lista, la ficha, el Radar,
huecos y cierres, quién entra, qué se registra); `PersonasDelProgramaControllerTest` (contrato JSON, 403/404/400, cuerpo
idéntico al del grupo, `semanaHasta` y `semana`); `LiderVeATodosIT` (Tomcat y Postgres reales: sin grupo 200, mentor 403,
cuenta de prueba 404, registro con grupo `NULL`, cuerpo igual al del grupo); `ArchitectureTest`.

## Fuera de alcance / abierto

- La pantalla de la app (la programa otro, con este contrato).
- «Avisar a su mentor» sigue siendo la observación de siempre; la app la ofrece con `puedeAvisarAlMentor`.
- La lista no ordena por semáforo ni muestra color: el pedido es llegar a la ficha. Si se quiere ordenar por color, es otra
  lectura (el semáforo por persona de todo el programa).
