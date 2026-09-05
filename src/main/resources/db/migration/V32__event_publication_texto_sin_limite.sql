-- Por que se toca la base
-- =======================
-- El outbox de Spring Modulith (`event_publication`) se creo en V2 con VARCHAR(255) en las tres
-- columnas de texto. El esquema OFICIAL de Modulith usa TEXT en las tres; el limite lo pusimos
-- nosotros y no aporta nada: nadie consulta esas columnas por rango ni las indexa por prefijo.
--
-- Durante meses no molesto porque los eventos existentes serializan a ~108 caracteres. El primer
-- evento con mas campos lo desbordo: `AvisoHabitoDebidoEvent` lleva dos UUID, el id del
-- participante, el titulo del habito, el tipo de aviso, los minutos que faltan, los puntos en
-- juego y el instante — su JSON pasa comodo los 255. Sintoma en produccion local:
--
--   ERROR: value too long for type character varying(255)
--   [habits.DespacharAvisosHabitoScheduler] 0 aviso(s) publicado(s), 8 participante(s) fallido(s)
--
-- Es decir: la funcion entera de avisos no publicaba NADA, y el fallo no se veia desde la app
-- porque el barrido captura por participante y sigue (`.claude/rules/02`). Solo aparecia en el log.
--
-- Por que TEXT y no un VARCHAR mas grande
-- ---------------------------------------
-- Elegir 512 o 1024 solo mueve la fecha del proximo desbordamiento: el tamanio depende de que
-- campos tenga el evento que alguien agregue el ano que viene, y no hay forma de acotarlo. En
-- Postgres TEXT y VARCHAR(n) tienen el MISMO rendimiento y el mismo almacenamiento — el limite no
-- compra nada, solo agrega una forma de fallar. Ademas asi el esquema queda igual al que publica
-- Spring Modulith, y una actualizacion de la libreria no vuelve a chocar.
--
-- `status` NO se toca: es un enum corto y acotado por la libreria (PUBLISHED/COMPLETED/FAILED).

ALTER TABLE event_publication ALTER COLUMN serialized_event TYPE TEXT;
ALTER TABLE event_publication ALTER COLUMN listener_id      TYPE TEXT;
ALTER TABLE event_publication ALTER COLUMN event_type       TYPE TEXT;
