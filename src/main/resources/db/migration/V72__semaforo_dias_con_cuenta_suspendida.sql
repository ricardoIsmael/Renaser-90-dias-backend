-- =====================================================================================
-- Semaforo: los dias en que la cuenta estuvo suspendida no se miden (D-209, 2026-09-27).
--
-- QUE PROBLEMA RESUELVE
--
-- Mientras una cuenta esta suspendida el barrido del semaforo no la calcula. Al reactivarla, la
-- corrida siguiente se pone al dia y calcula TODOS los dias que faltaban, incluidos los de la
-- suspension: `habits` le siguio generando habitos (quedan vencidos) y los objetivos planificados
-- siguen ahi, asi que esos dias cuentan como no cumplidos y la semana puede cerrar en rojo por dias
-- que la persona no pudo vivir (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §7, punto 2). El dueno
-- leyo «Hoy cuentan: al reactivar la cuenta, pueden dejar la semana en rojo» y eligio «Que no se
-- midan».
--
-- DE DONDE SALE LA SUSPENSION (Y POR QUE HACE FALTA GUARDARLA)
--
-- No hay en la base ningun registro de desde cuando hasta cuando estuvo suspendida una cuenta:
-- `usuarios.estado_cambiado_en` existe desde V1 pero nadie la escribe (y guardaria solo el ultimo
-- cambio), no hay tabla de auditoria de estados, y el outbox de Modulith corre con
-- `completion-mode: DELETE`, asi que los `EstadoDeCuentaCambiadoEvent` ya entregados no quedan. La
-- unica fuente confiable es ese evento, que `users` publica en el unico camino que suspende o
-- reactiva (`StaffAdminService.updateStatus`) con el instante del cambio. `points` lo escucha y
-- anota el tramo en SU tabla (D-41: no lee `usuarios`). Los dias se DERIVAN de ese instante en la
-- zona de la persona (regla 02 §2), no de lo que el barrido alcance a ver: las fechas salen exactas
-- aunque el evento se procese tarde o el barrido este apagado.
--
-- POR QUE EN `semaforo_pausas` Y NO UNA TABLA NUEVA
--
-- Para el calculo una suspension es exactamente lo que ya es una pausa del staff: un tramo de dias
-- que no entra al promedio, ni arriba ni abajo, sin borrar lo medido antes (`CalendarioDeMedicion`,
-- `FotoSemanal.dias_medidos`). La tabla ya tiene `desde`, `hasta`, `reanudada_el` y `reanudada_en`,
-- y ya cae en cascada con el programa. La spec de la retroalimentacion del 26/09 pide no crear
-- tablas (§0.3). Lo unico que falta es saber cual de las dos es cada fila, porque nacen, se muestran y
-- terminan distinto: la del staff se muestra como «pausa» y se cambia a mano; la suspension no se
-- muestra como pausa de nadie y termina sola al reactivar la cuenta.
--
-- `motivo`
--
-- 'PEDIDA_POR_LA_PERSONA' (la pausa del staff, todas las filas que ya existen) o
-- 'CUENTA_SUSPENDIDA'. «motivo» y no «tipo» u «origen»: es el mismo nombre que ya usa el semaforo para
-- decir por que un dia o una persona no tiene color (`MotivoSinDatos`, S-5). Texto con CHECK, como
-- `version_formula` de V68, y no un enum de Postgres: agregar un valor a un enum no se puede hacer
-- dentro de la misma transaccion en que se usa (V70 tuvo que ir sola por eso). DEFAULT
-- 'PEDIDA_POR_LA_PERSONA': es lo que fueron todas las filas hasta hoy y lo que inserta el codigo
-- anterior a V72, que no conoce la columna; asi volver a una imagen anterior no rompe la pausa del
-- staff. El codigo nuevo siempre manda el motivo, y una suspension sin motivo no pasa igual: el CHECK
-- de `hasta` la rechaza.
--
-- `hasta` NULL SOLO PARA LA SUSPENSION
--
-- Una suspension no tiene fecha de regreso: dura hasta que la reactivan. NULL es el valor honesto;
-- una fecha centinela (9999-12-31) se leeria como una pausa elegida. El CHECK
-- `semaforo_pausas_hasta_segun_motivo` lo ata al motivo: la pausa del staff sigue exigiendo su fecha.
--
-- `reanudada_el` DE UNA SUSPENSION
--
-- Conserva el significado de V68: el primer dia local que se vuelve a medir. Para una suspension es
-- el dia SIGUIENTE al de la reactivacion: no se mide ningun dia en que la cuenta estuvo suspendida,
-- aunque sea un rato, y eso incluye el dia en que se suspendio y el dia en que se reactivo.
-- `reanudada_en` guarda el instante de la reactivacion y `creada_en` el de la suspension.
--
-- EL RELLENO: CUENTAS YA SUSPENDIDAS AL DESPLEGAR
--
-- De las suspensiones anteriores a esta migracion no se sabe cuando empezaron, y no se inventa. Lo
-- que SI se sabe es que al desplegar estan suspendidas. Se les abre una suspension desde el dia del
-- despliegue (en su zona): desde hoy sus dias no se miden y, al reactivarlas, el evento la cierra.
-- Los dias ANTERIORES al despliegue en que ya estaban suspendidas se siguen midiendo como antes
-- (cuentan como no cumplidos si hubo algo programado); si alguien conoce las fechas por fuera,
-- `docs/DESPLIEGUE_Y_CI.md` §6.4 dice como anotarlas a mano. Solo programas activados: sin programa
-- activado el semaforo no mide a nadie. Una zona que Postgres no conozca cae en America/Lima, el
-- default de la columna, para que un dato raro no frene el despliegue.
--
-- VOLVER A UN BINARIO ANTERIOR
--
-- El codigo previo a V72 no conoce `hasta` NULL: al leer una suspension fallaria (`isAfter(null)`) y
-- con el la tarjeta de Hoy y la tabla del grupo de esa persona. Antes de volver a una imagen anterior
-- hay que borrar las filas 'CUENTA_SUSPENDIDA' (esas personas vuelven al comportamiento viejo) — ver
-- `docs/DESPLIEGUE_Y_CI.md` §6.4. La pausa del staff sigue andando por el DEFAULT de `motivo`.
-- =====================================================================================
SET search_path TO renaser, public;

