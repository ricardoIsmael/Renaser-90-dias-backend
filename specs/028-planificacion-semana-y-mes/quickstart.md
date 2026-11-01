# Quickstart: validar «Solo esta semana», repetir la semana y planificar el mes

Todo en local, base clonada y build debug; **nunca contra producción**. Los flujos de Maestro están escritos y no se corren aquí (sin emulador).

## Prerrequisitos

- Backend de esta rama con V130 y V131 aplicadas (Flyway las corre al arrancar). Una cuenta de aprendiz con el Día 1 elegido y con hábitos de varias dimensiones.
- App de esta rama (APK de desarrollo o Metro del worktree contra el backend local).

## 1. «Solo esta semana» (historia 2)

1. Training → engranaje → «Tus prácticas» → Cuerpo → Planificar → un hábito (por ejemplo Gimnasio).
2. En «Días» elige **Solo esta semana**, marca el sábado, mueve la hora a las 09:00 y guarda.
3. **Esperado**: esa semana el sábado va a las 09:00 (`GET /habit-preferences/{id}/week?weekStart=<lunes>`: `customDate: true`); la semana siguiente y el plan de siempre siguen a la hora de antes
   (`GET …/weekdays` no cambió). La pastilla del sábado se distingue y la línea dice «Cambiado solo esta semana: sáb».
4. «Volver al plan de siempre» devuelve la fecha; las alarmas del teléfono también.

## 2. Repetir la semana (historia 1 y 4)

1. Con el cambio de arriba hecho, abre «Tus prácticas»: arriba sale **«Repetir tu semana»** con «Tu semana del … en la del …».
2. «Ver qué se repite» lista, por hábito, los días y qué cambia. «Repetir» aplica; sale «Tu semana del … quedó como esta» con **Deshacer**.
3. **Esperado**: la semana siguiente tiene el sábado a las 09:00 (`GET …/week?weekStart=<lunes siguiente>`); repetir otra vez no cambia nada; «Deshacer» la devuelve al plan de siempre y la oferta vuelve.
4. Si la semana siguiente ya tenía un cambio propio, la oferta dice «Reemplaza lo que ya cambiaste» y pide un segundo toque; sin él el servidor responde 409 y no escribe nada.
5. «Ahora no» oculta la oferta mientras la semana modelo no cambie (`renaser.habitos.repetirSemana.descartada.<cuenta>`).

## 3. Alarmas del teléfono (SC-005)

Con «Solo esta semana» aplicado y los avisos prendidos, el sábado de esa semana suena a la hora y con el aviso propios y las demás semanas siguen como siempre; al pasar la fecha propia (o al deshacer) las alarmas
vuelven a las de siempre al abrir la app. Verificable con `dumpsys alarm | grep renaser` en el emulador (no se hizo en esta entrega).

## 4. Planificar el mes en Plan (historia 3)

Plan → «Planificar mi mes» → los tres meses del programa con el actual elegido, el objetivo del mes del eje abierto («Cambiar» abre el formulario de siempre) y las semanas con sus fechas.

## Pruebas automáticas

- Backend: `./mvnw -o test -Dtest='CambioDeFechaTest,PlanDeLaSemanaTest,RepeticionDeSemanaTest,SemanaCalendariaTest,FechaPropiaTest,PlanDeLaSemanaServiceTest,RepeticionDeSemanaServiceTest,PlanDeLaSemanaControllerTest,RepetirSemanaControllerTest,FechaPropiaPersistenceAdapterTest,PlanificacionDeLaSemanaIT,ArchitectureTest'` (con `MAVEN_OPTS=-Xmx1500m`, sin `verify` completo).
- App: `node node_modules/typescript/bin/tsc --noEmit` y Jest de `src/features/training`, `src/features/habits` y `src/features/objetivos`.
- Maestro (escritos): `.maestro/planificacion/{solo-esta-semana,repetir-la-semana,planificar-el-mes}.yaml`.
