# Checklist de requisitos — spec 042

- [x] Las decisiones del dueño del 2026-11-05 están citadas (flujo, sin meta, Google Maps 3D, avatar por `sexo`, Android primero,
      segundo plano, y el avatar al instante «incluso 4 pasos»).
- [x] La idempotencia está explicada en simple y en el contrato: `carreraId` del teléfono, índice único, huella, respuesta repetida igual,
      concurrencia.
- [x] Durante la carrera no hay pedidos al servidor (FR-004, SC-001); una sola escritura al terminar (FR-016).
- [x] Sin red: cola local con espera creciente y el texto que ve la persona (FR-017, FR-018).
- [x] Sin doble conteo con Health Connect: regla del mayor (FR-022, SC-008).
- [x] Día de la persona y regla del 17-oct: no se reasigna un día cerrado (FR-021, FR-023, SC-009).
- [x] Límites anti-fraude y «por revisar» (FR-024, FR-025).
- [x] Fusión de sensores con criterios medibles (FR-030 a FR-036, SC-005, SC-006).
- [x] Privacidad de la ruta: quién la ve, cuánto se guarda, zona recortada (FR-040 a FR-043).
- [x] Una sola tabla nueva, justificada; el cierre sigue por `RegistroService.completar`.
- [x] Supuestos para el dueño a la vista (11).
- [x] Lo que necesita: APK nuevo, clave de Maps, migración, Play Console, política de privacidad.
- [x] Lo que no se hace está dicho (iPhone, metas, historial, Strava).
- [x] Los criterios que no se pueden probar con código llevan `[sin prueba automática: …]` (SC-007, SC-011).
- [ ] El dueño respondió los supuestos.
- [ ] Investigación técnica de la app hecha (plan, «Investigación pendiente»).
- [x] La spec NO está en `specs/trazabilidad.json` (entra en el mismo cambio que pone sus marcas, regla 06).
