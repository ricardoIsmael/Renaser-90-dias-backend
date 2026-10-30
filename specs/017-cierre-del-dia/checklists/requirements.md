# Checklist de requisitos: Cierra tu día (spec 017)

- [x] La spec dice quién lo ve, que es opcional y que no da puntos, con las decisiones del dueño del 2026-10-29 a la vista.
- [x] Los supuestos del equipo están separados de las decisiones del dueño (hora de la fila en la app, nota por acción, Espejo Sombra, constancia de consulta).
- [x] Ninguna tabla nueva (restricción del dueño); una migración con cabecera que justifica por qué columnas y por qué no un enum.
- [x] Cada ruta tiene permiso, interruptor de Soporte TI y prueba de autorización negativa.
- [x] Todo comportamiento diario se prueba con el reloj en la madrugada UTC.
- [x] Guardar el cierre no completa acciones ni toca puntos: prueba de dominio, de servicio, de persistencia y (escrita) de integración.
- [x] Compatibilidad: la app nueva contra el backend anterior no muestra la fila; el APK anterior no nota nada.
- [x] Documentos del mismo cambio: D-290, módulos, mapa de endpoints.
- [ ] Pendiente fuera de esta spec: Política de Privacidad; `./mvnw verify` completo con Docker.
