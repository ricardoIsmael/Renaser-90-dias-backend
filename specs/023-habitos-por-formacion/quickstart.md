# Quickstart: validar la spec 023 de punta a punta

1. Backend local con base clonada (nunca producción). `TZ=UTC ./mvnw -B verify` en verde (incluye `HabitosPorFormacionIT`).
2. Entrar como ADMIN → Administración → Más opciones → «Hábitos del programa». Elegir **Diplomado**: la lista está vacía.
3. «Categorías» → crear «Estudio» con el ícono de libro; crear «Práctica». Crear el hábito «Leer treinta minutos» en «Estudio» (el formulario solo ofrece las
   categorías del Diplomado).
4. «Copiar a otra formación»: copiar «Despertar» de la Escuela al Diplomado, en «Estudio». Cambiar la hora de la copia: el original no cambia.
5. Entrar con un aprendiz del Diplomado (con su Día 1 elegido): Training muestra «Estudio» y «Práctica» (si tiene hábitos) y nada de la Escuela; entrar con uno
   de la Escuela: ve lo de siempre y nada del Diplomado.
6. Intentar desactivar «Estudio» con el hábito activo: «Esta categoría tiene hábitos activos. Muévelos o desactívalos primero.».
7. Un mentor asignado a Escuela y Diplomado cambia de formación activa: sus aprendices y los hábitos que ve cambian por completo.
8. Un líder de mentores intenta `POST /api/v1/admin/habit-categories`: 403.
