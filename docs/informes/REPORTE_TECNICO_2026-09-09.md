# Reporte técnico diario · Renaser
**Fecha y corte:** 9 de septiembre de 2026, 17:35, America/Lima.
**Alcance:** frontend, backend, ramas activas, commits del día, código sin commit y worktrees relacionados. Se excluye la documentación de planificación del balance de avance.

Informe basado en Git, lectura de código y reportes de pruebas disponibles. Se ejecutó TypeScript del frontend principal y comprobación de whitespace de diffs. No se modificó código de aplicación, no se ejecutó migración, no se hizo merge/commit/push ni se consultó producción. Los repositorios tienen trabajo concurrente: esta es una fotografía al corte indicado.

## 1. Estado real de ramas

| Repositorio | Checkout actual | HEAD | Commits de hoy alcanzables desde HEAD | Relación local con master y origin/master |
|---|---|---|---:|---|
| Frontend | mentor | f076afd | 14 | Mismo commit; 0 adelante / 0 atrás |
| Backend | mentor | e2150ae | 2 | Mismo commit; 0 adelante / 0 atrás |

En ambos repositorios, la rama local lider-de-mentores también apunta al mismo HEAD que mentor/master. Las ramas mentor y lider-de-mentores no tienen upstream configurado. Se compararon referencias locales; no se hizo fetch, por lo que esta igualdad no acredita el estado remoto en tiempo real.

Los cambios sin commit descritos abajo están físicamente en el checkout mentor. Aunque parte del código trate al líder de mentores, eso no significa que ya esté separado en la rama lider-de-mentores. Cambiar nombres de rama no versiona ni aísla esos archivos.

Antes de guardar este informe: no había cambios staged en los dos checkouts principales.

## 2. Frontend · Avance comprometido del día

### 2.1 Diseño compartido y accesibilidad
El commit 07920fe realizó un pase de componentes y pantallas: tokens sensibles al tema, separación de dorado de texto y superficie, cortes reales de Jost, sustitución de iconografía y ajuste de zonas táctiles. Abarcó 69 archivos según Git.

También se ajustaron márgenes responsive y composición en Hoy, Plan, Training, Comunidad y Yo. Se conserva la estructura de cinco tabs. La entrada animada utiliza Animated y contempla reducción de movimiento.

Los commits 4c2fe8c y afff258 amplían etiquetas/roles accesibles en login y onboarding, contraste de selectores y tamaños pulsables. El documento de avance anterior registra mediciones y comprobaciones visuales web; este reporte confirma los cambios versionados, pero no vuelve a certificar todas esas mediciones en dispositivo.

### 2.2 Modales e interacción
750cafb añadió VeloModal a los selectores de fecha, hora, país y ubicación, para cerrar al tocar fuera. Esto mejora una salida concreta de los selectores; no demuestra que todos los modales ya resuelvan el gesto nativo de iOS. Los formularios con contenido o firmas no se cerraron indiscriminadamente.

### 2.3 Plan y evidencias propias
6092091 sustituyó datos de relleno en Plan/Yo por datos reales o estados honestos:
- Evidencias propias contra GET /api/v1/evidence, con schema y hook específicos.
- Estados de revisión distinguidos de “verificada”.
- Avance del programa en Plan vinculado al día real.
- Logros presentados como catálogo cuando no existe una fuente real de desbloqueo.

Referencia: [hook de evidencias propias](/home/ricardo/Documentos/Renaser/Renaser-90-dias-frontend-/src/features/evidence/hooks/useMisEvidencias.ts).

### 2.4 Mapa de Renacimiento y Hoy
3d63b46 incorporó lectura del estado de finalización del Mapa y confirmación al backend; 36b3b7e documentó la relación con autoguardado. El objetivo del cambio es conservar la finalización entre sesiones/dispositivos y evitar que el recorrido vuelva a crear objetivos/hábitos al repetirse.

f346007 incorporó la etapa del Mapa al avance del onboarding y ajustó la presentación del mensaje del Muro en Hoy. bbfc87c corrigió una referencia de hook renombrado en YoScreen.

El recorrido completo con escritura en servidor no se reprodujo durante este reporte.

### 2.5 Rol mentor: UI real e integración básica
e278101 construyó el módulo src/features/mentor:
- MiCelulaScreen y AlumnoScreen.
- Entrada de acompañamiento desde Hoy.
- Fila de alumno, tarjeta de mentor y estados de carga/error.
- Tipos, schemas, reglas y hooks.

