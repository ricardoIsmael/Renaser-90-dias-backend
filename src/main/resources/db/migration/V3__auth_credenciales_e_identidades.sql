-- Autenticacion propia (docs/MODULO_AUTH.md). Excepcion acotada a D-40, que habia congelado
-- la BD en 90 tablas: autenticar en casa es imposible sin guardar credenciales. La propuesta
-- inicial eran 4 tablas; quedo en 2 columnas + 1 tabla tras revisarla con el dueno del proyecto.
--
-- Lo que NO esta aca, y por que:
--  * Sesiones -> Redis (spring-session-data-redis). Son efimeras, tienen TTL propio y se
--    consultan una vez por request; una tabla obligaria a escribir un cron de purga.
--  * Tokens de verificacion de correo -> no existen: el alta la aprueba un ADMIN, y esa
--    aprobacion es mas fuerte que un mail. En login social el correo ya viene verificado.
--  * Token de reset de contrasena -> Redis con TTL. El "un solo uso" sale gratis al borrarlo.

BEGIN;

-- Explicito a proposito, no heredado: V1 fija el search_path en su linea 32 y eso persiste en
-- la sesion mientras todas las migraciones corran de una (arranque desde cero). Pero si V1 ya
-- estaba aplicada y esta corre sola en un despliegue posterior, el search_path NO esta puesto y
-- los objetos caerian en `public`. V2 tiene esa misma fragilidad; no se toca porque cambiarle el
-- contenido rompe su checksum de Flyway.
SET search_path TO renaser, public;

-- ── Credencial: 1:1 estricto con el usuario, por eso columna y no tabla ────────────────────
-- NULL es un estado valido y esperado: quien entra solo por Google/Apple no tiene contrasena.
ALTER TABLE usuarios ADD COLUMN hash_contrasena text;
ALTER TABLE usuarios ADD COLUMN contrasena_actualizada_en timestamptz;

COMMENT ON COLUMN usuarios.hash_contrasena IS
    'Hash con prefijo de algoritmo ({bcrypt}$2a$...), generado por el PasswordEncoder de Spring. '
    'NULL = la cuenta solo entra por proveedor social. NUNCA se mapea en UserJpaEntity: se lee '
    'con una entidad aparte para que no pueda salir por una respuesta HTTP ni por un log.';

-- ── Identidades externas: 1:N real (un usuario puede vincular Google Y Apple Y Facebook) ───
CREATE TABLE identidades_externas (
    -- PK natural, siguiendo la convencion de V1 para tablas asociativas. Ademas ES la frontera
    -- de seguridad: impide que dos usuarios reclamen el mismo sujeto del mismo proveedor, que
    -- es como se toma una cuenta ajena.
    proveedor        text        NOT NULL CHECK (proveedor IN ('GOOGLE', 'APPLE', 'FACEBOOK')),
    -- El `sub` del proveedor: opaco y estable. NO el email -- resolver identidad por correo
    -- permitiria que quien registre una cuenta social con el correo de un aprendiz existente
    -- se apodere de ella.
    sujeto_proveedor text        NOT NULL,
    usuario_id       uuid        NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    -- Informativo, para mostrar "vinculada a juan@..." en pantalla. Nunca para autenticar.
    email_proveedor  text,
    vinculada_en     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (proveedor, sujeto_proveedor)
);

-- Para listar las cuentas vinculadas de alguien y para que el ON DELETE CASCADE no haga
-- seq scan (toda FK con indice, convencion de V1).
CREATE INDEX identidades_externas_usuario_idx ON identidades_externas (usuario_id);

COMMENT ON TABLE identidades_externas IS
    'Vinculo entre un usuario nuestro y su cuenta en un proveedor social. text con CHECK y no '
    'enum nativo: sumar un proveedor es cambiar el CHECK, sin ALTER TYPE sobre un tipo compartido.';

COMMIT;
