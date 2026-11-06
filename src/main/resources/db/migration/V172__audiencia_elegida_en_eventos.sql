-- V172 — Un tipo de audiencia nuevo para los eventos: SELECCION (spec 044, pedido del dueño del 2026-11-06)
--
-- Por qué se toca la base
-- -----------------------
-- Hasta hoy un evento era para todos, para un nivel, un curso, unos roles o UN grupo (`tipo_audiencia`, V1). El dueño pidió que el líder de
-- mentores, el mentor y la guía de aprendiz creen eventos para personas elegidas por su nombre, sus alumnos, un color del semáforo o el personal, y
-- que esas opciones se puedan sumar. Ninguno de los cinco valores lo dice, así que se agrega uno, `SELECCION`, y las partes viven en las tablas de
-- V173.
--
-- Va sola en su migración: Postgres no deja usar en la misma transacción un valor de enum recién agregado, y V173 lo usa en un CHECK.
-- Ningún evento existente cambia (FR-031).

SET search_path TO renaser, public;

ALTER TYPE tipo_audiencia ADD VALUE IF NOT EXISTS 'SELECCION';
