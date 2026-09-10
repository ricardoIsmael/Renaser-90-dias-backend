# Reporte técnico de ADMIN/ALQUIMISTA — revisión del SDD 003

Fecha de revisión: 2026-09-10, America/Lima.
Corte del inventario Git: 12:30:53 -05:00.
Alcance: estado real de ambos proyectos para armar la especificación administrativa, incluidos cambios sin commit. No es certificación de despliegue ni auditoría completa de cada módulo.

La primera versión del SDD contenía reglas superadas y capacidades presentadas con más certeza de la que permitía el código. Esta revisión corrige esas afirmaciones y convierte las diferencias en tareas explícitas.

## 1. Resultado principal

El backend ofrece gran parte del CRUD administrativo, pero todavía no está completo el flujo móvil ADMIN/ALQUIMISTA. Los puntos prioritarios son la integración de asignaciones manuales con historial/chat/cupos, el ciclo de vida del grupo, la lectura semanal administrativa y la paridad del programa personal.

Decisión confirmada por el usuario durante esta revisión: siete días de bienvenida y grupos manuales con nombre y fechas. La rotación automática mensual/semanal y el traslado automático antiguos quedan fuera del comportamiento vigente.

## 2. Repositorios y trabajo local

| Proyecto | Rama | HEAD | Working tree al corte |
|---|---|---|---|
| Frontend | mentor | 121e141 | 10 documentos del SDD003 nuevos, sin cambios funcionales locales |
| Backend | mentor | 0318e21 | 26 archivos modificados y 13 nuevos, incluido el enlace documental del SDD |
| Staging | Ambos | — | Sin cambios staged al corte |

No estamos en master. No se cambió de rama. El backend recibió nuevos archivos durante la inspección: los números y hallazgos representan la foto del corte, y Claude debe releer el diff antes de actuar.

Cambios recientes confirmados:
- Frontend 121e141 documenta el cambio de agrupación; edf77f3/06b6fc4/76de114 mejoran nombres y apertura del chat; 6bcd0b8 pliega rejilla semanal en móvil.
- Backend 7315524 condiciona los schedulers antiguos y agrega V48; 0318e21 agrega PeriodoGrupo y sus pruebas.
- Trabajo local: conexión de período a CRUD/JPA, especialidad en mentor, V49 y circuito de avisos por vencimiento.

## 3. Hallazgos que cambian el spec

| ID | Prioridad | Hallazgo verificado | Consecuencia para implementación |
|---|---|---|---|
| H01 | Alta | El primer SDD pedía 3 días y rotación; el usuario confirmó 7 días y grupos manuales | Reemplazar instrucciones contradictorias; mantener jobs antiguos apagados |
| H02 | Alta | CRUD manual de alumnos actualiza participantes_programa y mentor de célula actualiza celulas; no escribe el historial de acompañamiento por ese camino | Conectar operación manual a asignaciones_celula, cupo, punteros y evento de chat antes de darla por terminada |
| H03 | Alta | Las lecturas de mentor usan asignaciones_celula | Crear un grupo por CRUD no garantiza que aparezca correctamente en mentor, seguimiento o chat |
| H04 | Alta | Hoy llama useProgramaPersonal(esMentor); la lista de roles mentor excluye ADMIN/ALQUIMISTA | Habilitar inicio personal por capacidad, sin cambiar el rol |
| H05 | Alta | phasecontracts permite firma solo TRAINEE y consulta TRAINEE/MENTOR | No afirmar paridad completa del programa de staff; completar contratos propios con pruebas |
| H06 | Alta | Semana mentor exige acompanaVigente y que alumno pertenezca al grupo | Agregar alcance admin reutilizando composición; no relajar el guard del mentor |
| H07 | Media | UserRole.can devuelve true para MENTOR/ADMIN/ALCHEMIST; RequireAdminGuard y guards específicos sí protegen operaciones | No presentar una matriz efectiva de capacidades ya disponible ni hacer endurecimiento global indiscriminado |
| H08 | Media | No se encontró feature administrativa móvil | Implementar entrada y vistas por fases, reutilizando UI existente |
| H09 | Media | Endpoint admin de hábitos devuelve configuración/horarios/cuota | No confundirlo con calendario de cumplimiento; ambas lecturas deben convivir |
| H10 | Media | MentorCandidatoResponse no incluye especialidad y CrearCelulaRequest no incluye tipo/capacidad | Completar lectura y contratos antes de exponer controles |
| H11 | Media | Período y aviso están parcialmente conectados en cambios locales; no se demostró cierre/revocación por fecha completo | Validar vigencia futura, cierre, archivo y acceso al chat en servidor |
| H12 | Media | Política legacy aún usa diaTraslado=4, mientras PeriodoGrupo usa bienvenida de 7 días | No restaurar la política antigua al conectar bienvenida |
| H13 | Media | No existe evidencia de superioridad de ALQUIMISTA en los guards revisados; ambos pueden gestionar roles | Retirar jerarquía inventada y usar mismas vistas con autorización real |
| H14 | Media | Edición de hábito es POST, detalles completos, sin effectiveFrom ni cambio de tipo/título | Ajustar formularios al contrato; retirar promesa de vigencia universal |
| H15 | Media | Algunas búsquedas/filtros deseados no existen en listados; cursos están en Comunidad | Definir extensiones puntuales; mantener la organización actual |
| H16 | Media | Retirada manual recibe grupo en command, pero delega en quitarCelula por actor/alumno | Verificar pertenencia al grupo de origen y actualizar historial coherentemente |

