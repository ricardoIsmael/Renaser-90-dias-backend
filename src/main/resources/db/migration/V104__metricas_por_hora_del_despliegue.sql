-- V104 — Uso y errores por hora de las funcionalidades en despliegue (spec 002, fase 2, FR-020; D-271)
--
-- Por qué se toca la base:
--   En cada escalón del despliegue, Soporte TI decide si sube o baja mirando cuántos pedidos y cuántos errores del
--   servidor tuvieron quienes la tienen, contra quienes no (spec US4, escenario 5; decisión del dueño P5: las últimas
--   24 h, por hora). Ese número tiene que sumar lo de todas las instancias y sobrevivir a un reinicio, así que no
--   alcanza con contadores en memoria. Tampoco va a Prometheus: separar por persona o por grupo dentro/fuera ahí
--   multiplica las series (cardinalidad), y el gráfico se dibuja en la app, no en Grafana.
--
-- Cómo se llena (para que el costo sea el de una consulta por minuto, no una por pedido):
--   El interceptor de interruptores cuenta en memoria cada pedido a una funcionalidad con despliegue activo, separado
--   por la hora (truncada, en UTC) y por si la persona estaba dentro del despliegue. Cada minuto, cada instancia escribe
--   lo suyo en un solo INSERT … ON CONFLICT DO UPDATE que SUMA a lo que ya había: dos instancias no se pisan.
--
-- Por qué una tabla aparte de V103 y sin FK a `funcionalidades_despliegue`:
--   Las métricas sobreviven al fin del despliegue: «Prender para todos» borra la fila de V103 y la consola sigue
--   mostrando las últimas horas para comparar. Un barrido borra lo de más de 48 h (la consola muestra 24).
--
-- `errores` cuenta las respuestas 5xx; un 503 por un interruptor apagado no llega a contarse (es mantenimiento, no una
-- falla). Un error también es un pedido, por eso errores <= pedidos.

CREATE TABLE renaser.funcionalidades_metricas_hora (
    clave    text        NOT NULL,
    hora     timestamptz NOT NULL,
    dentro   boolean     NOT NULL,
    pedidos  bigint      NOT NULL CHECK (pedidos >= 0),
    errores  bigint      NOT NULL CHECK (errores >= 0),
    PRIMARY KEY (clave, hora, dentro),
    CONSTRAINT funcionalidades_metricas_hora_errores_ck CHECK (errores <= pedidos)
);
