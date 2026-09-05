-- ============================================================================
-- POST DIARIO EN COMUNIDAD: se le da la `clave_sistema` que le faltaba
-- ============================================================================
--
-- Que problema resuelve
-- ---------------------
-- El dueno del producto pidio (2026-09-04) que ese habito deje de marcarse hecho con el
-- gesto generico y solo se de por cumplido si el aprendiz publico de verdad en el Muro
-- ese dia. Esa es una regla PROPIA de un habito puntual, y el mecanismo que el modulo ya
-- tiene para eso es `PoliticaHabito` + `SelectorHabito` (habits/domain/model/politica/).
--
-- `SelectorHabito` tiene exactamente dos formas: por `tipo` (toda una forma estructural de
-- habito) y por `clave_sistema` (UN habito puntual del catalogo). Esta regla es del segundo
-- tipo: no aplica a "todo habito CHECKBOX" -- hay 15 mas y ninguno debe pedir publicacion --
-- sino a este habito y nada mas. Pero POST DIARIO EN COMUNIDAD es el unico de los cuatro
-- habitos protegidos de V18 SIN `clave_sistema` (`NULL` en el dato de origen, V4 linea 78),
-- asi que hoy no hay forma de seleccionarlo por el mecanismo existente.
--
-- Por que no se reusa una columna existente
-- -----------------------------------------
--   - `icono_clave` YA vale 'COMMUNITY_POST' para esta fila, pero es la clave del ICONO
--     (`iconos_habito`, V1): describe como se dibuja el habito, no que es. Un cambio de
--     icono pedido por diseno cambiaria en silencio a que habito le aplica la regla de
--     negocio. No se selecciona una regla por su dibujo.
--   - `titulo` esta explicitamente descartado como criterio en V18 y en
--     RenombreHabitoService: el titulo es editable (HabitRenameController) y renombrar el
--     catalogo no puede reasignar reglas.
--   - `id` uuid funciona (es lo que uso V18) pero solo dentro de SQL. Meter ese uuid de
--     produccion dentro del `SelectorHabito` de una politica en Java obligaria a agregar una
--     TERCERA forma de selector (`PorId`) al sellado, y a hardcodear un uuid en `domain/`.
--     `clave_sistema` es, textualmente, el campo que V1 linea 396 declara para esto:
--     "identidad funcional estable (integraciones)".
--
-- Por que este nombre
-- -------------------
-- 'COMMUNITY_POST', no uno nuevo: es el valor que la fila YA lleva en `icono_clave`, o sea
-- el identificador con el que este habito se conoce en el dato de origen. Sigue la
-- convencion SCREAMING_SNAKE en ingles de las otras seis claves del catalogo
-- ('DAILY_CLASS', 'PASTILLA_RENACER', 'PHONE_FREE_DAY', 'GREEN_JUICE',
-- 'WARM_LEMON_WATER', 'AUDIO_THERAPY_WEEKLY'). No colisiona con ninguna: la columna es
-- UNIQUE y ningun habito la usa hoy como clave_sistema (verificado contra V4 y V9 antes de
-- escribir esto).
--
-- V18 dejo esto anotado como "una decision de catalogo aparte, fuera de alcance de ESE
-- cambio". Esta migracion es esa decision, tomada cuando el pedido del dueno la hizo
-- necesaria.
--
-- Que NO cambia
-- -------------
-- Nada de la conducta actual depende de que esta columna sea NULL:
--   - V18 protege la fila por `id`, no por clave -- sigue igual de protegida.
--   - `RenombreHabitoService` solo deja renombrar las claves de su lista blanca
--     (CLAVES_RENOMBRABLES); 'COMMUNITY_POST' no esta ahi, asi que este habito sigue sin
--     poder renombrarse -- exactamente como hoy, que no se puede por tener clave NULL.
--   - `RegistroPoliticasHabito.para()` cae en la politica por `tipo` o en la GENERICA
--     cuando no hay politica para la clave: darle clave a un habito no le cambia el
--     comportamiento por si solo.
-- El efecto observable lo produce `PoliticaPostDiarioComunidad`, que se agrega en el mismo
-- cambio; sin esa clase esta migracion es inerte.
-- ============================================================================

BEGIN;

-- Explicito a proposito, mismo motivo que V3/V11/V12/V13/V17/V18: si esta migracion corre
-- sola en un despliegue posterior, el search_path que fija V1 no esta puesto en la sesion.
SET search_path TO renaser, public;

-- Por `id` y no por titulo, por el mismo motivo que V18: el titulo es editable. Es el mismo
-- uuid que V18 ya uso para este habito, preservado desde produccion
-- (docs/db/AUDITORIA_REDISENO_BD.md #14.8).
--
-- `AND clave_sistema IS NULL` hace la migracion segura de correr sobre una base donde
-- alguien ya se la haya puesto a mano: no pisa un valor existente y no puede chocar contra
-- el UNIQUE por su propia fila.
UPDATE habitos
   SET clave_sistema = 'COMMUNITY_POST',
       actualizado_en = now()
 WHERE id = '830c3d76-888a-4aef-bb30-fb0f0cc7ca73' -- POST DIARIO EN COMUNIDAD
   AND clave_sistema IS NULL;

COMMIT;
