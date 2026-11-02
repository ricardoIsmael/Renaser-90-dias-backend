# Implementation Plan: Identidad por formación

**Branch**: `035-identidad-por-formacion` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md) | **Contrato**: [contracts/api.md](contracts/api.md) | **Datos**: [data-model.md](data-model.md)

## Summary

La identidad (los tres mensajes, la portada y la imagen) pasa de ser global a **ser de una formación**. Enfoque: **una columna `formacion` en la bitácora que ya guarda cada
cambio** (`cambios_bienvenida`), el mismo punto único de resolución (`EstadoDePieza`) con una regla de herencia explícita, y la formación se toma siempre **del objeto
que se atiende** (la conversación del soporte, el chat del grupo, la conversación cuya foto se pide), nunca del rol.

## Technical Context

Java 25 / Spring Boot / Modulith; PostgreSQL (V159, aditiva); Expo SDK 57 + TypeScript + zod. Pruebas: JUnit + Mockito (dominio y servicios), `IdentidadPorFormacionIT`
(Testcontainers, comparte la base: limpia lo que siembra), `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`,
`RutasCubiertasPorElFiltroTest`, `NingunaPruebaDeclaraSuPropioRelojTest`; en la app, Jest, `tsc --noEmit` y Maestro escrito.

## Constitution Check

| Principio | Cumple | Cómo |
|---|---|---|
| Dependencias al dominio | Sí | La regla de herencia vive en `chat.domain` (`EstadoDePieza`, `ImagenDeLaFormacion`); los puertos nombran la intención; `chat` lee la formación por `users.api` (ya lo hace). |
| No se inventan reglas | Sí | Lo no dicho está en *Supuestos* de la spec. |
| Pruebas en verde | Sí | Unitarias + IT de aislamiento + las de arquitectura; cada ruta nueva con 403 (MENTOR, aprendiz, suspendido). |
| Base con historia | Sí | Append-only; sin tabla nueva; cabecera con el porqué. |
| Documentar en el mismo cambio | Sí | D-308, `docs/MODULO_CHAT.md`, `AGENTS.md` de la app. |

### Complexity Tracking

| Desviación | Por qué | Alternativa descartada |
|---|---|---|
| Una pieza de imagen dentro de la bitácora «de la bienvenida» | Mismo precedente que `CARTA_CAJA` (V82): una imagen del chat que Administración cambia desde la app, con quién y cuándo | Tabla `imagenes_de_formacion` (duplica la bitácora y su historia) |
| Dos lecturas indexadas para portada/imagen de una formación que no es la Escuela | La herencia es en vivo (la Escuela cambia, las otras la siguen) | Copiar la de la Escuela a cada formación al migrar (se desincroniza) |

## Cambios por capa (backend, módulo `chat`)

```text
db/migration/                 V159 (columna, pieza nueva, CHECKs, índice)
domain/model/bienvenida/      PiezaDeBienvenida(+IMAGEN_DE_LA_FORMACION, esImagen), CambioDeBienvenida(+formacion), EstadoDePieza(+deImagen),
                              ImagenDeLaFormacion (reglas de ruta y tipo)
application/ports/out/        CambiosDeBienvenidaPort, TextosOriginales/TextosDeBienvenidaPort, DibujarBienvenidaPort, TarjetaConNombrePort (con Formacion),
                              ImagenDeLaFormacionPort (abrir/revisar/servir)
application/ports/in/         BienvenidaEditable(+formacion, heredada), casos de uso de Bienvenida con Formacion, CambiarImagenDeLaFormacionUseCase
application/services/         BienvenidaParaAdministrar, TextosDeBienvenidaAdminService, PortadaDeBienvenidaAdminService, BienvenidaEnSoporte/EnGrupo,
                              FotosDelChatService, ConversacionService (photoPath), ImagenDeLaFormacionService, MensajesParaMostrar (firma)
infrastructure/               CambiosDeBienvenidaJdbcAdapter, TextosDeBienvenidaYamlAdapter/Vigentes, PortadasDeBienvenida, TarjetasConNombreEnMemoria,
                              ImagenesDeLasFormaciones (nuevo), BienvenidaAdminController, ImagenDeLaFormacionAdminController (nuevo), ConversacionResponse
resources/                    bienvenida/mensajes.yaml (secciones diplomado y certificacion)
```

## App

Selector de formación (`ControlSegmentado`) en `BienvenidaAdminScreen`; «Imagen de la formación» en el detalle de cada formación; `AvatarDeChat` pide la foto del
servidor también en la comunidad y en los grupos (la tarjeta local queda de respaldo); la firma del programa y el rótulo de la sección de Tribu llevan el nombre de
la formación de la conversación. Ver la entrada de `AGENTS.md`.

## Orden de publicación

1. Backend (V159 aditiva; el APK anterior sigue igual). 2. APK nuevo (selector, imagen, firma). Sin APK, Administración edita la bienvenida de la Escuela y los chats
muestran lo de la app, aunque el servidor ya mande `photoPath`: el APK anterior ya lo usa en los grupos (D-212) y lo ignora en la comunidad.
