# Checklist de requisitos: Vista del líder, limpia

- [x] Cada historia tiene criterios de aceptación verificables (US1 8, US2 5, US3 1 + tabla).
- [x] Los textos quitados están en una tabla con su motivo.
- [x] Lo que se ignora por decisión del dueño está escrito («por el momento no debería haber grupos»).
- [x] El supuesto del umbral está marcado como tal y localizado en un solo método.
- [x] Compatibilidad: backend anterior (sin `programacion`) y APK anterior (ignora el campo).
- [x] Autorización: sin endpoint nuevo; las pruebas del 403 por rol, persona de otro grupo y cuenta suspendida siguen y no
      leen nada de `habits` / `rocks`.
- [x] Tiempo: «hoy» en la zona de la persona, con prueba en la madrugada UTC.
- [x] Sin migración, sin tabla, sin dependencia nueva.
- [ ] Probado de punta a punta en el emulador (pendiente: la máquina la comparte un E2E; no se corrió aquí).
