# Tasks: Identidad por formación (spec 035)

Todo implementado el 2026-11-02. `[x]` = hecho y probado (unitarias corridas; las `*IT` y el `verify` completo quedan para la corrida con Docker, ver el informe).

## Backend (módulo `chat`)

- [x] T001 V159: columna `cambios_bienvenida.formacion` (FK a `formaciones`, `DEFAULT 'ESCUELA'`), pieza `IMAGEN_DE_LA_FORMACION`, CHECKs de ruta y de forma, carta de la caja solo de la Escuela, índice `(formacion, pieza, id DESC)`. Idempotente, con su cabecera.
- [x] T002 Dominio: `CambioDeBienvenida` con formación; `EstadoDePieza.deImagen` (la herencia de portada e imagen); `ImagenDeLaFormacion` (ruta y tipo); `PiezaDeBienvenida.esImagen`.
- [x] T003 Puertos con `Formacion`: `CambiosDeBienvenidaPort`, `TextosOriginales/TextosDeBienvenidaPort`, `DibujarBienvenidaPort`, `TarjetaConNombrePort`; puerto nuevo `ImagenDeLaFormacionPort`.
- [x] T004 `bienvenida/mensajes.yaml` con las secciones `diplomado:` y `certificacion:` (la clave que falta usa la de la Escuela) y su adaptador.
- [x] T005 La formación sale del objeto atendido: `BienvenidaEnSoporteService` (conversación del soporte), `BienvenidaEnGrupoService` (chat del grupo), `FotosDelChatService` (conversación cuya foto se pide).
- [x] T006 Administración: `?formation=` en las rutas de `/admin/bienvenida`; `heredada` en la portada; `BienvenidaParaAdministrar` por formación.
- [x] T007 Imagen: `ImagenDeLaFormacionService` (consulta), `ImagenDeLaFormacionAdminService`, `ImagenesDeLasFormaciones` (JPEG cuadrado de 512), `ImagenDeLaFormacionAdminController`.
- [x] T008 Chat: `photoPath` en la comunidad general y en los grupos sin foto propia (+ `ownPhoto`); `GET …/foto` sirve la imagen de la formación.
- [x] T009 Firma del programa por formación (`NombreDelChat.delPrograma`) en el listado de mensajes y en los avisos.
- [x] T010 Pruebas unitarias (dominio, servicios, adaptadores, controlador) y `IdentidadPorFormacionIT` (aislamiento, 403 en cada ruta, V159). Las cinco de arquitectura.
- [x] T011 Documentación: D-308, `docs/MODULO_CHAT.md`, contrato y modelo de datos de la spec.

## App

- [x] T020 `bienvenidaApi` con formación (la Escuela sin query), esquema con `formation` y `portada.heredada`, `bienvenidaEsDe`.
- [x] T021 Bienvenida: `ControlDeFormacion`, `useBienvenidaAdmin(formacion)`, `PortadaDeBienvenida` y `MensajeDeBienvenida` por formación.
- [x] T022 «Imagen de la formación» en el detalle de cada formación (API, utilidades, componente con vista previa y volver).
- [x] T023 Chat: avatar de la comunidad con la imagen del servidor, `grupoTieneFotoPropia`, firma del programa, rótulo de Tribu y de «Compartir».
- [x] T024 Jest (lo puro y el cableado), Maestro `.maestro/identidad-por-formacion/` (escrito, sin correr) y entrada de `AGENTS.md`.

## Pendiente de la corrida con Docker y el emulador

- `./mvnw -B clean verify` (corre las `*IT`, entre ellas `IdentidadPorFormacionIT`).
- `tsc --noEmit` y Jest completo de la app.
- Los cuatro flujos de Maestro en el emulador, con el backend con V159.
