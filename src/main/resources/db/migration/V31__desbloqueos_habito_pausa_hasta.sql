-- La pausa del aprendiz pasa de "indefinida" a poder tener FECHA DE FIN.
--
-- PEDIDO (dueno del producto, 2026-09-04): al pausar un habito desde el Plan, poder elegir hasta
-- cuando -- "hasta el domingo" fue el ejemplo textual. Hoy `pausado_en` (V23) solo dice DESDE
-- cuando esta pausado; no hay forma de decir hasta cuando, asi que toda pausa es para siempre
-- hasta que alguien se acuerde de volver a encenderlo.
--
-- POR QUE UN RANGO Y NO "dias de la semana". Se evaluaron las dos. Un patron semanal ("nunca los
-- sabados") crea un agujero PERMANENTE y silencioso en un programa de 90 dias, y ademas
-- duplicaria una regla que ya existe: que dias aplica un habito lo decide el catalogo via
-- `horarios_habito.tipo_dia` (expuesto como `activeWeekdays`). Dos fuentes respondiendo "¿va
-- hoy?" es exactamente la duplicacion que CLAUDE.MD manda evitar. El rango se cura solo: llegada
-- la fecha, el habito vuelve sin que nadie haga nada.
--
-- POR QUE UNA COLUMNA Y NO UNA TABLA: mismo razonamiento que V23. `desbloqueos_habito` YA es "que
-- habitos lleva este aprendiz"; el fin de la pausa es un atributo de esa relacion, no una
-- relacion nueva.
--
-- SEMANTICA (la impone el dominio, no la base -- ver DesbloqueoHabito):
--   pausado_en NULL                        -> ACTIVO.
--   pausado_en con valor, pausado_hasta NULL -> pausado INDEFINIDAMENTE (comportamiento de V23,
--                                             que se conserva tal cual para las filas existentes).
--   pausado_en con valor, pausado_hasta = D -> pausado hasta el final del dia D INCLUSIVE; desde
--                                             D+1 vuelve a estar activo, sin necesidad de que
--                                             corra ningun cron ni de que el aprendiz lo toque.
--
-- `date` y no `timestamptz`: la pausa se razona en dias del calendario del aprendiz ("hasta el
-- domingo"), y comparar contra una hora exacta reintroduciria el problema de zonas de E-91. La
-- fecha se evalua SIEMPRE en la zona del participante (`participantes_programa.timezone`), nunca
-- con la del servidor.
--
-- El CHECK vive aca porque la invariante se puede evaluar con los datos de la MISMA fila (regla
-- de .claude/rules/04): no tiene sentido una fecha de fin sin una pausa que terminar.

SET search_path TO renaser, public;

ALTER TABLE desbloqueos_habito
    ADD COLUMN pausado_hasta date;

ALTER TABLE desbloqueos_habito
    ADD CONSTRAINT desbloqueos_pausa_hasta_requiere_pausa
    CHECK (pausado_hasta IS NULL OR pausado_en IS NOT NULL);

COMMENT ON COLUMN desbloqueos_habito.pausado_hasta IS
    'Ultimo dia (INCLUSIVE) en que el habito sigue pausado, en la zona del participante. NULL con '
    'pausado_en = pausa indefinida (comportamiento de V23). Desde el dia siguiente el habito '
    'vuelve solo: la reanudacion se DERIVA de la fecha, no la ejecuta ningun cron. Ver V31.';
