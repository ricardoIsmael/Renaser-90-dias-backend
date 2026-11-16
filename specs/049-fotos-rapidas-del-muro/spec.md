# Spec 049 — Fotos rápidas en el Muro

**Input**: pedido del dueño (2026-11-10): «en el Muro se demora 1 s, que sea más rápido, tipo las redes sociales». Investigado
cómo lo hacen (una versión chica para las listas, adelantar lo que viene, difuminado y CDN) y elegido por el dueño: **la
miniatura la arma una Lambda en AWS**, sin aviso al backend.

## Decisiones del dueño (2026-11-10)

| # | Tema | Decisión |
|---|---|---|
| D1 | Dónde se arma | Una Lambda (`renaser-miniaturas-muro`) que dispara S3 al llegar una foto a `muro/fotos/`. Como Facebook: vale para cualquier app y puede rehacer las fotos viejas. |
| D2 | Aviso al backend | Ninguno: la miniatura tiene nombre fijo (`muro/miniaturas/<autor>/<id>.webp`) y el backend la firma por convención. Si todavía no existe, la app muestra la completa. |
| D3 | Difuminado (blurhash) | Queda para después; mientras carga, el color del tema. |

## Lo que hay hoy (leído el 2026-11-10)

La fila del Muro dibuja una miniatura de 66 px bajando la foto completa (1440 px, cientos de KB); la caché por `cacheKey` estable
ya hace que una foto vista vuelva al instante; nada se adelanta.

## Requisitos

- **FR-001** El feed MUST traer, por cada foto de `muro/fotos/`, `thumbnailUrl` firmada (`muro/miniaturas/<autor>/<id>.webp`),
  y la fila del Muro MUST dibujar esa miniatura; la publicación abierta sigue con la completa.
- **FR-002** Si la miniatura todavía no existe (o la foto es vieja, un video o una evidencia de otro módulo), la app MUST mostrar
  la foto completa, como antes.
- **FR-003** Al llegar una página del Muro, la app MUST empezar a bajar las primeras fotos (hasta 8) por el mismo camino de caché.
- **FR-004** La Lambda MUST armar una WebP de 480 px de ancho (sin agrandar, con la orientación EXIF y sin metadatos) y MUST
  borrar la miniatura cuando se borra la foto.

## Criterios de éxito

- **SC-001** La miniatura pesa al menos 5 veces menos que la foto completa.
- **SC-002** Una publicación nueva, con la Lambda desplegada, se ve en el Muro con su miniatura.

## Fuera de alcance

El difuminado (D3) y CloudFront delante de S3.