442079d concentró la lectura compartida de la célula. f076afd conectó la información mediante tres endpoints existentes: cohorts → cells → detalle de célula.

**Qué obtiene ahora:** identidad de célula/cohorte, próxima sesión, enlace de videollamada e integrantes básicos.
**Qué no obtiene todavía en este checkout:** día del programa, última actividad, hábitos de la semana y pendientes de evidencias por alumno. El mapper asigna null a esos campos.

La pantalla de mentor existe y está conectada al listado básico. No debe presentarse como calendario completo ni evaluación real de alumnos.

Observaciones técnicas:
- Selecciona el primer cohort y la primera cell: falta selección/contexto explícito para escenarios con varias asignaciones.
- Las reglas evitan porcentaje 0 cuando no hay datos, pero el grupo “al día” se forma con quienes no tienen motivos detectados. Datos desconocidos pueden acabar contados en ese grupo: falta un estado independiente.
- El resumen actual es de hábitos agregados, no la evaluación mensual de evidencias que se está construyendo en backend.

Referencia: [mentorApi](/home/ricardo/Documentos/Renaser/Renaser-90-dias-frontend-/src/features/mentor/api/mentorApi.ts), [reglas de presentación](/home/ricardo/Documentos/Renaser/Renaser-90-dias-frontend-/src/features/mentor/reglas.ts).

### 2.6 Historial de commits del día
| Commit | Avance |
|---|---|
| 07920fe | Tokens, tipografía, contraste, iconos y pantallas |
| 750cafb | Cierre de cuatro selectores |
| 6092091 | Datos reales de Plan/Yo y evidencias |
| 4c2fe8c | Accesibilidad de login/onboarding |
| 3d63b46 | Persistencia de finalización del Mapa |
| 36b3b7e | Explicación del cierre del Mapa y autoguardado |
| 5ca8f49 | Herramientas de diseño y configuración de pruebas versionadas |
| 29a03b6 | Registro documental del avance visual |
| afff258 | Contraste y controles de onboarding |
| f346007 | Etapa Mapa y contenido del Muro |
| bbfc87c | Corrección del hook en YoScreen |
| e278101 | Construcción de pantallas de mentor |
| 442079d | Lectura compartida de célula |
| f076afd | Célula e integrantes desde endpoints reales |

Los commits exclusivamente documentales no se cuentan como nuevas funcionalidades.

## 3. Frontend principal · Sin commit
Al corte no había archivos de código tracked modificados ni nuevos archivos bajo src. Había mockups y documentación sin seguimiento; se excluyen del avance funcional solicitado.

**Verificación ejecutada ahora:** ./node_modules/.bin/tsc --noEmit finalizó con exit 0 y sin diagnósticos. Es chequeo de tipos; no equivale a una prueba funcional, build de tienda o validación de UI nativa.

No se ejecutó export web ni pruebas de dispositivo en este reporte.

## 4. Backend · Avance comprometido del día

### 4.1 Redis y consumo de códigos · 3c590ad
Se reemplazó la secuencia separada de leer/verificar/borrar códigos por un script Lua que compara y consume de forma atómica. Integra conteo de intentos y TTL. Esto aborda que dos solicitudes concurrentes validaran el mismo código correcto.

Se añadieron ajustes de configuración/documentación de Redis y un serializador JSON de sesiones. Git registra 12 archivos, +219/-47 para este commit.

### 4.2 Metadata de Spring Session · e2150ae
Se amplió la lista explícita de tipos permitidos por el serializador para Long, Integer, Boolean, String, Instant y Duration. Spring Session almacena metadata junto con el contexto de seguridad; una whitelist limitada a Spring Security rechazaba esa metadata al leerla.

El cambio incluye prueba del mapa con creationTime, lastAccessedTime y maxInactiveInterval, además del SecurityContext. Git registra 2 archivos, +82/-5.

El mensaje del commit declara una suite de 2628 tests sin fallos. Se informa como declaración histórica del commit, no como verificación del código sin commit actual.

Referencia: [RedisSessionConfig](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/shared/infrastructure/session/RedisSessionConfig.java).

## 5. Backend principal · Trabajo sin commit
Al corte: **10 archivos tracked modificados (+507/-42) y 25 archivos nuevos bajo src sin seguimiento**. El diff stat de Git no incluye el contenido de los nuevos archivos. Una de las 10 modificaciones es la bitácora; las demás afectan código, configuración o pruebas.

