# Quickstart: comprobar el Código Renaser con la voz

## Backend (worktree propio, nunca en el checkout del IDE)

```bash
cd .claude/worktrees/impl-030
./mvnw -q -DargLine=-Xmx1500m test -Dtest='RegistroRadarTest,RadarServiceTest,RadarControllerTest*,RadarDePersonaServiceTest,CumplimientoDelLiderControllerTest,DiarioYRadarServiceTest,AcompanamientoDeAlumnoServiceTest,ArchitectureTest'
./mvnw -q -DargLine=-Xmx1500m verify -Dit.test='RegistroRadarPersistenceAdapterTest'   # Postgres real con Testcontainers
```

Esperado: un registro con `dictado: true` se guarda, vuelve en `GET /radar/history`, en la lectura del mentor y en la del líder; sin el
campo vale `false`; el segundo intento de la hora devuelve el primero con su `dictado`.

## App

```bash
node node_modules/typescript/bin/tsc --noEmit
node node_modules/jest/bin/jest.js src/features/radar src/features/renasia src/features/mentor/components src/features/alarmas
```

Esperado: cero errores de tipos; el dictado suma sin borrar y sin pasar de 2 000; un micrófono a la vez; el fallo del micrófono deja
lo escrito y lo dictado; «Escuchar» se detiene antes de abrir el micrófono; la ficha dice «Dictado por voz…» solo en los registros
dictados; `sonidosElegibles` en verde con los cuatro MP3.

## En el teléfono (a mano, con APK nuevo y backend nuevo)

1. Aprendiz en los días 1 a 7: abrir el Código Renaser, tocar «Escuchar» en «¿Qué siento?» sin conexión y oír la pregunta.
2. Tocar «Dictar», hablar, ver el texto en el campo, corregir una palabra, completar el resto y «Registrar».
3. En la ficha del mentor (y la del líder y la de Administración) el registro dice «Dictado por voz…».
4. Sin permiso del micrófono: una línea lo dice y se puede escribir. Maestro: `.maestro/codigo-renaser/41-dictar-y-escuchar.yaml`.
