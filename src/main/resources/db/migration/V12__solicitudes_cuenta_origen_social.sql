-- Guarda de que proveedor social vino un alta, para poder vincular la identidad al aprobarla.
--
-- El agujero que cierra (A-7 de docs/MODULO_AUTH.md): quien tocaba "Continuar con Google" y no
-- tenia cuenta llegaba a `AutenticacionSocialService`, que verificaba el `id_token` y abria una
-- `solicitudes_cuenta`... y ahi mismo perdia el `sub` del proveedor, porque no habia donde
-- guardarlo. Al aprobar, `identidades_externas` no se escribia nunca (no habia con que), asi que
-- el segundo intento de login no encontraba el vinculo, intentaba dar de alta otra vez, chocaba
-- con el usuario ya existente y respondia "inicia sesion con tu metodo actual" — un metodo que
-- esa persona no tiene, porque el alta social deja `usuarios.hash_contrasena` en NULL a
-- proposito. Resultado: quien se registraba con Google o Apple no podia volver a entrar nunca.
--
-- Por que aca y no en `identidades_externas`: la FK de esa tabla exige que la fila de `usuarios`
-- este ACTIVA y aprobada; el vinculo real solo puede escribirse al aprobar. Entre el alta y la
-- aprobacion puede pasar un dia, y el `sub` tiene que sobrevivir esa espera en algun lado. El
-- lugar natural es la solicitud misma, que es el registro de "esta alta, y como entro".
--
-- Por que se toca una BD declarada congelada (D-40): mismo criterio que V3 y V11 — es la misma
-- excepcion acotada de autenticacion propia. Dos columnas nullable, sin reescritura de la tabla
-- (ADD COLUMN sin DEFAULT es cambio de catalogo en Postgres 11+), y ningun dato existente cambia
-- de significado: toda solicitud ya cargada es de alta por formulario y queda con NULL en ambas,
-- que es exactamente lo que significa.

BEGIN;

-- Explicito a proposito, por el mismo motivo que V3 y V11: si esta migracion corre sola en un
-- despliegue posterior, el search_path que fija V1 no esta puesto en la sesion.
SET search_path TO renaser, public;

ALTER TABLE solicitudes_cuenta ADD COLUMN proveedor        text;
ALTER TABLE solicitudes_cuenta ADD COLUMN sujeto_proveedor text;

-- text con CHECK y no enum nativo: misma decision, mismo motivo que identidades_externas.proveedor
-- en V3 (sumar un proveedor es cambiar el CHECK, sin ALTER TYPE sobre un tipo compartido).
ALTER TABLE solicitudes_cuenta ADD CONSTRAINT solicitudes_proveedor_valido
    CHECK (proveedor IS NULL OR proveedor IN ('GOOGLE', 'APPLE', 'FACEBOOK'));

-- Un proveedor sin sujeto no identifica a nadie, y un sujeto sin proveedor es ambiguo (dos
-- proveedores distintos pueden emitir el mismo string). La misma regla vive en el constructor de
-- OrigenSocial; el CHECK la sostiene aunque alguien escriba la tabla por fuera del backend.
ALTER TABLE solicitudes_cuenta ADD CONSTRAINT solicitudes_origen_social_completo
    CHECK ((proveedor IS NULL AND sujeto_proveedor IS NULL)
        OR (proveedor IS NOT NULL AND sujeto_proveedor IS NOT NULL));

-- UNICO y parcial. Unico porque es la misma frontera de seguridad que la PK de
-- identidades_externas: dos solicitudes no pueden reclamar la misma identidad social. Parcial
-- porque las altas por formulario tienen ambas columnas en NULL y son mayoria — sin el WHERE,
-- todas entrarian al indice sin que ninguna consulta las busque nunca.
CREATE UNIQUE INDEX solicitudes_origen_social_idx
    ON solicitudes_cuenta (proveedor, sujeto_proveedor)
    WHERE sujeto_proveedor IS NOT NULL;

COMMENT ON COLUMN solicitudes_cuenta.proveedor IS
    'Proveedor social que abrio esta alta (GOOGLE | APPLE | FACEBOOK). NULL = alta por '
    'formulario, que es un estado valido y el de la enorme mayoria de las filas.';

COMMENT ON COLUMN solicitudes_cuenta.sujeto_proveedor IS
    'El `sub` que devolvio el proveedor al verificar el id_token: opaco y estable, NO el email. '
    'Se conserva desde el alta hasta la aprobacion, que es cuando recien se puede escribir la '
    'fila de identidades_externas (su FK exige que el usuario ya exista). Resolver identidad por '
    'correo permitiria apoderarse de una cuenta ajena: por eso este dato y no el email.';

COMMIT;
