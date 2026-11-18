# Plan de implementación: Rediseño del rol de líder de mentores

**Rama**: `056-lider-de-mentores` | **Fecha**: 2026-11-18 | **Spec**: [spec.md](spec.md)

## Resumen

Ordenar el recorrido del líder en Mis mentores → mentor → grupo → aprendiz, con la ficha de la 055 para aprendices y mentores. La app hace casi todo: extrae la ficha de la 055 a un componente que lee del mentor o del líder según su origen, y rehace las pantallas del líder. El servidor suma lecturas y acciones chicas sobre lo que ya existe: los días con color y la semana sábado a viernes para el líder, los objetivos de otra persona, el cupo derivado del mentor, el permiso de gestionar grupos acotado por formación, que la observación y el aviso le lleguen al mentor, responder consultas, y el documento semanal guardado con `AlmacenamientoPort`. Detalle de lo que se reutiliza en [research.md](research.md).

## Contexto técnico

**Lenguaje**: Java 25 + Spring Boot (backend); TypeScript + React Native / Expo 57 (app)
**Dependencias**: las del proyecto; ninguna nueva (el HTML del documento con text blocks y `HtmlUtils.htmlEscape`, como `PlantillasEmail`)
**Almacenamiento**: PostgreSQL, una migración Flyway (V199 o la siguiente libre al fusionar): `documentos_semanales` y `descargas_de_documentos`; S3 privado por `AlmacenamientoPort.subir` y `firmarLectura`
**Pruebas**: JUnit 5 + Testcontainers (backend); Jest + react-test-renderer (app); Maestro + mobile-mcp (E2E)
**Plataforma**: Android e iOS; backend en EC2
**Tipo**: móvil + API
**Rendimiento**: el documento se genera en < 10 s y se reutiliza en < 3 s (SC-006); «Mis mentores» en un pedido por lista
**Restricciones**: hexagonal y módulos (`leadership` solo importa `<otro>.api`; `ArchitectureTest`); el tiempo es de cada persona; solo cambios aditivos en respuestas existentes (la app instalada no se rompe); español de Perú con tuteo; sin textos de relleno; semáforo con forma (WCAG 1.4.1)
**Escala**: un líder tiene ~4–10 mentores, ~10 grupos de 10–15 aprendices

## Chequeo de la constitución

| Principio | Cómo se cumple |
|---|---|
| I. Dependencias hacia el dominio | Lo nuevo de `leadership` usa puertos `api` de `points` (días), `rocks` (objetivos), `community` (grupos) y uno nuevo de `chat` (mensaje directo); el documento: dominio `DocumentoSemanal` + servicio + adaptadores. |
| II. No se inventan reglas de negocio | Colores: la regla del semáforo existente. Cupo del mentor, permiso de grupos, quién descarga, quién respondió y que se guarde para siempre: decisiones del dueño del 18/11 (Aclaraciones). |
| III. Nada terminado sin pruebas en verde | Pruebas primero por FR con la marca `spec 056 FR-NNN`; `verificar` en los dos repos, Maestro y logs. |
| IV. La base guarda historia | Los documentos no se borran (para siempre); cada generación o descarga queda en `descargas_de_documentos`. |
| V. El tiempo es del participante | Semana sábado a viernes y días en la zona de cada persona; el documento usa la zona de la persona. |
| VI. Los cinco tabs no se tocan sin autorización | Cambian las pantallas del líder en Yo y «Descargar semana» en la ficha del mentor: autorizado por el dueño el 18/11; se anota en `docs/decisiones/liderazgo-y-administracion.md`. |
| VII. Documentar en el mismo cambio | Spec, decisión del dueño, revertir CL-04 en el javadoc de `UserRole` con su rastro, docs de módulo, bitácora si aparece un error y `trazabilidad.json`. |

Resultado: **pasa**. Una decisión vieja cambia a pedido del dueño (CL-04 → el líder gestiona grupos de sus formaciones); se documenta, no es una excepción.

## Estructura

### Documentación

```text
specs/056-rediseno-del-lider/
├── spec.md · plan.md · research.md · data-model.md · quickstart.md
├── contracts/api.md
└── checklists/requirements.md
```

### Código

**Backend** (`src/main/java/com/renaser/os/…`)

