# Cómo probarlo — Spec 056

1. Backend local con base clonada (`sin-senal.sh levantar` con `WORKTREE=wt-056-lider-de-mentores`), app con Metro del worktree y el build debug en el emulador. Nunca contra producción.
2. Entrar con el líder de prueba (`entrar-sin-borrar.py`, rol MENTOR_LEAD de `habitos-por-rol.json`).
3. Yo → Mis mentores: «Por resolver» y la lista con cupo (Historia 1).
4. Tocar un mentor que sigue su programa y uno que no (Historia 2); guardar «Alertar» y comprobar el mensaje y el aviso en la cuenta del mentor.
5. Abrir un grupo y la ficha de un aprendiz; ver una evidencia; «Avisar a su mentor» (Historia 3).
6. Grupos → Crear grupo con 2 aprendices; Asignar mentor a un grupo sin mentor (Historia 4).
7. «Descargar semana» en las dos fichas; repetir sin cambios (mismo documento, `reutilizado: true`); el enlace vence a los 10 minutos (Historia 5).
8. Consultas: Recordar y Responder yo; Reporte del mes → Exportar; Programar sesión con Meet (Historia 6).
9. Maestro: los flujos del líder nuevos y los de la 055 (la ficha del mentor no cambia). Logs del backend y `adb logcat` sin errores nuevos.
