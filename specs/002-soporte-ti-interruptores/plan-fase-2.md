# Plan — Fase 2: despliegue progresivo, ver como rol y roles personalizados

**Spec**: [spec.md](spec.md) (US4, US5, US7; FR-018 a FR-021, FR-026, FR-029 a FR-034) · **Fase 1**: [plan.md](plan.md) y
[contracts/funcionalidades.md](contracts/funcionalidades.md) · **Fecha**: 2026-10-13 · **Para retomar**: 2026-10-14.

---

## 0. Dónde quedamos (cierre del 2026-10-13)

### En producción

- D-266 GIMNASIO (hábito con foto + tabla del ranking), E-608 (el servidor exige la foto de los hábitos de evidencia
  obligatoria) y el S3 local para desarrollo. Backend `f2fa28e7`, V98 y V99 aplicadas. La pestaña «Gimnasio» de la app
  está en master pero **falta el APK nuevo**.
- Specs 002 y 003, la suite de Maestro nueva (`.maestro/`, 11 flujos en verde) y la guía de arquitectura.

### Fase 1 en curso (nada subido)

| Pieza | Rama / worktree | Estado al cierre |
|---|---|---|
| A — núcleo backend | `claude/plataforma-nucleo` en `.claude/worktrees/plataforma-nucleo` (backend) | El agente seguía trabajando. Ya había commiteado el rol `IT_SUPPORT` y el módulo `plataforma` con la consola; faltaba lo que su informe diga (anotaciones en los controladores, procesos, notificaciones, verify) |
| C — app | `claude/plataforma-app` en `.claude/worktrees/plataforma-app` (frontend) | **Terminado**: jest 342 suites / 2847 pruebas, tsc 0. Commits `740d822`, `7d6cd6c`, `cfaba50`, `7d89f79`, `44bdb97`, `a46dc9b` |
| B — IA y circuito de SER | (sin crear) | Pendiente. Se lanza cuando A termine (la máquina no aguanta dos builds de Maven a la vez) |
| D — QA | — | Pendiente: integrar A + B + C y probar en el emulador |

Puntos de coordinación que la app ya asume y se le pasaron a A (revisar en su informe que estén):

1. Contraseña mal en `POST /soporte-ti/desbloqueo`: 401 **con** `codigo: "CONTRASENA_INCORRECTA"` (la app trata un 401
   con sesión como sesión vencida).
2. `GET /funcionalidades/mias` incluye las `ia.*` apagadas por el circuito y la versión/ETag cambia cuando el circuito
   abre o cierra.
3. `PUT /soporte-ti/funcionalidades/{clave}` acepta `porRol` con `true` explícitos (claves con el nombre de la API).
4. Auditoría: filas con `id`, `tipo`, `clave`, `actor {id, nombre}`, `antes`/`despues` (`prendidaGlobal`, `porRol`,
   `aviso`), `motivo`, `creadoEn`; página con `contenido`, `pagina` (desde 0) y `total`.

### Orden para retomar

1. Leer el informe del agente A (o, si la sesión se cortó, mirar `git log origin/master..` y el estado de su worktree;
   correr `./mvnw -o clean verify` ahí y leer `Tests run:`).
2. Lanzar el agente B (circuito de SER en `rag`, interruptores `ia.*`, 204/1013/503, `CircuitoDeIaCambiadoEvent`, push).
3. Integrar A + B + C en ramas de integración locales; `clean verify`, jest y tsc.
4. QA local: base clonada, `fotos-local`, APK debug nuevo con `claude/plataforma-app`, cuenta Soporte TI con
   `scripts/soporte-ti/asignar.sql`, **suite completa de Maestro** (`.maestro/correr.sh`) **y mobile-mcp** con los casos
   del contrato §9. Sumar a Maestro los flujos de la consola y del aviso de mantenimiento.
5. Pedir el visto bueno del dueño y subir (backend antes que el APK).
6. Recién después, la fase 2 (este documento).

Notas del entorno: el emulador se apagó para liberar memoria (al relanzarlo, revisar que la app instalada sea la del día;
ver la memoria «emulador vuelve de un snapshot viejo»). El contenedor `renaser-fotos` quedó prendido. Worktrees de hoy por
limpiar cuando ya no sirvan: `gimnasio`, `foto-obligatoria`, `s3-local`, `integracion-gimnasio`, `merge-master`,
`guia-de-aprendiz` (backend) y sus pares del frontend; la base clonada `renaser_guia`.

