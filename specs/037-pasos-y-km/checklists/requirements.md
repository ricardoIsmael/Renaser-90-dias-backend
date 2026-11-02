# Checklist de requisitos — spec 037

- [x] El pedido del dueño está citado textualmente y la maqueta aprobada (opción A) está referida.
- [x] El cambio del dueño (solo Health Connect, Android 14+) está en la spec, el plan, la investigación y el código; no hay Recording API ni módulo propio.
- [x] Ninguna tabla nueva; cada columna nueva tiene su justificación en `V161` y en `data-model.md`.
- [x] El servidor no confía en el teléfono: meta, topes, día, idempotencia y «por revisar».
- [x] Los supuestos para el dueño están a la vista (`spec.md`, «Supuestos para el dueño»).
- [x] Lo que no se hace está dicho (B, C, iPhone, Recording API).
- [x] Las dependencias nuevas se revisaron antes de instalar (`research.md`).
- [ ] `clean verify` del backend, `tsc` y Jest de la app corridos y en verde.