### 5.1 Dominio de acompañamiento
Hay 10 archivos nuevos en community/domain/model/acompanamiento:
AsignacionCelula, AsignacionId, AsignacionInvalidaException, CoberturaCelula, ConjuntoAsignaciones, CupoCelula, FuncionAcompanamiento, MotivoAsignacion, PeriodoAsignacion y TipoCelula.

Implementan modelo temporal, funciones de acompañamiento, cupos de 10–15, recepción sin cupo, cobertura y comprobaciones de solapamiento. Hay cuatro clases nuevas de pruebas para asignaciones, conjuntos, cupos e intervalos.

**Estado:** base de dominio escrita. En el checkout principal no se encontraron todavía servicios/adaptadores/endpoints que conecten estas nuevas clases para ejecutar automáticamente todo el ciclo de recepción, traslado y rotación.

### 5.2 Cálculo de cumplimiento
Se añadieron cinco tipos en points.api y CalculoCumplimiento en dominio:
- Ventanas temporales y obligaciones de evidencia.
- Deduplicación por obligación.
- Entregadas/esperadas por aprendiz.
- Promedio de porcentajes individuales.
- Entregas fuera de ventana y verificación separada.
- Estados sin historial/sin muestra y versión de fórmula.

**Estado:** motor puro y test escritos; no se encontró aún consumo desde un endpoint de evaluación/ranking en el checkout principal. No confundir tener la fórmula con tener un KPI visible en la app.

Referencia: [motor de cálculo](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/points/domain/model/cumplimiento/CalculoCumplimiento.java).

### 5.3 Migración nueva V45
Existe V45__acompanamiento_mentoria.sql sin commit. Contiene:
- Extensión btree_gist y enums de acompañamiento.
- Nuevas columnas tipo/capacidad en celulas.
- Tres tablas: politicas_mentoria, asignaciones_celula y anomalias_acompanamiento.
- Restricciones de intervalos para evitar solapamiento.
- Backfill desde asignaciones actuales, iniciando historia desde migración.
- Registro de contradicciones entre punteros sin corregirlas silenciosamente.

**Estado:** SQL escrito; este reporte no acredita que haya sido aplicado ni probado contra una base real. Hay que revisar el conflicto de versión con el worktree de grupos, explicado más abajo.

### 5.4 Permisos del líder de mentores
Los cambios de Permission/UserRole incorporan:
- VIEW_MENTOR_CORPS.
- FOLLOW_UP_MENTOR.
- VIEW_MENTOR_REPORT.
- SET_MENTOR_OPERATIONAL_STATUS.
- Matriz específica para MENTOR_LEAD.

PermissionEnforcementInterceptor incorpora evaluación de ese rol en **modo sombra por defecto**. Con mentor-lead-enforcement=false registra lo que denegaría, pero continúa la petición. Al activar la propiedad aplica la denegación.

**No es todavía enforcement activo por defecto.** MENTOR, ADMIN y ALCHEMIST siguen fuera de la matriz restrictiva en esa capa; la protección depende de guards de servicio existentes. No se afirma que todas sus operaciones estén desprotegidas, pero la deuda de centralización permanece.

### 5.5 Estado operativo de mentor
Se añade un caso de uso, DTO, implementación y ruta:
PATCH /api/v1/users/{mentorId}/mentor-profile/operational-status.

Permite al líder autorizado y a ADMIN/ALCHEMIST cambiar el semáforo operativo del mentor. El nivel N0–N3 sigue bajo los permisos de administración existentes. La implementación incluye guard explícito y una nueva clase de pruebas de servicio.

**Estado:** cableado de controller/caso de uso presente, sin commit; no se probó mediante petición real durante este informe.

### 5.6 Programa personal del staff: bloqueo abierto
La bitácora registra E-169: activar seguimiento personal crea participación, pero diez guards de rocks/habits/academy todavía exigen rol TRAINEE para operar partes del programa.

Impacto: un mentor/líder puede inscribirse y después recibir 403 en objetivos, servicios de hábitos o academia. Añadir FOLLOW_OWN_PROGRAM a la matriz no corrige por sí solo esos guards.

**Estado:** problema documentado, no corregido en los cambios del checkout principal revisados.

## 6. Trabajo existente en otras ramas/worktrees

