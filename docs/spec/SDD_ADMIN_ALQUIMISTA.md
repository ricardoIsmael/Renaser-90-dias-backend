# SDD 003 — ADMIN/ALQUIMISTA · revisión 2

Fecha: 2026-09-10. Estado: plan revisado, implementación pendiente por tareas.
Modelo confirmado por el usuario: siete días de bienvenida y grupos manuales con nombre, período, mentor y aprendices. No reactivar traslado/rotación automáticos antiguos.

## Entrada para Claude

[Prompt autónomo](../../../Renaser-90-dias-frontend-/specs/003-admin-alquimista/PROMPT_CLAUDE.md)
[Reglas completas](../../../Renaser-90-dias-frontend-/specs/003-admin-alquimista/constitution.md)
[Especificación](../../../Renaser-90-dias-frontend-/specs/003-admin-alquimista/spec.md)
[Contratos revisados](../../../Renaser-90-dias-frontend-/specs/003-admin-alquimista/contracts.md)
[Tareas](../../../Renaser-90-dias-frontend-/specs/003-admin-alquimista/tasks.md)
[E2E obligatorio](../../../Renaser-90-dias-frontend-/specs/003-admin-alquimista/e2e.md)
[Reporte técnico local](REPORTE_ADMIN_ALQUIMISTA_2026-09-10.md)

Implementar en ambos repositorios, conservando los cambios locales. Ramas inspeccionadas: mentor en ambos, no master.

## Prioridades backend

1. Unificar asignaciones manuales, historial asignaciones_celula, cupos, punteros y evento de chat. Los EXCLUDE no protegen cambios que solo escriben punteros legacy.
2. Completar vigencia/cierre de grupo y bienvenida inicial sobre V48/V49 y trabajo local.
3. Permitir programa propio de ADMIN/ALQUIMISTA, incluyendo contratos de fase con guard de participación propia.
4. Exponer semana administrativa reutilizando seguimiento y motor de points, sin relajar el permiso del mentor.
5. Consumir hábitos personales de solo lectura, catálogo y evidencias existentes.
6. Mantener misma superficie operativa para ADMIN/ALQUIMISTA; no inventar jerarquía ni serializar UserRole.can como matriz efectiva.
7. Reutilizar avisos y push, con permisos revalidados y sin mensajes de chat automáticos.

Java25/Spring Boot4.1.1/Modulith; dominio puro, controller con un caso de uso y cruce de módulos solo por API pública. Clock y zonas correctas, jobs paginados/aislados; migraciones nuevas solo justificadas. No duplicar tablas ni cálculos.

Validación al implementar: ./scripts/test-cloud.sh con JDK25/Testcontainers Cloud y target libre, verificando Surefire/Failsafe, ArchitectureTest y EndpointAuthorizationDeclarationTest. No commits, push ni deploy por este plan.

> Corrección de versión1: retiradas recepción de tres días, rotación automática mensual/semanal y privilegios ALQUIMISTA superiores no verificados. El programa personal debe comprobarse extremo a extremo: su activación existente no prueba todas las pantallas.

## Ampliación del usuario: implementación E2E

Claude debe implementar y ejecutar E01–E17 y T28–T33 del paquete canónico: app web con Playwright, recorridos nativos esenciales con Maestro, Spring/DB reales, login X-Auth-Token y fixtures aislados. Reutilizar Testcontainers Cloud manteniendo el entorno vivo hasta terminar los runners; test-cloud.sh no mantiene los contenedores al salir.

Agregar las verificaciones de API para invariantes, concurrencia y acceso. El smoke con backend simulado de admin-panel/scripts/prueba-local.mjs no prueba la app Expo. Entregar E2E_RESULTADOS.md con comandos, resultados y evidencia; separar las comprobaciones de push real y los dispositivos no disponibles. No ejecutar sobre datos reales ni desplegar por el test.
