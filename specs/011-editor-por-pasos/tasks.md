# Tasks: Planificar — días solo con íconos, «Otra» en su lugar y pantalla completa (spec 011)

**Spec**: [spec.md](spec.md) · **Rama**: `011-editor-por-pasos` en los dos repos (backend: solo la spec; app: el código).
Rutas con prefijo `app:` (repo `Renaser-90-dias-frontend-`) y `back:` (repo `Renaser-90-dias-backend`).
Sin cambios de backend, endpoints, migraciones ni alarmas. Sin código nativo: no hace falta compilar para probar.

## Fase 0: Diseño y decisión (2026-10-20)

- [x] T001 Revisar la captura del teléfono real del dueño y listar lo que falla (7 problemas).
- [x] T002 Armar el lienzo de diseño con 20 tableros interactivos (A, B, B2–B4, C, C2–C4, C3b–C3d, «Lo de hoy, mejorado» y
  sus variantes, «por pasos» con punto, resumen y lista) en https://claude.ai/artifact/ThAjGxrVucj36hcgKasZzQ.
- [x] T003 Decisión del dueño: quedarse con el editor actual y cambiar solo tres cosas (días solo con íconos, «Otra» en su
  lugar, pantalla completa). Confirmado en consola: «Las 3 cosas».
- [x] T004 Escribir la spec y su checklist en back:`specs/011-editor-por-pasos/` (rama `011-editor-por-pasos`).

## Fase 1: Implementación (agente Sonnet 5.5, xhigh)

- [x] T010 [US1] Días solo con íconos: quitar el texto del aviso («30′», «30′ +1», «a la hora») de la pastilla y bajar la
  banda de 34 a 16 y el chip de 88 a 76, con las siete iguales. La etiqueta accesible conserva la lista completa. En
  app:`src/features/habits/components/FilaDeDiasDelPlan.tsx` y app:`src/features/training/utils/avisoDeLosDias.ts`
  (se borró `corto`, `antelacionEnElChip` y `minutosCortos`). Commit `00dbdfb`.
- [x] T011 [US2] «Otra» en su lugar: fila «− N min antes +» dentro de lo que se desliza, botones de 48, fundido de 160 ms
  (al instante con «reducir movimiento»), desplazamiento a la vista. Lógica pura en
  app:`src/features/training/utils/avisoOtra.ts`. Se borró `RuedaAntelacionPicker.tsx`. Commit `b2df5cc`.
- [x] T012 [US2] Corrección: «Otra» SUMA un aviso nuevo (15 o el siguiente libre) y no cambia los que había; «+» y «−»
  mueven solo ese aviso, de 5 en 5, entre 1 y 60, saltando valores existentes. Commit `f7b6469`.
- [x] T013 [US3] Pantalla completa: `HojaDesdeAbajo` acepta `tamano="completa"` (alto hasta debajo de la barra de estado);
  la usan los tres pasos de Planificar. Relleno final de 12 a 32 para que «Cómo suena» se lea sobre «Guardar». En
  app:`src/components/hojaDesdeAbajo/` y app:`src/features/training/components/PlanificarDimensionModal.tsx`. Commit
  `a130aa9`.
- [x] T014 Anotar el cambio en app:`AGENTS.md` (Integridad del Core, entrada del 2026-10-20). Commit `e87be72`.
- [x] T015 Pruebas: nuevas `avisoOtra.test.ts` (45), `planificarOtraAntelacion.test.ts` (18 + 2) y `tamanoCompleta.test.ts`;
  ajustadas `logicaDeLaHoja`, `filaDeDiasDelPlanAviso`, `avisoDeLosDias`, `planificarAvisoPorDia`,
  `recordatorioDeAccionesPorDia`, `trainingIconosYHojas` y `etiquetaDeAntelacion`.
- [x] T016 Playwright web app:`e2e/admin-alquimista/E19-recordatorios.spec.ts` ajustado a la fila nueva de «Otra» (sin
  correr; compila).
- [x] T018 «Otra» de 5 en 5, entre 5 y 60 (en el E2E, desde 1 «+» daba 6, 11, 16…). Commit `af3a7c7`; jest 3895/3897.
- [x] T017 Verificación: `npx tsc --noEmit` 0 errores; jest 407/409 suites, 3892/3894 tests. Las 2 que fallan
  (`alarmasTipoDespertador`, `escucharSonido`) son del `node_modules` compartido sin el parche de `postinstall`; pasan
  17/17 en una copia con el parche. No son de este cambio.

## Fase 2: Prueba en el emulador (agente Sonnet 5.5, xhigh)

- [x] T020 Entorno local, nunca producción: base clonada `renaser_alarmas` (V112), S3 local, backend de `origin/master`
  compilado aparte en el 8097 (sin tocar la carpeta ni el `target/` del backend del dueño), emulador Pixel_6 con el build
  debug de hoy y Metro desde la rama 011. Aviso a las demás sesiones de que se tomó la máquina.
- [x] T021 [US3] Pantalla completa en el emulador: nada asoma detrás; «Cómo suena» entero sobre «Guardar».
- [x] T022 [US1] Días solo con íconos (sábado sin aviso, domingo 10 min), siete pastillas iguales, etiqueta accesible.
- [x] T023 [US2] «Otra» suma 15 con 30 y «a la hora» marcados; «+», «−», topes; reabrir sin sumar otro; nada encimado.
- [x] T024 Persistencia: reabrir el hábito y comprobar en la base (solo lectura) las antelaciones guardadas.
- [x] T025 Atrás de Android (con y sin días marcados).
- [x] T026 Recordatorio de acciones (Plan / Yo → Alarmas): la fila de días también solo con íconos.
- [x] T027 Modo oscuro.
- [x] T028 Maestro `.maestro/regresion/08-planificar-despertar.yaml`: 1 bien, 0 mal.
  Resultado E2E: casos 1–8 PASAN (el atrás igual que master); base idéntica antes y después. Capturas `e2e011-01..39`.
- [x] T029 Restaurar los datos de prueba de la cuenta.

## Fase 3: Cierre (después del visto bueno del dueño)

- [x] T030 Mostrar las capturas al dueño y recibir su visto bueno («me gustó el cambio… arma el AAB»).
- [ ] T031 PR de la app (rama `011-editor-por-pasos`) y merge a master. La spec del backend va en su propio PR, solo
  documentación.
- [ ] T032 AAB de producción (EAS, en disco y no en `/tmp`), SOLO cuando el dueño lo pida. Sale como
  `~/Descargas/Renaser-1.5.0-<versionCode>.aab`.
- [ ] T033 Liberar la máquina: bajar el backend, Metro y el emulador; borrar el worktree temporal del backend
  (`$S/backend-origin-master`) y avisar a las demás sesiones.
