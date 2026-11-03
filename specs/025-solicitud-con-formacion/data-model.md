# Data model: Aceptar una solicitud eligiendo formación, rol y ficha (spec 025)

Reutiliza `solicitudes_cuenta`, `participantes_programa` (V125), `formaciones_personal` (V126) y `ajustes_dia_programa` (V21). **No agrega tablas.**

## Migraciones (V140–V142)

### V140 — `solicitudes_cuenta`: la foto de lo elegido al aceptar
| Columna nueva | Tipo | Notas |
|---|---|---|
| `formaciones_elegidas` | `text[]` NULL | códigos de `formaciones`: una (aprendiz), una o varias (mentor, guía, líder), `{}` (Administración y Alquimista). NULL mientras la solicitud no está aceptada. `CHECK cardinality BETWEEN 0 AND 3` |
| `duracion_elegida_dias` | `smallint` NULL | solo el aprendiz elige duración. `CHECK BETWEEN 1 AND 365` |
| `rol_elegido` | `text` NULL | rol base con el que entró (nombres de la API, p. ej. `TRAINEE_GUIDE`). NULL en lo anterior |
| `con_diagnostico` | `boolean` NULL | lo pedido al aceptar. Solo historial |

- **Quién y cuándo** ya estaban: `revisada_por`, `revisada_en`. No se repiten.
- **Por qué una foto y no la pertenencia viva**: la formación y la duración vivas están en `participantes_programa` / `formaciones_personal` y cambian con un traslado o una corrección;
  «lo que se decidió al aceptar» no debe cambiar con ellas. Desnormalización justificada de un dato histórico (misma idea que `participaciones_anteriores`).
- **Por qué `text[]` y no una tabla N:M**: se lee y escribe entera con la solicitud, nadie la une con nada, y no es la fuente de la pertenencia.
- **Backfill**: las solicitudes ya aprobadas quedan con `formaciones_elegidas = {ESCUELA}` y `con_diagnostico = false` (lo que fueron); `rol_elegido` y `duracion_elegida_dias` en NULL.
- **La marca de la persona NO está aquí**: «con o sin diagnóstico» de la persona vive en el módulo `onboarding` (spec 026).

### V141 — `ajustes_dia_programa`: el cambio de duración
`duracion_anterior smallint NULL`, `duracion_nueva smallint NULL` (ambas o ninguna, `CHECK`). Un cambio de duración de quien ya empezó es una corrección manual de su reloj, con motivo,
y se anota en la misma bitácora append-only que mover el día (quién, cuándo, por qué). En ese ajuste `dia_anterior = dia_nuevo`: el día se conserva.

### V142 — `solicitudes_cuenta`: el rol personalizado en la foto
| Columna nueva | Tipo | Notas |
|---|---|---|
| `rol_personalizado_id` | `uuid` NULL, `REFERENCES roles_personalizados ON DELETE SET NULL` | el rol personalizado con el que entró (D-272); NULL con un rol base. Si el rol se borra, el id se suelta |
| `rol_personalizado_nombre` | `text` NULL, `CHECK` 1 a 60 | cómo se llamaba ese día: una foto, no cambia si lo renombran. `CHECK`: con id hay nombre |

`rol_elegido` sigue siendo el rol BASE. Sin tabla nueva: dos columnas de la fila que ya existe, por la misma razón que las de V140. Las solicitudes anteriores quedan con las dos en NULL.

## Dónde queda cada decisión al aceptar
| Decisión | Aprendiz | Personal (mentor, guía, líder) | Administración / Alquimista |
|---|---|---|---|
| Formación | `participantes_programa.formacion` | `formaciones_personal` (todas las marcadas; la **activa** es la primera por orden) | ninguna (ven las tres) |
| Duración | `participantes_programa.duracion_dias` (la elegida) | la de su programa propio = la más corta que ofrece la formación activa | 90 (Escuela) |
| Rol | `usuarios.rol` (por `UpdateUserRoleUseCase`, como «Equipo y roles») | idem | idem |
| Ficha | `onboarding` (`FichaAlAceptar`, spec 026) | idem | idem |
| Historial | las 4 columnas de V140 y las 2 de V142 | idem | idem |

## Dominio (sin Spring)
- `PedidoDeIngreso(formaciones, duracionDias, conDiagnostico)`: lo que pide Administración (puede venir vacío: una app anterior).
- `EleccionDeIngreso(rol, formaciones, duracionElegidaDias, conDiagnostico, rolPersonalizadoId, rolPersonalizadoNombre)`: la foto. `IngresoPorAceptacion` le pone el rol personalizado con el nombre que tiene al aceptar.
- `ReglasDeIngreso.decidir(rol, pedido, catalogo)` → `Resultado(eleccion, formacionDelPrograma, duracionDelProgramaDias)`:
  aprendiz → una formación (400 si más de una) y duración (la única, o elegida: 400 «Elige la duración»); personal → una o varias; Administración/Alquimista → ninguna;
  formación desactivada → 400; sin formación elegida (app anterior) → Escuela y su duración por defecto, sin comprobar que esté activa (FR-013).
- `AccountRequest.approve(actor, usuario, eleccion, clock)` guarda la foto; `verificarQuePuedeDecidir` falla antes de tocar nada.
- `ParticipacionPrograma.cambiarDuracion(nueva, clock)`: conserva el día, recalcula la fase; 409 si ya terminó, si es la misma o si su día no cabe en la nueva.

## Transición de la aceptación (una sola transacción)
```
approve(solicitud, rol, rolPersonalizadoId?, pedido)
  ├─ solicitud leída CON CANDADO (FOR UPDATE): dos aceptaciones simultáneas se turnan; la segunda recibe 409
  ├─ cuenta INACTIVE → ACTIVE; identidad social vinculada si la hay
  ├─ rol (UpdateUserRoleUseCase, igual que «Equipo y roles»; personalizado → su rol base)
  ├─ IngresoPorAceptacion.formalizar:
  │     ReglasDeIngreso.decidir → participantes_programa (formación + duración, reloj en pausa)
  │     personal → formaciones_personal.reemplazar(formaciones, activa = la primera)
  │     con diagnóstico → FichaAlAceptar.conDiagnostico(persona, quien)   [409 si no hay diagnóstico publicado]
  ├─ solicitud APROBADA + foto de lo elegido
  └─ UsuarioRegistradoEvent  →(después del commit)→ community mete al aprendiz en la recepción de SU formación
```