```text
users/api/UserRole.java                         MANAGE_CELLS al líder (revierte CL-04)
community/application/services/
  CelulaService, SumarAprendizAGrupoService,
  SumarMentorAGrupoService, ComposicionDeCelulaService   requireAdmin → admin o líder dentro de su alcance (AlcanceDeFormacionFinder)
community/…/CelulaDashboardResponse               + capacity por grupo
leadership/
  application/services/DiasDeLaPersonaService       días con color (points.api.DiasDelSemaforoFinder), recorte [Día 1, hoy]
  application/services/SemanaDeLaPersonaService     semana sábado a viernes con diaUno (reutiliza la de la 055 por puerto)
  application/services/ObjetivosDeLaPersonaService  rocks.api.RocasDelAprendizFinder
  application/services/ObservacionesDeMentorService + publica ObservacionDelLiderEvent (aviso) y manda el mensaje por chat.api
  domain/model/documento/DocumentoSemanal.java      huella, ruta, tipo, persona, semana
  application/services/DocumentoSemanalService      arma datos → huella → reutiliza o genera → subir → firmarLectura(10 min) → registra
  infrastructure/adapter/out/documento/PlantillaDelDocumento.java   HTML con text blocks, todo escapado
  infrastructure/adapter/in/rest/…                  dias, semana, objetivos, documento (persona y mentor), cupo en padrón y ficha
chat/api/MensajeDirectoPort.java + adaptador en chat (CrearConversacionDirectaUseCase + EnviarMensajeUseCase)
notifications/…/ObservacionDelLiderNotificationListener   push al mentor
support/…/TicketMentorService.responder           el líder de la formación también responde; TicketMentorResponse + respondidoPor {id, nombre}
db/migration/V199__documentos_semanales.sql
```

**App** (`src/features/…`)

```text
mentor/components/FichaDePersona.tsx       cuerpo de AlumnoScreen con origen {mentor, grupoId} | {lider, grupoId|null}; ranuras cabecera, pie y después
mentor/utils/lecturasDeLaFicha.ts          origen → cargarSemana, cargarDias, cargarRadar, cargarHuecos, cargarCierres, cargarDiagnostico, cargarOchentaVeinte, cargarObjetivos, notas
mentor/screens/AlumnoScreen.tsx            envoltorio de FichaDePersona (origen mentor) + «Descargar semana» (FR-047)
lider-mentores/
  screens/MisMentoresScreen.tsx            Por resolver + lista con cupo (reemplaza MisMentoresEnYo/ListaDeMisMentores en el panel)
  screens/FichaDelMentorScreen.tsx         FichaDePersona (origen líder) + sección «Como mentor»; sin programa → línea #202
  screens/FichaDelAprendizDelLider.tsx     FichaDePersona (origen líder) + Avisar a su mentor + Descargar semana (reemplaza PersonaDelLiderScreen en el líder)
  screens/GruposDelLiderScreen.tsx, CrearGrupoScreen.tsx (asistente de 3 pasos), GrupoDelLiderScreen.tsx (rehecho)
  screens/ConsultasDelLiderScreen.tsx      bandeja + Recordar al mentor + Responder yo
  screens/ReporteScreen.tsx                anillo + ranking + Exportar
  components/AnilloDeCupo.tsx (react-native-svg), EstadoConForma.tsx (× ! ✓ ·), BarraDeColores.tsx, FilaPorResolver.tsx, SeccionPlegable.tsx
  hooks/useDescargarSemana.ts              pide el enlace y lo abre (Linking) o lo comparte (expo-sharing)
  api/…                                    rutas nuevas; grupos reutilizan adminApi
eventos/components/FormularioDelEvento.tsx  + audienciaPrevia (opcional); hoja «Programar sesión» desde la ficha del mentor
paneles-de-rol/screens/PantallaDelPanelDeRol.tsx   casos nuevos (grupos, crearGrupo)
```

Administración sigue usando `PersonaDelLiderScreen` donde ya la usa; no cambia en esta spec.

## Orden de entrega

Reparto en paralelo según el grafo (regla 11/12 del arnés; máximo 3 agentes):

1. **Backend A — lecturas** (módulo `leadership`): días, semana, objetivos, cupo derivado en padrón y ficha. Solo lectura, sin migración.
2. **Backend B — acciones** (`users`, `community`, `support`, `chat`, `notifications`): permiso de grupos acotado, responder consultas, observación que avisa y escribe.
3. **Backend C — documento** (`leadership` documento + V199): dominio, plantilla, servicio, REST, registro. Va después de A (usa sus lecturas).
4. **App 1 — ficha compartida**: `FichaDePersona` + `lecturasDeLaFicha` + `AlumnoScreen` como envoltorio (sin cambiar lo que ve el mentor), con sus pruebas.
5. **App 2 — pantallas del líder**: Mis mentores, fichas, grupo, grupos y asistente, consultas, reporte, programar sesión, descargar semana. Arranca con los contratos de [contracts/api.md](contracts/api.md) escritos.
6. E2E en el emulador (de a uno, al final), logs, docs y PR en cada repo (el de la app espera al despliegue del backend).

## Seguimiento de complejidad

Sin violaciones que justificar. Riesgo: `FichaDePersona` toca la ficha del mentor ya en producción; se protege con sus pruebas actuales de la 055 corriendo sin cambios.