ALTER TABLE semaforo_pausas
    ADD COLUMN motivo text NOT NULL DEFAULT 'PEDIDA_POR_LA_PERSONA';

ALTER TABLE semaforo_pausas
    ADD CONSTRAINT semaforo_pausas_motivo_valido
        CHECK (motivo IN ('PEDIDA_POR_LA_PERSONA', 'CUENTA_SUSPENDIDA'));

ALTER TABLE semaforo_pausas
    ALTER COLUMN hasta DROP NOT NULL;

ALTER TABLE semaforo_pausas
    ADD CONSTRAINT semaforo_pausas_hasta_segun_motivo
        CHECK ((hasta IS NULL) = (motivo = 'CUENTA_SUSPENDIDA'));

COMMENT ON TABLE semaforo_pausas IS
    'Dias que el semaforo no mide (D-168, D-209): pausas que pide el staff con programa propio y dias con la cuenta suspendida.';

COMMENT ON COLUMN semaforo_pausas.motivo IS
    'PEDIDA_POR_LA_PERSONA (pausa del staff, con hasta) o CUENTA_SUSPENDIDA (sin hasta; termina al reactivar la cuenta). V72.';

COMMENT ON COLUMN semaforo_pausas.reanudada_el IS
    'Primer dia local que se vuelve a medir. En una suspension, el dia siguiente al de la reactivacion. V68, V72.';

INSERT INTO semaforo_pausas (id, usuario_id, motivo, desde, hasta, reanudada_el, creada_en, reanudada_en)
SELECT gen_random_uuid(),
       pp.usuario_id,
       'CUENTA_SUSPENDIDA',
       (now() AT TIME ZONE COALESCE((SELECT z.name FROM pg_timezone_names z WHERE z.name = pp.timezone),
                                    'America/Lima'))::date,
       NULL,
       NULL,
       now(),
       NULL
  FROM participantes_programa pp
  JOIN usuarios u ON u.id = pp.usuario_id
 WHERE u.estado = 'SUSPENDIDO'
   AND pp.programa_activado_en IS NOT NULL;
