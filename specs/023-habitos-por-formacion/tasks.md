# Tasks: Hábitos y categorías por formación (spec 023)

Estado al 2026-10-31. `[x]` = hecho y probado.

## Datos
- [x] T001 V134: formación e ícono en `categorias_habito`; formación en `habitos`; llave compuesta; unicidad por formación (título y `clave_sistema`)
- [x] T002 V135: `auditoria_catalogo_habitos` (solo agregar)
- [x] T003 V136: `dia_inicio` y `dia_desbloqueo` hasta 365

## Backend
- [x] T010 Dominio: `CategoriaHabito`, `IconoDeCategoria`, `CodigoDeCategoria`; `Habito` (formación, copiar, 404 de otra formación); `HorarioHabito`/`GuiaHabito.copiarPara`
- [x] T011 Puertos y adaptadores JDBC de categorías y de la auditoría
- [x] T012 `CategoriasDeHabitoAdminService` (crear, renombrar, ícono, ordenar, activar, desactivar, eliminar) y `CategoriasDeLaFormacionService`
- [x] T013 `CatalogoAplicable` por formación; `LoadHabitoPort.porClaveSistema(clave, formacion)` en los seis flujos propios
- [x] T014 `HabitoAdminService` por formación (alta, mover de categoría, lista) y `CopiarHabitoService`
- [x] T015 404 de otra formación en elegir/pausar/quitar, preferencias, día semanal y renombre
- [x] T016 Topes de 90: `DesbloqueoHabitoService`, `HorarioHabito`, `HabitosDeUnDia`; Audioterapia y Pastilla en escala base
- [x] T017 API abierta de categorías (`category`, `categoryKey`), `GET /habit-categories`, `/admin/habit-categories`, `/admin/habits/{id}/copy`
- [x] T018 Pruebas: dominio, servicios, `HabitosPorFormacionIT` (aislamiento, migración, constraints, permisos)
- [x] T019 Documentación: D-300, `MAPA_ENDPOINTS`, `MODULO_HABITS`

## App
- [x] T030 Capa de datos de categorías, Training, Hoy y «Mi programa» en otra formación
- [x] T031 Administración: formación en «Hábitos del programa», «Categorías» y «Copiar a otra formación»
- [x] T032 Pruebas Jest, Maestro (`.maestro/habitos-por-formacion/`) y `AGENTS.md`

## Pendiente (fuera de esta spec)
- E2E en emulador (lo corre el agente del E2E); `verify` completo y Jest completo se corren con el candado al cierre.