El análisis H02 sigue las llamadas del servicio y sus puertos. Las restricciones EXCLUDE de V45 solo protegen las escrituras en asignaciones_celula: no protegen un cambio que actualice únicamente un puntero de otra tabla.

El aviso por vencimiento fue creciendo durante la revisión: se observaron regla pura, servicio, puerto, evento, scheduler y listener. En la lectura inspeccionada el barrido todavía era una sola transacción con lista completa. No se considera “terminado”; revisar adaptación de consulta, paginación, aislamiento, zona, deduplicación y pruebas sobre la versión final.

## 4. Qué se puede reutilizar

| Área | Reutilización concreta | Límite |
|---|---|---|
| Solicitudes | account-requests y aprobación/rechazo | Aprobación no prueba ingreso a bienvenida |
| Grupos | cohorts/cells, PeriodoGrupo, V48 | Falta coherencia manual temporal completa |
| Acompañamiento | ConjuntoAsignaciones, CupoCelula, eventos y APIs | No copiar schedulers ni habilitarlos |
| Mentor | Perfil y especialidad en trabajo local | Candidatos todavía sin ese campo |
| Persona | admin trainees, hábitos personalizados | Búsqueda/filtros y semana admin por completar |
| Cumplimiento | SeguimientoService, obligaciones históricas y points | Autorización distinta para administración |
| Frontend semanal | RejillaSemanal y useSemanaDelAlumno | Adaptar fuente sin duplicar diseño/cálculo |
| Evidencias | admin evidence, review y void | Respetar filtros y permisos reales |
| Comunicación | Conversaciones, composición, notificaciones, transporte Expo | Push real y permisos tras cierre requieren prueba |
| Programa propio | AuthContext, activación, onboarding, mapa, objetivos y hábitos | Falta entrada para ADMIN/ALQUIMISTA y contratos de fase |
| Más | Staff, roles, tickets, categorías y conocimiento | Sin inventar editor de cursos ni analítica global |

No se justifica crear tablas espejo para grupos, hábitos, seguimiento o dashboard. V48 ya reserva período/especialidad; V49 está ocupada por notificación de vencimiento. Releer migraciones para no colisionar con trabajo concurrente.

## 5. UX propuesta y trazable

Acceso Administración desde Hoy y Yo. La persona puede permanecer en su programa personal sin tarjetas operativas entre sus hábitos. Administración presenta Grupos, Personas, Hábitos generales, Evidencias y Más, además de pendientes reales.

Grupo: período, mentor/especialidad, cupo, aprendices y acceso a chat. Persona: resumen, hábitos/horarios, semana, evidencias y contacto. Los detalles se abren bajo demanda; calendario plegable para móvil. La consulta del mapa/objetivos de otra persona requiere contrato y autorización específicos.

No se fuerza un selector al iniciar ni se añade sexto tab. Cursos permanecen en Comunidad. Un grupo nuevo obtiene identidad/conversación propias; cerrar uno no significa borrar su historia.

