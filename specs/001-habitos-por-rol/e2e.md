# Informe E2E — Hábitos globales por rol (spec 001)

Fecha: 2026-10-12. Quién: el agente, a pedido del dueño («las pruebas e2e con todos los casos antes de subir…
no quiero errores en producción»). **Nunca contra producción** (regla del dueño): todo local.

## Entorno

| Pieza | Valor |
|---|---|
| Backend | worktree `claude/habitos-por-rol`, puerto 8092 (admin 8093), variables de entorno leídas de la configuración de IntelliJ del dueño en cada arranque |
| Base | `renaser_habitos_rol`, clon de `renaser` en el contenedor `renaser-db` (puerto 5433) |
| App | worktree `claude/habitos-por-rol` del frontend, APK debug `com.renaser.app` compilado en el worktree, Metro en 8081 con `EXPO_PUBLIC_API_URL=http://10.0.2.2:8092` |
| Dispositivo | emulador `Pixel_6` (Android), manejado con `mobile-mcp` |
| Cuentas | `~/.config/renaser-e2e/habitos-por-rol.json` (fuera del repo): ADMIN, ALQUIMISTA, MENTOR (con programa, día 15), LÍDER, APRENDIZ1, APRENDIZ2 y un ADMIN suspendido |

«Al día siguiente» se simuló moviendo en la base clonada la fecha de la versión de audiencia (o del tramo de rol)
a ayer, y regenerando los registros pendientes del aprendiz cuando hacía falta. Es lo mismo que ve el servidor
pasado el cambio de día: la regla compara la fecha guardada con la fecha del día en la zona de la persona.

Capturas: `~/Imágenes/e2e-2026-10-12/habitos-por-rol/`.

## Resultado por caso

| Caso | Resultado | Evidencia |
|---|---|---|
| T039 Entorno aislado | OK | Base clonada, backend 8092, app del worktree; cuentas sembradas |
| T040 Quién entra | OK | ADMIN y ALQUIMISTA ven «Hábitos del programa» (alquimista crea → 201). MENTOR, LÍDER y APRENDIZ: 403 en `GET/POST /admin/habits` y `GET /roles`; el aprendiz no tiene tarjeta de Administración. ADMIN suspendido: 401 con el mensaje genérico. 403 forzado (se le quitó el rol con la sesión abierta): «Tu cuenta no tiene permiso para administrar los hábitos del programa.» y sin «Nuevo». `T040-403-sin-permiso.png` |
| T041 Lista | OK | Activos/Inactivos, filtro por cada rol (incluye «Para todos»), rótulos de audiencia, «Rige desde mañana». `T041-lista-claro.png` |
| T042 Crear | OK tras E-601 y E-602 | Nombre vacío → aviso, sube al campo y le da el foco; interruptor apagado sin roles → aviso; «solo Mentor» aparece en el filtro Mentor y no en Aprendiz, sin «Rige desde mañana». `T042-formulario-solo-mentor.png` |
| T043 Editar | OK | Cambios sin tocar roles no crean versión; cambiar roles → «Rige desde mañana»; los cinco roles se guardan como cinco. `T043-lista-rige-manana.png` |
| T044 Activar, desactivar, eliminar | OK | Desactivar/activar (botones apagados con ruedita mientras guarda: `conf-*.png`); eliminar sin uso; eliminar con registros → diálogo con «Desactivar» y queda inactivo. `T044-eliminar-con-registros.png`. La línea de confirmación dura 4 s y no alcanzó a salir en captura: se comprobó con un log temporal, ya quitado |
| T045 Aviso y navegación | OK | Aviso de reglas propias en Clase diaria, Pastilla y Agua tibia (todos los que tienen `systemKey`). Salir con cambios → «¿Salir sin guardar?»; «Salir» no guarda (Pastilla siguió `OPTIONAL`). Atrás: formulario → lista → Más opciones. `T045-aviso-reglas-propias.png` |
| T046 Accesibilidad y tamaños | OK | Oscuro a 360 dp, claro, tablet de 800 dp (centrado a 560), letra al 130 % sin desbordes, «quitar animaciones» (las casillas aparecen y se van al instante y vuelve la selección), lector de pantalla: interruptor, casillas con estado y chips como radio. `T046-*.png` |
| T047 Restringir a Mentor | OK | Restringido hoy, el aprendiz lo conserva hoy y lo completa (+10 pts). «Al día siguiente»: APRENDIZ1 sin JUGO VERDE ni Clase diaria en `/habits`, registros (14), Training y Planificar Cuerpo (6); APRENDIZ2 (control) con sus 16; el mentor los recibe. `T047-aprendiz-cuerpo-sin-jugo-verde.png` |
| T048 Conteos | OK, parcial | `habitosHoy`: 14 para APRENDIZ1 y 16 para APRENDIZ2; los puntos solo suben con lo completado. Coherencia, semáforo y «¡Día completo!» salen de esos mismos registros; no se cerró una semana ni se completaron los 14 para verlos en pantalla |
| T049 Mentor y administración | OK | Con APRENDIZ1 en un grupo del MENTOR: la vista del mentor, la ficha de administración y el `/habits` del aprendiz dan los mismos 18 (17 + su personal), sin JUGO VERDE ni Clase diaria |
| T050 Clase diaria restringida | OK | No aparece en Training → Mente (`T050-mente-sin-clase-diaria.png`); su única entrada es esa tarjeta. Llamar a mano `POST /classroom/clase-diaria` da 404 «No hay Clase Diaria generada para hoy», sin marcar la lección ni dar puntos |
| T051 Volver a «Para todos» | OK | Agua tibia con hora, recordatorio, cambio de hora pendiente y nombre propio → restringida: desaparece del catálogo, de horarios y de la ficha, y las filas siguen en la base → de vuelta: todo igual que antes. La pausa no se pudo probar (los desbloqueos están apagados en esta base: «no está en tu plan»); la racha sale de los registros, que nadie borra |
| T052 Cambio de rol | OK | APRENDIZ2 → Mentor: el historial anota `TRAINEE` desde siempre y `MENTOR` desde el cambio; hoy sigue con lo de aprendiz; «mañana» suma JUGO VERDE, Clase diaria y el hábito E2E y conserva sus 16 registros. Vuelta a Aprendiz desde «Equipo y roles» en la app: tramo `TRAINEE` nuevo. `T052-equipo-y-roles-aprendiz2.png` |
| T053 Hábitos personales | OK | Un hábito personal nuevo aparece en `/habits`, en la ficha (`isPersonal`) y en Training; no está en el catálogo global, la API del catálogo da 404 y no tiene audiencias |
| T054 App anterior | OK, con límite | El código de `master` (la app sin esta función, sigue diciendo 1.5.0) en el mismo APK debug contra este backend: Hoy (1/14, 110 pts) y Training cargan sin errores y muestran menos hábitos. Ningún cliente del repo llama a `/admin/habits` fuera de la pantalla nueva. No se probó el binario 1.5.0 de la tienda: no hay etiqueta para reconstruirlo y nunca se prueba contra producción. `T054-*.png` |
| T055 Informe | Este archivo | |

