-- «Cierra tu dia»: lo que la persona dice de cada accion del dia que le quedo pendiente. Spec 017, decision D-290.
--
-- QUE PROBLEMA RESUELVE. Pedido del dueno (2026-10-29), sobre el encargo de gerencia «Cerrar y evaluar mi dia»: al final del dia
-- la persona cuenta, de cada accion de sus objetivos que no hizo, que paso («No me alcanzo el tiempo», «Surgio otra cosa»,
-- «Me falto energia», «Lo deje para despues») y que hara manana («Hacerla a otra hora», «Hacerla mas corta», «Pedir ayuda»,
-- «Dejarla»), y puede escribir una nota. Lo ven ella y su acompanamiento (mentor, lider, admin y alquimista). Es opcional, no da
-- puntos y no cuenta en el semaforo. Hasta hoy una accion (`rocas_diarias`) solo sabe si se completo (R-02: solo con evidencia):
-- no hay ningun campo de resultado, nota o motivo, y `acciones_diarias` (V61) son los pasos de la accion, sin estado, a proposito.
--
-- POR QUE COLUMNAS EN `rocas_diarias` Y NO UNA TABLA NUEVA. El dueno pidio expresamente no crear una tabla nueva sino reutilizar.
-- La respuesta es de UNA accion y de UN dia: su clave es la de la propia fila (`rocas_diarias.id`), de modo que una tabla aparte
-- duplicaria `(participante_id, fecha)` y la posicion para volver a decir «de cual accion hablo». Cuatro columnas nulas la extienden
-- sin tocar ninguna fila existente. Lo que NO es de una accion -la marca de «dia cerrado» y la nota del dia cuando no hubo
-- pendientes- no cabe aqui: va en `entradas_diario`, tipo `EVALUACION_DIA`, que existe desde V1 con `UNIQUE (participante_id,
-- fecha, tipo)` y nadie escribia (ver `EvaluacionDelDiaService`). Esa fila no necesita migracion.
--
-- POR QUE TEXT CON CHECK Y NO UN ENUM DE POSTGRES. Los cuatro motivos y los cuatro planes son una lista corta que el dueno puede
-- cambiar («Pedir ayuda» por otra cosa): con `text` + CHECK sumar o quitar un valor es reemplazar una restriccion, con un tipo
-- enumerado seria `ALTER TYPE` (que no se puede revertir ni usar en la misma transaccion). El dominio (`MotivoDeAccionPendiente`,
-- `PlanDeManana`) es quien nombra los valores; el CHECK es la ultima barrera contra un valor roto.
--
--   cierre_motivo   que paso con la accion (uno de cuatro)
--   cierre_manana   que hara manana con ella (uno de cuatro)
--   cierre_nota     lo que quiso contar (1 a 500 caracteres; sin nota es NULL, nunca vacia)
--   cierre_en       cuando se guardo ESTA respuesta (la ultima; el dia en si se cierra una vez, en `entradas_diario`)
--
-- LOS CHECK miran solo la fila (regla 04):
--   * motivo, plan y hora vienen JUNTOS o ninguno: una respuesta a medias no existe (la app no deja guardar con un chip sin elegir).
--   * la nota exige respuesta: una nota suelta no es el cierre de nada.
--   * NO hay un CHECK de «solo si la accion sigue pendiente» (`completada = false`): la accion se puede cumplir DESPUES de cerrar
--     el dia, y ese UPDATE (R-02) no tiene por que conocer el cierre. La lectura oculta la respuesta de una accion cumplida
--     y la proxima vez que se guarde el cierre la limpia. Un CHECK aqui haria fallar el cumplimiento de una accion.
--
-- GUARDAR EL CIERRE NUNCA TOCA `completada`, `completada_en` NI `puntos_otorgados`. Lo garantiza el adaptador (UPDATE dirigido solo a
-- estas cuatro columnas), no esta migracion; se dice aqui porque es la razon por la que no hay trigger ni evento de dominio.
--
-- COMPATIBILIDAD. Toda fila existente queda con las cuatro columnas en NULL: nada cambia de significado. El APK publicado no las
-- manda ni las lee. Borrar la cuenta borra las filas (`ON DELETE CASCADE` de `rocas_diarias` hacia `participantes_programa`).

SET search_path TO renaser, public;

ALTER TABLE rocas_diarias
    ADD COLUMN cierre_motivo text        NULL,
    ADD COLUMN cierre_manana text        NULL,
    ADD COLUMN cierre_nota   text        NULL,
    ADD COLUMN cierre_en     timestamptz NULL;

ALTER TABLE rocas_diarias
    ADD CONSTRAINT rocas_diarias_cierre_motivo_valido
        CHECK (cierre_motivo IS NULL
               OR cierre_motivo IN ('SIN_TIEMPO', 'SURGIO_OTRA_COSA', 'SIN_ENERGIA', 'PARA_DESPUES')),
    ADD CONSTRAINT rocas_diarias_cierre_manana_valido
        CHECK (cierre_manana IS NULL
               OR cierre_manana IN ('OTRA_HORA', 'MAS_CORTA', 'PEDIR_AYUDA', 'DEJARLA')),
    ADD CONSTRAINT rocas_diarias_cierre_completo
        CHECK ((cierre_motivo IS NULL) = (cierre_manana IS NULL)
               AND (cierre_motivo IS NULL) = (cierre_en IS NULL)),
    ADD CONSTRAINT rocas_diarias_cierre_nota_con_respuesta
        CHECK (cierre_nota IS NULL
               OR (cierre_motivo IS NOT NULL AND char_length(cierre_nota) BETWEEN 1 AND 500));

COMMENT ON COLUMN rocas_diarias.cierre_motivo IS
    '«Cierra tu dia» (spec 017): que paso con la accion que quedo pendiente. NULL = sin respuesta. Nunca implica completada.';
COMMENT ON COLUMN rocas_diarias.cierre_manana IS
    '«Cierra tu dia» (spec 017): que hara manana con la accion. Viene junto con cierre_motivo.';
COMMENT ON COLUMN rocas_diarias.cierre_nota IS
    '«Cierra tu dia» (spec 017): nota opcional de la persona (1 a 500 caracteres). Dato personal: no se loguea.';
COMMENT ON COLUMN rocas_diarias.cierre_en IS
    '«Cierra tu dia» (spec 017): cuando se guardo esta respuesta.';
