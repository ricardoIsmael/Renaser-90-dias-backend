-- V107 — Cuentas de prueba para «ver como rol» (spec 002, fase 2, US5; contrato fase-2.md §5; D-270)
--
-- Por qué se toca la base:
--   El dueño eligió que Soporte TI recorra la app «como la vería» un aprendiz, una guía, un mentor, un líder, un
--   admin o un alquimista con CUENTAS DE PRUEBA, una por rol, en solo lectura (P2), también para los roles
--   personalizados (P4). Esas cuentas son filas reales de `usuarios` (así los guards, los padrones y el programa las
--   tratan igual que a cualquiera y la vista es fiel), pero no pueden aparecer en rankings, padrones, semáforos, listas
--   de Administración, chats ni avisos. Hace falta una marca en la fila que las listas puedan filtrar en el mismo
--   SELECT, sin consultar otra tabla por persona.
--
-- Por qué una columna nueva y no algo que ya existe:
--   * El correo (`ver-como+<rol>@renaser.invalid`) las identifica, pero filtrar por un patrón de texto en cada lista
--     es frágil (un cambio del patrón las haría reaparecer en silencio) y no usa índice.
--   * `estado` (ACTIVO / INACTIVO / SUSPENDIDO) no sirve: la cuenta de prueba tiene que estar ACTIVA para que la app la
--     muestre como la vería esa persona, y las listas ya cuentan a las activas.
--   * Una tabla aparte obligaría a cada lista a un JOIN o a un NOT EXISTS más; una columna booleana con DEFAULT false
--     no toca ninguna fila existente ni ningún INSERT de hoy.
--
-- Reglas que impone la base (las que se evalúan con la fila):
--   * una cuenta de prueba nunca es Soporte TI (la consola no se abre con «ver como»);
--   * su correo es siempre del dominio reservado `.invalid` (RFC 2606): no hay buzón detrás, nadie puede recibir un
--     código de recuperación ni un aviso por correo, y no choca con el correo de ninguna persona real;
--   * no guarda contraseña: no se entra con ella por el login; la única puerta es la sesión de solo lectura que abre
--     Soporte TI (`POST /api/v1/soporte-ti/ver-como`).
--
-- El índice parcial es casi vacío (hasta 6 + 20 filas): lo usan la búsqueda de las cuentas de prueba y los
-- `NOT EXISTS` de las listas que no leen `usuarios` directamente.

ALTER TABLE renaser.usuarios
    ADD COLUMN es_cuenta_de_prueba boolean NOT NULL DEFAULT false;

ALTER TABLE renaser.usuarios
    ADD CONSTRAINT usuarios_cuenta_de_prueba_ck CHECK (
        NOT es_cuenta_de_prueba
        OR (rol <> 'SOPORTE_TI'
            AND email LIKE 'ver-como+%@renaser.invalid'
            AND hash_contrasena IS NULL)
    );

CREATE INDEX usuarios_cuentas_de_prueba_idx ON renaser.usuarios (id) WHERE es_cuenta_de_prueba;
