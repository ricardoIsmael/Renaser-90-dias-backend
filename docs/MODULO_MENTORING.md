# Módulo `mentoring`: acompañamiento visto por el mentor

Spec 055 (rediseño del rol de mentor, fase 1) agrega a este módulo las **notas privadas** y la lectura de **colores por día**; la
**semana** del aprendiz para el mentor vive en `leadership` (ver abajo por qué). Lo anterior (semáforo del grupo, avisos,
huecos, diagnóstico, evaluación) sigue como está.

## Qué ve el mentor de un aprendiz

| Lectura | Ruta (prefijo `/api/v1/mentor/groups/{g}/learners/{u}`) | Dónde vive |
|---|---|---|
| Semana de sábado a viernes con el pilar de cada hábito | `GET /semana?semanaHasta=` | `leadership`: `SemanaDelAprendizService` / `SemanaDelAprendizController` |
| Color y porcentaje de cada día (máx. 62) | `GET /dias?desde=&hasta=` | `mentoring`: `DiasDelAprendizService`; la regla es de `points` (`DiasDelSemaforoFinder`) |
| Notas privadas | `GET` · `POST /notas`, `PATCH /notas/{id}` | `mentoring`: `NotasDelMentorService`, dominio `NotaDelMentor` |

Todas exigen **acompañar hoy el grupo y que el aprendiz sea de él** (guard del servicio; el permiso `USE_APP` no protege nada, A-1)
y responden 403 `NO_ACOMPANA` si no. `@Funcionalidad("mentor.mi_grupo")`.

**Por qué la semana vive en `leadership`.** Reutiliza `LecturaDeLaFicha` y `ArmadoDeLaFicha` de la ficha del líder, que ya
dependen de `mentoring.api`; si `mentoring` importara `leadership` se cerraría un ciclo que `ArchitectureTest` rechaza. La semana
es la misma que ve el líder (mismos colores, mismas obligaciones), con otra puerta y una ventana que también admite la semana en
curso (`SemanaDelMentor`).

## Notas privadas

- Tablas `notas_del_mentor` y `notas_del_mentor_versiones` (V198). La nota sigue al **aprendiz**, no al grupo.
- Texto de 1 a 2000 caracteres sin contar los espacios de los extremos; se guarda recortado.
- Corrige **solo el autor** y **solo el mismo día** en que la escribió, contado en la zona del autor (`dia_del_autor`); cada
  corrección guarda el texto anterior en las versiones. No hay borrado.
- Leen: el acompañante vigente (todas las del aprendiz), el liderazgo de su formación (`GET /leadership/people/{id}/notas`,
  `VIEW_MENTOR_CORPS`) y Administración (`GET /admin/trainees/{id}/notas`, `MANAGE_TRAINEES`), estos dos con `puedeCorregir=false`.
  El aprendiz no tiene ninguna ruta que las devuelva.
- La baja de la cuenta (D-243) borra las notas sobre la persona y las que escribió.

## Pilar de cada hábito

`ObligacionDelDia.pilar` es la `categoria_clave` del hábito tal cual está en el catálogo (`CUERPO`, `MENTE`, `ESPIRITU`,
`CONSCIENCIA` —«Emociones» en la Escuela— o la clave de otra formación); `null` si el llamador no la conoce. Es aditivo.
