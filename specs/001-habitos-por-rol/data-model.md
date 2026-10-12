# Data Model: Hábitos globales por rol

## Migración `V96__audiencia_de_habitos_por_rol.sql`

Cabecera obligatoria (constitución IV): qué resuelve (hábitos globales solo para ciertos roles, con
vigencia estricta desde el día siguiente), por qué no alcanza el modelo (no hay dónde guardar la
audiencia ni su historia; `usuarios` no guarda historia de rol), por qué los nombres, y por qué
`text[]` y no FK a `roles` (SUPERADA, D-21) ni `rol_usuario[]` (research R-1).

```sql
-- Versiones de la audiencia de un hábito global. Vacío = para todos.
CREATE TABLE renaser.audiencias_habito (
    id            uuid        PRIMARY KEY,
    habito_id     uuid        NOT NULL REFERENCES renaser.habitos(id) ON DELETE CASCADE,
    roles         text[]      NOT NULL DEFAULT '{}',
    rige_desde    timestamptz NOT NULL,
    cambiado_por  uuid        NOT NULL,          -- usuarios.id, sin FK: es historia (como ajustes)
    cambiado_en   timestamptz NOT NULL,
    CONSTRAINT audiencias_habito_roles_sin_nulos CHECK (array_position(roles, NULL) IS NULL)
);
CREATE INDEX audiencias_habito_vigencia_idx ON renaser.audiencias_habito (habito_id, rige_desde DESC);

-- Historial del rol de cada persona, anotado por habits al recibir RolDeUsuarioCambiadoEvent.
CREATE TABLE renaser.historial_rol_participante (
    usuario_id  uuid        NOT NULL,
    rige_desde  timestamptz NOT NULL,            -- epoch (1970-01-01) para el rol previo al primer cambio
    rol         text        NOT NULL,
    PRIMARY KEY (usuario_id, rige_desde)
);
```

- **Append-only**: ninguna de las dos tablas tiene UPDATE ni DELETE desde el código (salvo el
  `ON DELETE CASCADE` al eliminar un hábito **sin** historia, que solo es posible si nunca generó
  registros).
- **Sin datos semilla**: un hábito sin filas en `audiencias_habito` es para todos (SC-003).
- **Versión base**: el primer cambio de un hábito sin filas inserta antes `('{}', rige_desde = epoch)` (research R-2,
  corrección del 2026-10-12).
- **Solo hábitos `SISTEMA`**: lo impone el dominio (la audiencia de un personal no existe), no un
  CHECK, porque depende de otra tabla.
- Idempotencia del listener: la PK `(usuario_id, rige_desde)` + `ON CONFLICT DO NOTHING` evita
  duplicar si el outbox reentrega el evento.

## Dominio (`habits/domain/model/audiencia/`)

| Tipo | Qué es | Reglas |
|---|---|---|
| `CodigoDeRol` (record) | Código opaco de rol (`"MENTOR"`) | No vacío, mayúsculas `[A-Z_]+`. No conoce los roles concretos |
| `Audiencia` (record) | `Set<CodigoDeRol> roles` | `paraTodos()` si está vacío. `aplicaA(CodigoDeRol rol)`: vacío → true; si no, `roles.contains(rol)` |
| `VersionDeAudiencia` (record) | `HabitoId, Audiencia, Instant rigeDesde, UserId cambiadoPor, Instant cambiadoEn` | |
| `HistorialDeAudiencia` | Versiones de un hábito, ordenadas | `vigenteEl(LocalDate dia, ZoneId zona)`: R-2. `ultima()`: la última guardada (para el admin) |
| `TramoDeRol` (record) | `CodigoDeRol rol, Instant rigeDesde` | |
| `HistorialDeRol` | Tramos de una persona | `vigenteEl(dia, zona, CodigoDeRol actual)`: R-2, sin tramos → actual |
| `VigenciaPorDia` (utilidad pura de dominio) | Regla común | "rige en D si su fecha local en `zona` es < D"; la usan los dos historiales |

`Habito` no cambia de forma: la audiencia es otro agregado chico, ligado por `HabitoId`, para no
cargar versiones cada vez que se lee un hábito.

## Puertos

**Salida (habits)**

- `CargarAudienciasPort`: `deHabitos(Collection<HabitoId>) → Map<HabitoId, HistorialDeAudiencia>`
  (una consulta por lote, no N+1).
- `GuardarAudienciaPort`: `agregarVersion(VersionDeAudiencia)`.
- `HistorialDeRolPort`: `de(UserId) → HistorialDeRol`; `anotarCambio(UserId, CodigoDeRol anterior,
  CodigoDeRol nuevo, Instant)`.
- `ContarRegistrosDeHabitoPort`: `tieneRegistros(HabitoId) → boolean` (para eliminar).
- `CatalogoDeRolesPort`: `rolesDelSistema() → List<RolElegible(codigo, nombre)>`; adaptador sobre
  `users.api.CatalogoDeRolesFinder`.

**Entrada (habits)**

- `ConsultarCatalogoAplicableUseCase`: `para(UserId, ProgresoParticipanteHabits, LocalDate) →
  List<Habito>` — único filtro (R-4).
- `ConsultarRolesElegiblesUseCase`: `listar(UserId actor)`.
- Los casos del CRUD admin existentes reciben la audiencia en sus comandos
  (`CrearHabitoCommand.audiencia`, `ActualizarHabitoCommand.audiencia` opcional) y el listado acepta
  un filtro de rol.

**users.api (nuevo)**

- `CatalogoDeRolesFinder`: `roles() → List<RolDelSistema(String codigo, String nombre)>`, desde
  `UserRole.values()` + `UserRole.nombreVisible()`.

## Estados y transiciones

```
Hábito global creado ──► versión 1 (rige desde la creación)
      │ admin cambia roles
      ▼
versión n+1 (rige desde el día local siguiente de cada persona)
      │ admin elimina (solo sin registros) ──► versiones borradas en cascada
      │ admin desactiva ──► sigue con su audiencia; no se genera para nadie
```

Persona: `rol actual` + tramos de `historial_rol_participante`; cada `RolDeUsuarioCambiadoEvent`
agrega un tramo.

## Qué NO cambia

`habitos`, `registros_habito`, `desbloqueos_habito`, `preferencias_horario`,
`dias_semanales_habito`, `renombres_habito`: sin columnas nuevas. La configuración de la persona
se conserva porque nada la borra; si el hábito vuelve a aplicar, se vuelve a usar (FR-008/009).
