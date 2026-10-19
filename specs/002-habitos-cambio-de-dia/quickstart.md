# Validación

1. Backend aislado: `TZ=UTC ./mvnw clean verify`; revisar resúmenes Surefire y Failsafe.
2. App: `node node_modules/typescript/bin/tsc --noEmit`; `node node_modules/jest/bin/jest.js --runInBand`; exportación web.
3. Reloj controlado: snapshot Lima a 04:59:59Z, avanzar hasta 05:00:00Z; hábitos anteriores dejan de ser operables y se piden los nuevos. Repetir Madrid y días de cambio estacional.
4. App/pestaña suspendida y regreso a Training: refrescar sin restaurar respuesta vieja; lista vacía válida.
5. Mantener formulario o cámara abierta al corte, también desde SER/aviso: ningún POST al registro viejo tras comprobar que ya no está. Si cambia durante el POST, 409 actualiza y no celebra.
6. Sin red al corte: mensaje y reintento; no datos vencidos operables. Servidor viejo con 404 del snapshot: array anterior, refresco periódico y validación del ID.
7. Conservar doble toque, evidencia ya subida, registro ya completado, otros 409 y sesiones especiales.

Las pruebas automatizadas no sustituyen una comprobación visual en APK nuevo; resultados y límites quedan en `validation.md`.
