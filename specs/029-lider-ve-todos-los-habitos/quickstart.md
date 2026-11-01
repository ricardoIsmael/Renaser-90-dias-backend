# Quickstart: validar «Hábitos de todos» (spec 029)

1. Backend local con base clonada (nunca producción). `TZ=UTC ./mvnw -B verify` en verde (incluye `HabitosDeTodosIT`).
2. Con dos aprendices de la Escuela y uno del Diplomado, cada uno con sus registros de hoy, entrar como líder de mentores (formación Escuela) → Yo → «Mis mentores» → «Hábitos de todos».
3. Se ve el resumen de hoy y los hábitos de la Escuela con menos cumplimiento primero; nada del Diplomado.
4. Tocar «Despertar»: los alumnos en secciones (sin cumplir / pendientes, cumplieron, no les tocaba), con la hora planificada y la de cumplimiento. Buscar por nombre.
5. Tocar un alumno: su ficha de siempre, en ese día; al volver se conservan el hábito, el filtro y la posición.
6. ‹ para ayer: lo no cumplido pasa de «Pendiente» a «Sin cumplir»; las cifras cuadran con las fichas.
7. Un mentor y una guía no ven la fila; pedir la ruta a mano da 403.
