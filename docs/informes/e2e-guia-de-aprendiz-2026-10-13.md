# Informe E2E — Guía de aprendiz, rol al aprobar, E-605 y E-606 (D-265)

Fecha: 2026-10-13. Pedido del dueño: «cuando llega una solicitud el sistema debe mostrar todos los roles… los guía de
aprendiz es lo mismo para el tema de mentor… luego arregla esos 2 bugs». **Nunca contra producción** (regla del dueño):
todo local.

## Entorno

| Pieza | Valor |
|---|---|
| Backend | worktree `claude/guia-de-aprendiz`, puerto 8092, variables de la configuración de IntelliJ del dueño en cada arranque; V97 aplicada al arrancar |
| Base | `renaser_guia`, clon de `renaser_habitos_rol` (contenedor `renaser-db`, 5433), con tres solicitudes sembradas: E2E Guía Nueva, E2E Aprendiz Nuevo, E2E Admin Nuevo |
| App | APK debug ya instalado (mismo nativo; el cambio es solo JavaScript) con Metro del worktree `claude/guia-de-aprendiz` del frontend y `EXPO_PUBLIC_API_URL=http://10.0.2.2:8092` |
| Dispositivo | emulador `Pixel_6`, manejado con `mobile-mcp` |
| Cuentas | `~/.config/renaser-e2e/habitos-por-rol.json` y `guia-de-aprendiz.json` (fuera del repo) |

Capturas: `~/Imágenes/e2e-2026-10-13/guia-de-aprendiz/`.

## Resultado por caso

| Caso | Resultado | Evidencia |
|---|---|---|
| G1 Solicitudes muestran los seis roles | OK | Aprendiz, Guía de aprendiz, Mentor, Líder de mentores, Administrador, Alquimista; Aprendiz marcado; chips con rol radio y estado. `G1-solicitudes-admin-elegido.png` |
| G2 Aprobar como Aprendiz | OK | Un toque, sin confirmación; base: `APRENDIZ`, `ACTIVO`, solicitud `APROBADA` |
| G3 Aprobar como Guía de aprendiz | OK | Confirmación «¿Aprobar a E2E Guía Nueva como Guía de aprendiz?»; aviso «Cuenta aprobada: E2E Guía Nueva entró como Guía de aprendiz.»; base: `GUIA_APRENDIZ`, `ACTIVO`, perfil de mentor, fila de programa, sin grupo de bienvenida; historial de hábitos `TRAINEE → TRAINEE_GUIDE`. `G3-*.png` |
| G4 Aprobar como Administrador | OK tras E-607 | Base: `ADMIN`, `ACTIVO`, sin perfil de mentor. En la primera corrida el oyente de rol del chat chocó (E-607); con el arreglo, aprobar como Alquimista dejó 0 publicaciones incompletas y la persona en los 72 soportes. `G4-confirmar-administrador-y-atras-E606.png` |
| E-606 Atrás cierra el diálogo | OK | Con «¿Aprobar … como Administrador?» abierto, el atrás de Android lo cerró, la pantalla siguió en Solicitudes y la solicitud siguió `PENDIENTE` en la base; lo mismo en «¿Hacer Guía de aprendiz a E2E APRENDIZ2?» de Equipo y roles (el rol no cambió) |
| G5 Equipo y roles | OK | Cabecera «El rol se elige al aprobar…», aviso del grupo inicial distinto del rol, «Mentores y guías (6)» con «E2E Guía Nueva · Guía de aprendiz · Sin grupo asignado». `G5-*.png` |
| E-605 Aprendices sin el personal | OK | «APRENDICES (93 en total)» empieza por aprendices reales; la API con `role=TRAINEE` trae 93 (todos `APRENDIZ`, igual que la base), sin filtro 111 (las seis etiquetas), con un rol inventado 400 |
| G6 Cambiar a Guía desde Equipo y roles | OK | APRENDIZ2 → Guía de aprendiz con confirmación; base `GUIA_APRENDIZ` con perfil de mentor e historial; sale de «Aprendices». `G6-*.png` |
| G7 Hábitos por rol | OK | `GET /admin/habits/roles` lista «Guía de aprendiz» segundo; la pantalla tiene el filtro; los tres hábitos «solo Mentor» de la base no llegan al guía; un hábito marcado para los cinco roles de ayer no lo incluye (D-264). `G7-habitos-filtro-guia.png` |
| G8 Guía como mentor de un grupo | OK | Las dos guías aparecen en «Elige un mentor»; asignada a «Grupo destino E2E» (función `MENTOR`), con un aprendiz. `G8-*.png` |
| G9 La guía entra | OK tras el sello | Entra sin onboarding (programa opcional del staff); Hoy muestra «Mi grupo · Grupo destino E2E · 1 aprendiz»; ve semáforo, aprendices, evaluación y la ficha del aprendiz con sus hábitos. El sello decía «MENTOR»: ahora «GUÍA DE APRENDIZ». `G9-*.png` |
| G10 Marca en el chat | OK | En su grupo, `MENTOR` (la app decide «Ver ficha» con esa marca); en el global y en el 1 a 1 con su aprendiz (creado solo, como con un mentor), `GUIA_APRENDIZ` → «Guía de aprendiz» |
| G11 App anterior | OK | `POST …/approve` sin cuerpo → `APRENDIZ`, `ACTIVO` |

## Fallos encontrados y corregidos

- **E-607** Aprobar como ADMIN/ALCHEMIST dejaba un oyente del chat incompleto (`participantes_conversacion_pkey`).
- **Botón «Aprobar como Líder de mentores»** en tres renglones: la tarjeta cambiaba de alto con cada chip. El botón dice siempre «Aprobar».
- **Más opciones** describía «Equipo y roles» con cinco roles; suma «guía de aprendiz».
- **Sello de «Mi grupo»** fijo en «MENTOR»; a la guía le dice «GUÍA DE APRENDIZ».

## Observaciones fuera de alcance

1. El padrón del Líder de mentores muestra a los guías como mentores, sin rótulo propio.
2. Una app 1.5.0 muestra la marca cruda «Guia_aprendiz» en la info de un chat que no es el grupo del guía.
3. Los 403 de `/me/emergency-request` y `/rocks/today` para la guía sin programa son los mismos que recibe un mentor sin programa.
