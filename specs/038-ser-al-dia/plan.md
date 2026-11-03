# Implementation Plan: SER al día con la lógica nueva

**Branch**: `038-ser-al-dia` (backend y app) | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md)

## Summary

SER no gana herramientas de escritura: se corrige **lo que dice y lo que propone** para que coincida con la app de hoy, y se le da el dato que le faltaba para que cumplir un hábito con SER haga lo mismo que cumplirlo en Training. Hexagonal y Modulith: `rag` sigue leyendo `habits` y `rocks` solo por sus `api/` (un finder nuevo y campos aditivos), nada de tablas de otro módulo.

## Technical Context

**Backend**: Java 25 / Spring Boot 4 / Spring Modulith, Flyway **V160** (idempotente). Pruebas: JUnit 5 + Mockito + AssertJ; las IT usan Testcontainers y el reloj de pruebas del proyecto. **La IA real no se llama** (el prompt se renderiza y se lee con herramientas simuladas).
**App**: Expo / React Native / TypeScript, Jest. Sin dependencias nuevas.
**Storage**: ninguno nuevo (V160 solo cambia un dato del catálogo).

## Decisiones de diseño

| Punto | Cambio | Dónde |
|---|---|---|
| 1 Mapa y textos | Prompt reescrito (sección «Su diagnóstico y sus objetivos», «Donde esta cada cosa en la app»); `MapaParaProponer` vacío sin Mapa; `consultar_mi_mapa` solo para el Mapa viejo; `consultar_rocas noventa` con `porConfirmar`; eje «Negocio»; plazo de la persona en lugar de «90 dias»; ventana del plan del día | `renasia-sistema.st`, `TextoDelMapa`, `TextoDeObjetivos`, `ObjetivoDeNoventaDias.porConfirmar` (rocks.api, rag), `TextoDePlanDeRocas`, `ProponerPlanDelDiaHerramienta`, `MapaEnElPrompt` |
| 2 Rituales | `proponer_registrar_con_foto` sobre un ritual deja la tarjeta (`conPregunta = true`) con el mensaje de escribir; el prompt y la lista de hábitos dicen `registro=escribir`; la app abre `RegistroDeRitualConTexto` | `PropuestaDeRegistrarConFoto`, `HabitoDelDia.seRegistraEscribiendo`, `HabitoDeHoy.pideTexto`, `LoQueLeFaltaHoy`; app: `pedidosDeFoto`, `TarjetaFotoDeHabito`, `RenasiaPanel`, `HoyScreen` |
| 3 Última comida | **V160**: `exigencia_evidencia = 'OPCIONAL'` por id; SER la marca con `marcar_habito_completado` | `V160__ultima_comida_es_de_evidencia_opcional.sql`, `UltimaComidaOpcionalIT` |
| 4 Cumplir con SER | `HabitoEnJuegoResumen` y `HabitoDelDia` suman `habitoId` y `fecha`; `ResultadoHerramienta.Exito` suma `cumplido`; `confirmar` devuelve `habitoCumplido`; la app emite `habitoCumplido` | `HabitoCumplido`, `CompletacionDeHabito`, `ResultadoPropuestaResponse`; app: `renasiaApi`, `renasiaSchemas` |
| 5 Día cerrado | `EstadoDeHoy.DIA_CERRADO`; `LoQueLeFaltaHoy` ya no cuenta `EXPIRADO` entre lo que falta; `PropuestaDeRegistrarConFoto` y los mensajes de rechazo lo dicen | `SituacionDelTurnoService`, `HabitosDeHoyEnElPrompt`, `LoQueLeFaltaHoy` |
| 6 Formaciones | `habits.api.CategoriasDeLaFormacionFinder`; puerto y adaptador de `rag`; `CategoriaDelHabito` reemplaza al enum en `HabitoPersonalPedido`; la agenda rotula con el nombre real | `ConsultarCategoriasDeHabitoPort`, `ArgumentosDeHabitoPersonal`, `ConsultarAgendaHabitosAdapter` |
| 7 Privacidad | Regla del prompt; `compactar-memoria.txt` excluye dinero; descripción de `consultar_mi_diagnostico`; línea en `/privacidad/` de la app | `compactar-memoria.txt`, `public/privacidad/index.html` (app) |

## Orden de despliegue

Backend primero (V160, `habitoCumplido`, textos); la app tolera el backend anterior (el campo es opcional). La app sola, contra el backend anterior, sigue funcionando: sin `habitoCumplido` no apaga alarmas (como hoy) y el ritual por SER abre el registro por texto aunque el servidor siga pidiendo foto en su texto.

## Pruebas

Backend (unitarias, sin IA): `PromptSistemaRenasiaTest`, `SerEnLasTresFormacionesTest` (parametrizada: Certificación 90 días, Diplomado 3 meses, Diplomado 1 año), `ConsultarAgendaHabitosAdapterTest` (una por formación), `PropuestaDeCrearHabitoPersonalTest` (una por formación), `ConsultarRocasHerramientaTest`, `ProponerPlanDelDiaHerramientaTest`, `PropuestaDeRegistrarConFotoTest`, `LoQueLeFaltaHoyTest`, `HabitosDeHoyEnElPromptTest`, `SituacionDelTurnoServiceTest`, `MarcarHabitoCompletadoConfirmableTest`, `PropuestasRenasiaControllerTest`; IT: `UltimaComidaOpcionalIT` (V160), `CrearHabitoPersonalPorPropuestaIT`. App (Jest): ver `tasks.md`.
Sin emulador en esta corrida: el E2E con SER (Maestro) queda escrito en `.maestro/ser-al-dia/` de la app.
