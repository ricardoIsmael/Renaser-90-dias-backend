# Tasks: SER al día con la lógica nueva

Estado: **[x]** hecho y probado · **[ ]** pendiente. Decisión **D-311**, migración **V160**. Backend en `038-ser-al-dia` (desde `integracion-formaciones`); app en `038-ser-al-dia` del frontend.

## 1. Mapa, textos y orientación
- [x] T001 Prompt: sección «Su diagnóstico y sus objetivos» (sin Mapa), «Donde esta cada cosa en la app» (Training / Plan / Cursos / Yo), «Lo que tu no escribes: llevala a Plan», eje Negocio, fases y duración sin números fijos.
- [x] T002 `MapaParaProponer` vacío sin Mapa; `consultar_mi_mapa` solo para el Mapa viejo y, sin Mapa, manda al diagnóstico y a Plan.
- [x] T003 `consultar_rocas noventa`: `porConfirmar` (rocks.api y puerto de rag); sin objetivos manda a Plan; sin «Rocas Maestras» en lo que se le dice al modelo.
- [x] T004 Textos fijos: «90 dias» → plazo de la persona (`MapaEnElPrompt`, `ConsultarClaseDeHoy`, `LecturaDeAcademia`, situación, memoria); «Trabajo» → «Negocio» en las tarjetas.
- [x] T005 `proponer_plan_del_dia` comprueba la fecha antes de proponer; rechazos en segunda persona.
- [x] T006 Pruebas: se actualizan `ConsultarMiMapaHerramientaTest`, `PropuestasConObjetivoDelMapaTest`, `PromptSistemaRenasiaTest` (fijaban lo viejo); nuevas en `ConsultarRocasHerramientaTest` y `ProponerPlanDelDiaHerramientaTest`.

## 2. Rituales por texto
- [x] T007 Backend: `PropuestaDeRegistrarConFoto` (mensaje de escribir, sin foto ni «¿Qué sentiste?»), `registro=escribir` en `consultar_habitos_del_dia`, `pideTexto` en la situación, texto de respaldo («desde Training»).
- [x] T008 App: la tarjeta de SER de un ritual dice «Escribir cómo me siento» y abre `RegistroDeRitualConTexto` (panel de SER y hoja del orbe de Hoy), no la cámara.

## 3. Última comida y hábitos sin evidencia
- [x] T009 **V160** idempotente + `UltimaComidaOpcionalIT`.
- [x] T010 Prompt: lo opcional, Despertar, Dormir y Última comida se marcan con `marcar_habito_completado` (propuesta con «Confirmar»); lo subido a mano se hace en Training.

## 4. Cumplir con SER apaga las alarmas
- [x] T011 Backend: `habitoId` y `fecha` en `HabitoEnJuegoResumen` / `HabitoDelDia`; `HabitoCumplido` en `ResultadoHerramienta.Exito`; `ResultadoPropuestaResponse.habitoCumplido`.
- [x] T012 App: `confirmarPropuestaRenasia` emite `habitoCumplido` (alarmas del día, celebración, semáforo, fénix) con el dato de la respuesta.
- [ ] T013 (límite aceptado) Pastilla, Audioterapia y Clase por SER siguen sin avisar; sumar `HabitoCumplido` a sus tres confirmables.

## 5. Día cerrado (D-259)
- [x] T014 `EstadoDeHoy.DIA_CERRADO`; `LoQueLeFaltaHoy`, `SituacionDelTurnoService`, `PropuestaDeRegistrarConFoto`, `PropuestaDeMarcarHabito` y `CompletacionDeHabito` hablan de «día cerrado»; un `PENDIENTE` con la hora pasada sí se registra.

## 6. Categorías por formación
- [x] T015 `CategoriasDeLaFormacionFinder`, puerto/adaptador de `rag`, `CategoriaDelHabito` en el pedido del hábito propio y rotulación real en la agenda.
- [x] T016 Pruebas parametrizadas por formación (`PropuestaDeCrearHabitoPersonalTest`, `ConsultarAgendaHabitosAdapterTest`, `SerEnLasTresFormacionesTest`).
- [ ] T017 Decisión del dueño: separar por formación el material de conocimiento no ligado a lecciones (`metadatos->>'formacion'`, ver spec).

## 7. Privacidad
- [x] T018 Regla en el prompt, memoria sin dinero, descripción de `consultar_mi_diagnostico`.
- [x] T019 Política de privacidad de la app: una línea sobre el diagnóstico y el modelo de IA.

## 8. Cuando se integren 036 y 037 (no se programa aquí)
- [ ] T020 **036:** situación «tu programa empieza mañana» con el plazo de su formación; `proponer_plan_del_dia` exige el Día 1 elegido.
- [ ] T021 **037:** `consultar_habitos_del_dia` con `mide=km, meta, lleva`; SER no pide foto si hay pasos conectados y dice «Registrar» de la fila de Training; el prompt lo explica.

## Verificación
- [x] Compilado y probado a mano con `javac` + el lanzador de JUnit (1261 de 1263 pruebas unitarias de `rag`, la otra de Piper es del entorno; 1260 de `rocks` y `habits.application`).
- [ ] `./mvnw clean verify` (ITs con Testcontainers) y `tsc` + Jest de la app: **encolados** (el emulador está prendido). Log: `verificar-038.log`.
- [ ] E2E con SER en el emulador (Maestro): «marca mi ritual», «marca mi Despertar» (y comprobar que la alarma siguiente no suena), «arma mi día» sin Mapa, «arma mi semana» con diagnóstico sin abrir Plan.