## 6. Verificación ejecutada

- Lectura de ambos HEAD y status, staged y unstaged; inspección de código nuevo sin commit.
- Revisión de controllers, métodos HTTP, DTO, servicios de dominio/aplicación, puertos, guards y migraciones.
- Consulta de los grafos locales como orientación; validación contra código real porque no contienen necesariamente el working tree.
- Frontend: npm run typecheck → tsc --noEmit terminó con exit0 y sin errores.
- Backend: no se ejecutó Maven ni Testcontainers en esta auditoría documental. Los directorios de reportes inspeccionados no contenían TEST-*.xml, por lo que no hay evidencia local para afirmar build completo en verde.
- No se probó login real como admin, navegación en dispositivo, entrega de push ni ejecución de migraciones.
- No se modificó código funcional, configuración, datos ni ramas; solo el paquete documental y sus referencias.

## 7. Qué cambió en el paquete documental

Los diez documentos del SDD003 fueron revisados en conjunto:
- Modelo manual/7 días, período y archivo de grupos.
- Eliminación de jerarquía ALQUIMISTA inventada.
- Corrección de endpoints y separación existente/propuesto.
- Tareas de integración temporal y permisos que faltaban.
- Programa staff con contratos de fase como pendiente real.
- Reutilización concreta de calendario, datos y módulos.
- Reglas completas para Claude y comando de pruebas Cloud sin duplicar clean verify.
- 27 tareas y 30 escenarios de validación.

Orden recomendado: coherencia manual del grupo → ciclo de vida/bienvenida → programa propio/capacidades → fichas y seguimiento → catálogo/evidencias/avisos → más opciones y verificación.

## 8. Evidencias fuente

- [Navegación de cinco tabs](/home/ricardo/Documentos/Renaser/Renaser-90-dias-frontend-/src/navigation/RootNavigator.tsx:18)
- [Inicio personal condicionado al mentor](/home/ricardo/Documentos/Renaser/Renaser-90-dias-frontend-/src/screens/HoyScreen.tsx:49)
- [Roles reconocidos como mentor](/home/ricardo/Documentos/Renaser/Renaser-90-dias-frontend-/src/features/mentor/types/mentor.types.ts:19)
- [Decisión reciente registrada](/home/ricardo/Documentos/Renaser/Renaser-90-dias-frontend-/specs/001-mentoria-acompanamiento/decisions.md:81)
- [CRUD manual de grupo y alumnos](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/community/application/services/CelulaService.java:125)
- [Asignación de puntero del participante](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/users/application/services/ParticipacionProgramaService.java:289)
- [Seguimiento y alcance vigente](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/mentoring/application/services/SeguimientoService.java:105)
- [Restricciones de contratos de fase](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/phasecontracts/application/services/ContratoService.java:39)
- [Permisos pendientes de matriz](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/users/api/UserRole.java:123)
- [Bienvenida de siete días](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/community/domain/model/celula/PeriodoGrupo.java:31)
- [Política antigua día4](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/community/domain/model/acompanamiento/PoliticaMentoria.java:32)
- [Hábitos personalizados de aprendiz](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/habits/application/services/HabitosDeAprendizAdminService.java:57)
- [Avisos por vencimiento en desarrollo](/home/ricardo/Documentos/Renaser/Renaser-90-dias-backend/src/main/java/com/renaser/os/community/application/services/AvisosDeVencimientoService.java:54)

## 9. Inventario Git al corte

Rutas relativas a cada repositorio; M = modificado sin stage, ?? = archivo nuevo. Este inventario precede a la creación de este reporte y a la revisión 2 de los documentos.

### Frontend

- ?? specs/003-admin-alquimista/PROMPT_CLAUDE.md
- ?? specs/003-admin-alquimista/README.md
- ?? specs/003-admin-alquimista/clarifications.md
- ?? specs/003-admin-alquimista/constitution.md
- ?? specs/003-admin-alquimista/contracts.md
- ?? specs/003-admin-alquimista/plan.md
- ?? specs/003-admin-alquimista/research.md
- ?? specs/003-admin-alquimista/spec.md
- ?? specs/003-admin-alquimista/tasks.md
- ?? specs/003-admin-alquimista/validation.md

### Backend