### 6.1 Frontend · claude/seccion-mentor-guia-aprendiz
HEAD 04f8193; worktree limpio. Tiene **4 commits propios no integrados** en el checkout principal, y le faltan los 14 commits de hoy del principal.

Avances:
- UI “Grupo” y separación de mentor/guía.
- Feature grupos con acompañantes, miembros, barra de rotación y hook de grupo.
- Miembros/chat con datos reales, eliminación de relleno e iniciales.
- Normalización del rol GUIDE enviado por servidor.
- Consumo de datos reales del guía.

Commits: b643bac, c235362, 3b94885 y 04f8193.

**Estado:** código comprometido en esa rama, no integrado en mentor/master. Ambos lados modifican Comunidad y otras piezas: la integración debe preservar el pase visual y las conexiones reales.

### 6.2 Backend · claude/seccion-mentor-guia-aprendiz
HEAD 42427ff; worktree limpio. Tiene **5 commits propios no integrados** y no contiene los dos commits Redis del principal.

Avances identificados:
- Autenticación exigida para asignar mentor y tracking de staff.
- Nuevo rol global GUIDE y sus mapeos.
- Capacidad de grupos, grupo inicial y guía asociado.
- Autoasignación desde bienvenida cuando díaPrograma > 3.
- Rotación cíclica de mentores conservando grupos.
- Exclusión de bienvenida de la rotación.
- Eventos de cambio de miembro y alta/baja en chat.
- Migraciones V45 a V48.

Commits: 2ab3f74, 2c15e31, 45985b4, ce67f14 y 42427ff.

**Límites concretos de esta implementación:**
- La rotación usa tramos fijos de 30 días desde inicio de cohorte (1–30, 31–60, 61–90), no cadencia semanal configurable.
- El scheduler de rotación corre diariamente a las 05:20 UTC.
- El método que barre cohortes tiene una transacción global; requiere revisar aislamiento por grupo/fallo.
- La rotación manual borra y reemplaza registros del tramo: no conserva por sí sola todos los intervalos reales dentro de ese tramo.
- Sin cupo, la autoasignación mantiene al alumno en bienvenida y escribe aviso en log; no se observó allí una notificación en app/push que complete ese aviso.
- Existe sincronización de miembros por eventos; este informe no certifica revocación inmediata bajo eventos atrasados ni concurrencia de cupos.

Que esta rama tenga orquestación no permite afirmar que ya esté disponible en el checkout principal o desplegada.

### 6.3 Frontend · codex/horarios-habitos-por-fecha
Worktree separado en Renaser-90-dias-frontend-horarios-fecha. HEAD 2df3e49, ancestro del principal, que tiene 36 commits posteriores.

Tiene **6 archivos tracked modificados (+99/-132)**:
package.json, package-lock.json, tsconfig.json, habitsApi.ts, usePlanHabitos.ts y PlanScreen.tsx; además, un test nuevo de hábitos y documentación auxiliar.

Trabajo local:
- GET de preferencias con date y verificación de fecha retornada.
- PATCH de horario asociado a fecha seleccionada.
- Protección contra respuestas atrasadas al cambiar de día.
- Bloqueo de edición si las preferencias no son confiables.
- Fecha capturada al abrir selector y protección de guardado repetido.
- Harness con react-test-renderer y test:habits.

**Estado:** sin commit en ese worktree; no integrado ni validado nuevamente por este reporte. Requiere adaptarse al PlanScreen actual, no copiar íntegramente una pantalla 36 commits atrás.

## 7. Integración: problemas a resolver
| Tema | Evidencia | Implicación |
|---|---|---|
| Versión Flyway duplicada | Principal tiene V45__acompanamiento_mentoria.sql; worktree tiene V45__rol_guia_aprendiz.sql | No integrar ambas con la misma versión. Comprobar primero cuál fue aplicada en cada entorno; nunca renombrar una ya aplicada sin estrategia de migración. |
| Modelos de acompañamiento diferentes | Principal: funciones temporales y políticas por cohorte; worktree: GUIDE global, guía de célula, configuración y rotación por tramos | Resolver compatibilidad y reutilizar implementación existente; no mantener dos sistemas que gobiernen el mismo grupo. |
| Historial insuficiente para evaluación exacta | Rotación manual reemplaza tramo; motor nuevo necesita intervalos reales | Unificar fuente temporal antes de exponer calificación mensual. |
| Trabajo de mentor y líder en mismo checkout | Cambios locales de ambos temas bajo mentor; lider-de-mentores apunta al mismo commit | No asumir aislamiento por nombre de rama. Preparar integración con cambios preservados. |
| Frontends divergentes | Comunidad cambió en el principal y en rama de grupos | Resolver diferencias sin perder diseño/accesibilidad ni datos reales. |
| Pruebas no equivalentes al estado actual | XML de fechas distintas y fuente modificada después de fallo | Repetir validación tras integrar; no sumar XML como si fueran una suite actual completa. |