## Fallos encontrados y corregidos

- **E-600** Metro no resolvía `./index` desde un worktree (blockList sin anclar).
- **E-601** «Rige desde mañana» en un hábito recién creado: ahora lo decide el servidor (`targetRolesStartTomorrow`).
- **E-602** Formulario: aviso que no se iba, nombre fuera de la vista y chips que cambiaban de ancho.

## Observaciones fuera de esta funcionalidad (no se tocaron)

1. **E-605 · «Equipo y roles» rotula «Aprendiz» a todo el que viene de `GET /admin/trainees`**, también al personal con
   programa propio (E2E ALQUIMISTA, E2E ADMIN y E2E MENTOR aparecían en «Aprendices · hoy Aprendiz»). El rol real
   de la cuenta no cambia y el filtro de hábitos lee el rol real, pero quien administra puede confundirse.
2. **E-606 · El atrás de Android no cierra los diálogos de `dialogo.confirmar`.**
3. **Un cambio de rol no siempre llega a los oyentes en desarrollo**: con DevTools, el oyente de `habits` falló con
   `InconsistentClassPathException` (E-162, anotado como recurrencia) y Modulith lo reentregó al reiniciar. En
   producción no hay DevTools.
4. **Varios inicios de sesión seguidos agotan el límite de 10 por correo y hora** (E-598, anotado como recurrencia): se liberaron
   solo los contadores `reset-password:rl:login:*` de las cuentas de prueba en el Redis local.
5. **E-604 · El MENTOR sembrado a mano no tenía `perfiles_mentor`** y no se le podía asignar un grupo; el cambio de rol por
   la app sí lo crea. Es del sembrado, no del producto.
6. **E-603 · Si el emulador se cae, arranca desde un snapshot viejo** y la app instalada vuelve a una build anterior (sin
   Rive): la app se caía con SIGSEGV o «View config not found for component `RiveReactNativeView`». Se reinstaló
   el APK del día; todo lo anterior a la caída se probó con el APK correcto.

## Estado de la base clonada al terminar

JUGO VERDE y Clase diaria solo para Mentor (con fecha de ayer); DÍA SIN CELULAR solo para Mentor desde hoy; Agua
tibia de vuelta a «Para todos» con la configuración propia de APRENDIZ1; «E2E Revisión del grupo» para los cinco
roles desde hoy; un hábito personal «E2E Leer 20 páginas» de APRENDIZ1; grupo «E2E Hábitos por rol» con el
MENTOR y APRENDIZ1; APRENDIZ2 de nuevo Aprendiz (tramo de Mentor con fecha de ayer). Nada de esto sale del clon.