---

## 1. Alcance de la fase 2

| Historia | Qué entrega |
|---|---|
| **US4 — despliegue progresivo** | Una funcionalidad marcada «en despliegue» llega a una lista de probadores y a un porcentaje estable de personas (5 → 25 → 50 → 100 %). La consola tiene «Prender para todos» y subir o bajar el escalón a mano, con uso y errores a la vista. Decisión del dueño: **las dos formas, a mano; el escalón automático queda fuera** |
| **US5 — ver como rol** | Soporte TI recorre la app como la vería un aprendiz, guía, mentor, líder o admin, **en solo lectura** |
| **US7 — roles personalizados** | Soporte TI crea, renombra, desactiva o borra roles que **heredan de un rol base** y marcan un subconjunto de sus funcionalidades; los asignan ADMIN y ALQUIMISTA como hoy |

Depende de la fase 1 (catálogo, evaluación, auditoría, consola, desbloqueo y reautenticación).

## 2. Decisiones de diseño propuestas

### 2.1 Despliegue progresivo

- **Datos** (migraciones nuevas, cada una justificada):
  - `funcionalidades_despliegue (clave PK, porcentaje smallint 0–100, iniciado_en, actualizado_por, actualizado_en)`;
  - `funcionalidades_probadores (clave, usuario_id, PK (clave, usuario_id))`.
  - El historial de escalones es la auditoría de la fase 1 (tipo `MANUAL`, con antes y después del porcentaje).
- **Asignación estable**: `cubeta = primeros 4 bytes de SHA-256(clave + ":" + usuarioId) mod 100`; la persona está
  dentro si `cubeta < porcentaje` o si es probadora. Subir el porcentaje nunca saca a nadie (la cubeta no cambia);
  bajarlo saca a los de las cubetas altas (es lo esperado). Una función pura en el dominio, con prueba de 1000 personas
  × 10 lecturas sin oscilación (SC-004).
- **Orden de la evaluación** (se suma al §5 del contrato de la fase 1): apagado manual > circuito de la IA > despliegue.
  Una funcionalidad en despliegue que a la persona todavía no le toca **no muestra el aviso de mantenimiento**: es algo
  nuevo que no tiene. Propuesta de contrato: `GET /funcionalidades/mias` suma `noDisponibles: [clave]` (aditivo) y la
  app esconde la entrada en vez de mostrar el aviso; el servidor responde 404 de negocio («Todavía no está disponible
  para tu cuenta») en vez de 503. **A confirmar con el dueño** (pregunta P1).
- **«Prender para todos»**: porcentaje 100 y la funcionalidad deja de estar en despliegue (se borra la fila); crítica →
  contraseña, como en la fase 1.
- **Métricas** (FR-020): el interceptor de la fase 1 ya sabe la clave de cada pedido. Se cuentan, por hora y por clave,
  pedidos y respuestas 5xx separando **dentro / fuera** del despliegue, en una tabla de agregados
  `funcionalidades_metricas_hora (clave, hora, dentro, pedidos, errores)` con escritura en lote cada minuto (no por
  pedido). La consola muestra las últimas 24 h y la comparación con el escalón anterior. Sin etiquetas por persona en
  Prometheus (cardinalidad).
- **Consola**: en el detalle, «Despliegue»: porcentaje actual, botones del escalón siguiente y anterior, «Prender para
  todos», lista de probadores (buscar persona y sumar o quitar) y el gráfico de pedidos/errores.

### 2.2 Ver como rol (solo lectura)

Dos caminos; **recomendado el primero**, más simple y sin riesgo de saltarse permisos:

1. **Cuentas de prueba por rol** (recomendado): un script crea una cuenta sintética por rol
   (`usuarios.es_cuenta_de_prueba = true`, sin correo real, con datos de ejemplo). En la consola, «Ver como Mentor» abre
   una sesión de esa cuenta (`POST /soporte-ti/ver-como {rol}` devuelve un token de sesión nuevo, marcado
   `soloLectura`). Un filtro rechaza todo pedido que escriba (POST/PUT/PATCH/DELETE, y los WebSocket de envío) de una
   sesión `soloLectura` con 403 «Modo solo lectura», salvo `DELETE /soporte-ti/ver-como` para salir. La app guarda la
   sesión de Soporte TI aparte y la restaura al salir. Los servicios no cambian: deciden con el rol real de esa cuenta.
