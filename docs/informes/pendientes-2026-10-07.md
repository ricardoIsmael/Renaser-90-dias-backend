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

1. ~~**E-589:** «Seguir» invisible para `uiautomator`~~ — **resuelto 07/10**: era la herramienta (espera de quietud más larga que el cierre automático de 3,8 s), no la app; con lector de pantalla no se cierra sola.
2. La **pastilla** y la **clase diaria** no disparan la celebración a pantalla completa (solo los hábitos). Falta
   confirmar con el dueño si deben.
3. La celebración de **graduación** necesita un dato del servidor que hoy no llega.
4. E-551 («Voy» a una cita cancelada), E-584 (la foto de las 00:05 puede contar el día de inicio), E-585 (la pestaña
   General dice «acciones»).

## B.2 Prueba del podio semanal en el emulador (2026-10-07, local)

Backend `53e7078c` en local con almacenamiento S3 falso (S3Mock en `127.0.0.1:9000`, nunca AWS; con `noop` solo sale
el texto, G-5). Publicado con `POST /api/v1/admin/ranking-semanal/publicar` como ADMIN: los dos mensajes llegaron en
vivo al grupo «Formación Renaser Global» del aprendiz, firmados «Formación Renaser»; la imagen abre en grande completa;
publicar otra vez responde `YA_ESTABA` sin duplicar; un aprendiz recibe 403 en publicar y en vista previa. Videos en
`~/Imágenes/e2e-2026-10-07/podio/`. **Observado:** en la burbuja del chat la imagen (4:5) se recorta arriba y abajo
(no se ve el encabezado ni la frase final); en grande se ve entera. **Arreglado el mismo día (E-590):** JPEG de ~120 KB y burbuja 4:5.

## C. Del lado del dueño

Datos de los hábitos para SER, inscripción en la ANPD, invitación a Grafana, actualización obligatoria (postergada).