## 8. Verificaciones y resultados

### Ejecutadas para este reporte
- Frontend principal: TypeScript --noEmit, **exit 0**, sin diagnósticos.
- Git diff --check en los dos checkouts principales: **exit 0**. Solo comprueba formato del diff tracked, no lógica ni todos los archivos untracked.
- Lectura de HEAD/ramas, logs, diffs, archivos nuevos y estado de worktrees.

### Evidencia de backend encontrada en disco, no ejecutada por este reporte
- Cuatro suites nuevas de dominio de acompañamiento: **28 tests, 0 fallos**, reportes de las 17:20.
- CalculoCumplimientoTest: **11 tests, 1 fallo** en el último XML encontrado de las 17:23:19:
  promedioDePorcentajesNoDeTotales esperaba 75 y recibió 71.4286.
- La fuente CalculoCumplimiento.java fue modificada a las 17:23:47, después del XML, y al leerla ya promedia porcentajes. **El fallo acredita una ejecución anterior; falta confirmar la corrección con un reporte nuevo.**
- RedisSessionConfigTest: 2 tests sin fallos en reporte de las 16:57.
- ArchitectureTest: 8 tests sin fallos; EndpointAuthorizationDeclarationTest: 4 sin fallos, alrededor de las 16:59, anteriores a parte del código nuevo.
- Los reportes de UserRole/Interceptor encontrados también preceden los cambios recientes; no acreditan su validación actual.
- Failsafe contiene 11 XML con 25 pruebas sin fallos, pero fechados el **8 de septiembre**. No prueban la nueva migración ni cambios del día.
- El directorio Surefire mezcla ejecuciones antiguas y nuevas. Sus totales acumulados no deben presentarse como clean verify actual.

No se lanzó clean verify porque el encargo es un reporte y existe trabajo concurrente sobre el mismo checkout/target. La validación de implementación deberá seguir el procedimiento Testcontainers Cloud del repositorio y ejecutarse en un estado estable.

## 9. Balance funcional al cierre
| Funcionalidad | Situación real |
|---|---|
| Diseño/accessibilidad del frontend | Cambios comprometidos; tipos pasan; validación nativa no repetida |
| Evidencias personales y finalización del Mapa | Integración comprometida; flujo completo no reproducido aquí |
| Lista básica del grupo del mentor | Conectada en frontend principal |
| Calendario y KPI reales por alumno | No conectados en frontend principal |
| Redis/sesiones y códigos de un solo uso | Correcciones comprometidas |
| Historial temporal y motor de cumplimiento | Código nuevo local, sin conexión completa |
| Permisos del líder y semáforo operativo | Implementación local, enforcement en sombra y pruebas actuales pendientes |
| Bienvenida/autoasignación/rotación/chat de grupo | Implementados parcialmente en rama separada; integración pendiente |
| Programa completo para mentor/staff | Bloqueado parcialmente por guards de rol documentados |
| Avisos de acompañamiento con push nativo | No acreditados como flujo completo en lo revisado |
| Horarios de hábitos por fecha | Cambios locales en otro worktree, sin integrar |
| Despliegue de estos avances | No comprobado |

## 10. Próximos pasos técnicos
1. Consolidar las líneas de grupos/mentor preservando cambios locales; resolver versión de migración y modelo de guía antes de mezclar código.
2. Conectar dominio temporal y cálculo a consultas reales autorizadas.
3. Resolver los guards que impiden al staff operar su programa personal.
4. Integrar seguimiento semanal/evidencias en las pantallas existentes.
5. Completar rotación configurable, cobertura de soporte y avisos reales, reutilizando la orquestación ya escrita donde corresponda.
6. Reejecutar pruebas de cumplimiento, permisos, migración y suite Cloud sobre una revisión estable; verificar frontend en Android/iOS y push real.

Este informe describe avance comprobable y límites de validación. No modifica ni da por terminadas las implementaciones en curso.

