# SDD del rol Líder de Mentores

Fecha: 2026-09-09. Estado: **implementado en parte desde el 2026-10-01 (D-241)**: padrón, ficha, atribución
`respondido_por` (V87), observaciones (V88) y reporte del mes, en el módulo `leadership`.

> **Corregido 2026-10-01 (D-241).** Esta línea decía «Estado: planificación; implementación pendiente.».
> Lo que sigue sin hacer está en D-241 (`docs/MODULOS_A_AVANZAR.md` §8): pantalla para mover el semáforo
> operativo, entrada desde Comunidad, y el Líder como acompañante de la recepción (decisión pendiente).
> Dos datos de abajo cambiaron con la implementación: `TicketMentorRespondidoEvent` SÍ tiene consumidor
> fuera de `support` (`notifications`, E-219) y por eso no se amplió; y la primera migración libre fue V87.

El paquete canónico para backend y frontend está en el repositorio hermano:

[SDD 002 · README](../../../Renaser-90-dias-frontend-/specs/002-lider-de-mentores/README.md)

Ruta local: /home/ricardo/Documentos/Renaser/Renaser-90-dias-frontend-/specs/002-lider-de-mentores/README.md.

Incluye constitución, inventario verificado, requisitos RL-01..RL-30 en notación EARS, decisiones
y propuestas, plan técnico y de experiencia, contratos y permisos, 39 tareas, matriz de validación
y prompt de ejecución. Una sola versión, para que los contratos no diverjan. Si este repositorio
se distribuye sin el frontend, adjuntar también el paquete: este enlace depende del checkout
hermano.

Backend revisado en master, HEAD `3c590ad`. Esta referencia no modifica código, esquema ni lógica.

## Lo que este SDD toca de este repositorio

- `users`: `shared/domain/Permission.java` (cuatro permisos nuevos), `users/api/UserRole.java`
  (la fila `PERMISOS_MENTOR_LEAD`, que cierra el falla-abierto A-1 para ese rol) y
  `MentorProfileService` (separar el semáforo operativo del nivel).
- `support`: columna aditiva `tickets_mentor.respondido_por`, el campo equivalente en el dominio,
  el evento `TicketMentorRespondidoEvent` ampliado y un `support/api/TicketMentorFinder` nuevo.
  Verificado que el evento no tiene consumidores fuera de `support`.
- `community.api` y `users.api`: un método de lectura aditivo cada uno.
- Módulo nuevo `leadership`, que importa solo `api/` ajenas.
- Una migración aditiva a partir de la primera versión libre, con cabecera justificada.

## Lo que NO toca, a propósito

Recepción, células, cupos, rotación, sincronización de chat de grupo, fórmula de cumplimiento y
evaluación mensual del mentor: todo eso es del
[SDD 001](../../../Renaser-90-dias-frontend-/specs/001-mentoria-acompanamiento/README.md), lo
implementa otro agente, y este paquete lo **consume** por API pública sin recalcularlo ni
escribirlo. La tabla completa de fronteras está en el README del 002.

Leer `CLAUDE.MD` y `.claude/rules/` antes de implementar.