-  M src/main/java/com/renaser/os/community/application/ports/in/celula/ActualizarCelulaUseCase.java
-  M src/main/java/com/renaser/os/community/application/ports/in/celula/CrearCelulaUseCase.java
-  M src/main/java/com/renaser/os/community/application/services/CelulaService.java
-  M src/main/java/com/renaser/os/community/domain/model/celula/Celula.java
-  M src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/celula/ActualizarCelulaRequest.java
-  M src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/celula/CelulaAdminController.java
-  M src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/celula/CelulaDetalleResponse.java
-  M src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/celula/CelulaResponse.java
-  M src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/celula/CrearCelulaRequest.java
-  M src/main/java/com/renaser/os/community/infrastructure/adapter/out/persistence/celula/CelulaJpaEntity.java
-  M src/main/java/com/renaser/os/community/infrastructure/adapter/out/persistence/celula/CelulaPersistenceMapper.java
-  M src/main/java/com/renaser/os/notifications/domain/model/notificacion/TipoNotificacion.java
-  M src/main/java/com/renaser/os/notifications/infrastructure/adapter/out/persistence/notificacion/NotificacionPersistenceMapper.java
-  M src/main/java/com/renaser/os/notifications/infrastructure/adapter/out/persistence/notificacion/TipoNotificacionJpa.java
-  M src/main/java/com/renaser/os/notifications/infrastructure/adapter/out/persistence/preferencia/PreferenciaNotificacionPersistenceMapper.java
-  M src/main/java/com/renaser/os/users/application/ports/in/mentorprofile/UpdateMentorProfileUseCase.java
-  M src/main/java/com/renaser/os/users/application/services/MentorProfileService.java
-  M src/main/java/com/renaser/os/users/domain/model/mentorprofile/MentorProfile.java
-  M src/main/java/com/renaser/os/users/infrastructure/adapter/in/rest/mentorprofile/MentorProfileController.java
-  M src/main/java/com/renaser/os/users/infrastructure/adapter/in/rest/mentorprofile/UpdateMentorProfileRequest.java
-  M src/main/java/com/renaser/os/users/infrastructure/adapter/out/persistence/mentorprofile/MentorProfileJpaEntity.java
-  M src/main/java/com/renaser/os/users/infrastructure/adapter/out/persistence/mentorprofile/MentorProfilePersistenceMapper.java
-  M src/test/java/com/renaser/os/community/application/services/CelulaServiceTest.java
-  M src/test/java/com/renaser/os/community/domain/model/celula/CelulaTest.java
-  M src/test/java/com/renaser/os/users/application/services/MentorProfileServiceTest.java
-  M src/test/java/com/renaser/os/users/domain/model/mentorprofile/MentorProfileTest.java
- ?? docs/spec/SDD_ADMIN_ALQUIMISTA.md
- ?? src/main/java/com/renaser/os/community/api/GrupoPorVencerEvent.java
- ?? src/main/java/com/renaser/os/community/application/ports/in/celula/DetectarGruposPorVencerUseCase.java
- ?? src/main/java/com/renaser/os/community/application/ports/out/celula/ConsultarGruposPorVencerPort.java
- ?? src/main/java/com/renaser/os/community/application/services/AvisosDeVencimientoService.java
- ?? src/main/java/com/renaser/os/community/domain/model/celula/ReglasDeVencimientoDeGrupo.java
- ?? src/main/java/com/renaser/os/community/infrastructure/adapter/in/scheduler/AvisarGruposPorVencerScheduler.java
- ?? src/main/java/com/renaser/os/notifications/infrastructure/adapter/in/event/GrupoPorVencerNotificationListener.java
- ?? src/main/java/com/renaser/os/users/domain/model/mentorprofile/EspecialidadMentor.java
- ?? src/main/java/com/renaser/os/users/infrastructure/adapter/out/persistence/mentorprofile/EspecialidadMentorJpa.java
- ?? src/main/resources/db/migration/V49__aviso_de_grupo_por_vencer.sql
- ?? src/test/java/com/renaser/os/community/domain/model/celula/ReglasDeVencimientoDeGrupoTest.java
- ?? src/test/java/com/renaser/os/community/infrastructure/adapter/out/persistence/celula/CelulaPersistenceMapperTest.java
