# Pendientes — al 2026-10-07

Lista de trabajo, no informe.

**Estado:** backend `53e7078c` desplegado (podio semanal D-262 **apagado**, V95). Web `456db7c`. APK preview 99
(fénix que escucha, piensa y habla; Día 1 del personal D-260/D-261; animación del hábito; celebraciones a pantalla
completa; fases de Yo iguales a Plan; registro solo del día D-259). Pruebas de punta a punta en local con Maestro: en
verde. AAB de la Play Store: sigue el 1.5.0 (versionCode 97), sin nada de esto.

---

## A. Decide el dueño

1. **Tablas y columnas sin uso** (detalle en [`tablas-sin-uso-2026-10-07.md`](tablas-sin-uso-2026-10-07.md)):
   - ¿Borrar `permisos`, `rol_permiso`, `historial_coherencia` (con su caso de uso muerto) y las tres columnas vacías?
   - Funciones con backend y sin pantalla: acceso a cursos por grupo (`grupos`, `miembros_grupo`,
     `asignaciones_curso`), auditoría de cambio de rol, testimonios, video del día 90. ¿Se terminan o se borran?
2. **Podio semanal (D-262):** ver `GET /api/v1/admin/ranking-semanal/vista-previa` y decidir si se prende
   `RANKING_SEMANAL_ACTIVO`. Confirmar la regla de iniciales con nombres de 3 y 4 palabras (`NombreCorto`).
3. **Bandeja de tickets del líder en Hoy:** D-156 dice que se retiró; confirmar si vuelve.
4. **AAB para la Play Store** con todo lo del APK 99.

## B. Arreglos conocidos (sin empezar)

1. **E-589:** el botón «Seguir» de la celebración a pantalla completa no lo ve `uiautomator` ni, probablemente, el
   lector de pantalla.
2. La **pastilla** y la **clase diaria** no disparan la celebración a pantalla completa (solo los hábitos). Falta
   confirmar con el dueño si deben.
3. La celebración de **graduación** necesita un dato del servidor que hoy no llega.
4. E-551 («Voy» a una cita cancelada), E-584 (la foto de las 00:05 puede contar el día de inicio), E-585 (la pestaña
   General dice «acciones»).

## C. Del lado del dueño

Datos de los hábitos para SER, inscripción en la ANPD, invitación a Grafana, actualización obligatoria (postergada).
