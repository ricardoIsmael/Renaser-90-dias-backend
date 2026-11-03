# Research: Hábitos de todos (spec 029)

## 1. ¿De dónde salen las cifras?
- **Decisión**: de `registros_habito` (la misma fuente de la ficha de cada persona), en una consulta con `IN (personas)` y `fecha_ejecucion = :dia`. Un dato derivable se
  deriva en un solo lugar (FR-014): no se guarda ninguna copia ni se suma ficha por ficha.
- **Descartado**: reusar `ObligacionesHistoricasFinder` (no trae el hábito, su categoría ni su formación) y ampliarlo (sus constructores los usan muchas pruebas): un contrato
  nuevo y chico es más claro.

## 2. «Pendiente» o «sin cumplir» sin depender del reloj del servidor
- El propio registro lo dice: `PENDIENTE`/`EN_CURSO` = el día de esa persona no cerró (hoy: todavía puede); `FALLIDO`/`EXPIRADO` = cerró sin cumplirse. El barrido de expiración
  cierra cada día en la zona de cada persona (regla 02), así que con el reloj en la madrugada UTC —todavía el día anterior en Lima— lo no hecho sigue «pendiente».
- `opcional` sin cumplir es «sin datos» (la ficha no lo cuenta como falta). Quien no tiene registro (pausa, no le tocaba, antes de su Día 1, cuenta suspendida) no está
  en lo esperado.

## 3. «Hoy» y la ventana
- La ventana es hoy y 7 días atrás; «hoy» es el de la zona por defecto del padrón (Lima). Un alumno en otra zona puede estar un día adelante o atrás por unas horas: su registro
  es de su fecha local y se ve en el día que le corresponde. Documentado; no hay alumnos fuera de Lima hoy.

## 4. Formaciones
- Los alumnos salen de `PersonasDelPrograma.alumnos(alcance, ahora)`, que filtra con `AlcanceDeFormacionFinder.dentroDelAlcance` (spec 022); los registros, con
  `FiltroDeFormacionSql` sobre `habitos.formacion` (spec 023). Pedir un hábito por id que nadie tiene en el alcance es 404, igual que uno inexistente. El filtro «formación» solo
  puede acotar el alcance de quien pide, nunca ampliarlo.

## 5. Costos
- Lista: tres lecturas en lote (padrón + cuentas de prueba, grupos operativos, registros del día). Detalle: además, por cada fila de la página, la participación (zona y Día 1),
  el horario resuelto del día (`HorarioDelDiaFinder`) y una lectura de evidencias para las filas cumplidas. Con página de 30 son ~60 lecturas chicas, solo al abrir un hábito.
- Límite aceptado: el grupo de cada alumno no usa su grupo principal (sería una lectura por persona); quien está en dos grupos cae en el de menor categoría.

## 6. Entrada en la app
- Yo → «Mis mentores» (`ListaDeMisMentores`) gana la fila «Hábitos de todos», solo si el servidor la ofrece (un 200 de la primera lectura; 403/404 la esconden), igual que
  «Todos los aprendices».