2. Simular el rol sobre la propia cuenta Soporte TI: exige que `UserSummaryFinder` devuelva un rol distinto del real
   según la sesión, y todo guard pasaría a depender de eso. Más piezas que pueden fallar abriendo permisos.

Las cuentas de prueba no cuentan para rankings, padrones, semáforos ni métricas, y no reciben avisos (se filtran por
`es_cuenta_de_prueba`). Entrar y salir de «ver como» queda en la auditoría (tipo nuevo `VER_COMO`). **Pregunta P2.**

### 2.3 Roles personalizados

- **Datos**: `roles_personalizados (id, clave, nombre, descripcion, rol_base rol_usuario, activo, creado_por/en,
  actualizado_por/en)`, `roles_personalizados_funcionalidades (rol_id, clave)` y `usuarios.rol_personalizado_id`
  (nullable, FK). La cuenta **sigue guardando el rol base** en `usuarios.rol` (FR-030): guards, enums espejo, rankings,
  padrones y hábitos por rol no cambian.
- **Evaluación**: con rol personalizado, una funcionalidad está prendida si lo está para el rol base (fase 1) **y** su
  clave (o una madre) está marcada en el rol. Una funcionalidad nueva del catálogo nace sin marcar para los roles
  personalizados. Un interruptor por rol base vale también para sus hijos.
- **Reglas** (FR-029, FR-033, FR-034): solo un subconjunto del rol base; nunca base `IT_SUPPORT`; nombre único y distinto
  de los seis; con personas asignadas no se borra ni cambia de base (se desactiva o se reemplaza en bloque, con motivo);
  hasta 20 activos.
- **Asignación** (FR-031): `PATCH /users/{id}/role` y aprobar solicitud aceptan `rolPersonalizadoId` opcional (aditivo;
  el rol base sale del rol personalizado y se valida como hoy: solo ADMIN asigna algo que hereda de ADMIN). La lista de
  roles para elegir (`GET /admin/habits/roles` y la de Equipo y roles) suma los personalizados activos con su base.
- **API para la app** (FR-032): `/auth/me` y las listas de personas suman `rolPersonalizado {id, nombre}` (aditivo);
  `role` sigue siendo el base.
- **Consola**: «Roles» con la lista, crear/editar (nombre, descripción, base, funcionalidades marcables del árbol del
  catálogo filtrado por la base), desactivar, reemplazar en bloque y borrar.

## 3. Reparto con agentes (fase 2)

| Agente | Repo | Entrega |
|---|---|---|
| A2 — despliegue | backend | tablas, cubeta estable, evaluación, `noDisponibles`, métricas por hora, endpoints de la consola |
| B2 — roles y ver como | backend | roles personalizados (tablas, evaluación, asignación, listas, `/auth/me`) y ver como rol con cuentas de prueba y filtro de solo lectura |
| C2 — app | app | consola: despliegue con métricas, roles personalizados, ver como rol (cambio de sesión y franja «Viendo como Mentor · Salir»); la app esconde lo `noDisponible`; el rol personalizado en Equipo y roles, Solicitudes, chat y muro |
| D2 — QA | ambos | E2E local con Maestro + mobile-mcp: estabilidad del porcentaje, probadores, prender para todos, ver como (escrituras rechazadas), roles personalizados asignados y su efecto, permisos negativos |

A2 y B2 tocan el mismo módulo `plataforma`: se coordinan por contrato (`contracts/fase-2.md`, a escribir antes de lanzar)
y se integran de a uno.

## 4. Preguntas para el dueño (antes de lanzar la fase 2)

- **P1.** Una funcionalidad en despliegue que a la persona todavía no le toca: ¿se esconde (recomendado) o se muestra
  con un aviso «Muy pronto»?
- **P2.** «Ver como rol»: ¿cuentas de prueba por rol (recomendado) o simular el rol sobre tu propia cuenta?
- **P3.** Hábitos por rol (D-264): ¿la audiencia de un hábito puede elegir un rol personalizado, o solo los seis base?
- **P4.** ¿«Ver como» incluye los roles personalizados?
- **P5.** Métricas del despliegue: ¿alcanzan las últimas 24 h, o quieres ver días?
- **P6.** (De la fase 1, sin cerrar) ¿Alquimista y Admin ven la auditoría en solo lectura? ¿Los avisos de cambios
  críticos van solo por push o también al chat de soporte?
