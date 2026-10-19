-- V105 — Roles personalizados que heredan de un rol base (spec 002, fase 2, US7; FR-029 a FR-034; D-272)
--
-- Por qué se toca la base:
--   El dueño pidió poder crear roles sin código («con esto ya podríamos hacer un CRUD de roles y asignar al rol los
--   permisos de las funcionalidades»). Eligió roles que HEREDAN de uno de los seis roles base y marcan un subconjunto de
--   sus funcionalidades del catálogo (spec 002, «Clarifications»). Soporte TI los crea desde su consola; Admin y
--   Alquimista los asignan donde hoy asignan el rol. Ninguna columna existente guarda esto: `rol_usuario` es un enum
--   cerrado (un valor nuevo por rol sería código y una migración por cada uno, justo lo que el dueño quiere evitar).
--
-- Qué se guarda y qué no:
--   * `usuarios.rol` SIGUE siendo el rol base (FR-030): guards, enums espejo de los módulos, rankings, padrones y
--     hábitos por rol no cambian. `usuarios.rol_personalizado_id` es una referencia opcional y solo la lee la
--     evaluación de interruptores (plataforma) y lo que muestra el nombre del rol.
--   * `roles_personalizados_funcionalidades` guarda las claves MARCADAS. Una funcionalidad que se suma al catálogo
--     después nace sin marcar (no tiene fila): eso es lo que pide la spec («Rol personalizado y funcionalidad nueva»).
--     Las claves viven en el catálogo en código (`plataforma`), no acá: por eso no hay FK, y una clave que deja de
--     existir se ignora al leer.
--   * `clave` es «rp:<id>»: la misma clave con la que el rol figura en la auditoría (`cambios_auditados.clave`) y en
--     `GET /admin/roles-asignables`, así la consola filtra la auditoría de un rol con su propia clave. No cambia nunca
--     (el id no cambia) y el CHECK lo garantiza.
--   * `version` es por fila (bloqueo optimista del PUT de la consola, 409), no una secuencia global: la copia en
--     memoria de la plataforma se recarga entera ante cualquier cambio (evento + canal Redis + cada 30 s).
--
-- Reglas que impone la base (las que se evalúan con los datos de la fila):
--   * el rol base nunca es Soporte TI (FR-029);
--   * nombre de 1 a 60 caracteres y descripción de hasta 280 (decisión técnica, como E-369: la consola no pone tope);
--   * nombre único. Que además no se repita sin tildes ni mayúsculas, que no sea el de un rol base y el máximo de 20
--     activos dependen de otras filas o del código: los impone el dominio (`users`), con las escrituras serializadas
--     por un candado de transacción (`pg_advisory_xact_lock`).
--   * `usuarios.rol_personalizado_id` sin ON DELETE: un rol con personas no se borra (FR-033). Si el código fallara,
--     la FK lo rechaza igual.
--
-- Sin FK a `usuarios` en `creado_por` ni `actualizado_por`, como `funcionalidades_estado` (V101): una FK impediría la
-- purga definitiva de la cuenta de Soporte TI que creó el rol (D-243). El id queda como dato histórico.

CREATE TABLE renaser.roles_personalizados (
    id               uuid                PRIMARY KEY,
    clave            text                NOT NULL UNIQUE,
    nombre           text                NOT NULL UNIQUE,
    descripcion      text                NULL,
    rol_base         renaser.rol_usuario NOT NULL,
    activo           boolean             NOT NULL,
    version          bigint              NOT NULL CHECK (version > 0),
    creado_por       uuid                NOT NULL,
    creado_en        timestamptz         NOT NULL,
    actualizado_por  uuid                NOT NULL,
    actualizado_en   timestamptz         NOT NULL,
    CONSTRAINT roles_personalizados_clave_ck CHECK (clave = 'rp:' || id::text),
    CONSTRAINT roles_personalizados_nombre_ck CHECK (char_length(btrim(nombre)) BETWEEN 1 AND 60),
    CONSTRAINT roles_personalizados_descripcion_ck CHECK (descripcion IS NULL OR char_length(descripcion) <= 280),
    CONSTRAINT roles_personalizados_base_ck CHECK (rol_base <> 'SOPORTE_TI')
);

CREATE TABLE renaser.roles_personalizados_funcionalidades (
    rol_id  uuid NOT NULL REFERENCES renaser.roles_personalizados (id) ON DELETE CASCADE,
    clave   text NOT NULL CHECK (clave ~ '^[a-z_]+(\.[a-z_]+)*$'),
    PRIMARY KEY (rol_id, clave)
);

ALTER TABLE renaser.usuarios
    ADD COLUMN rol_personalizado_id uuid NULL REFERENCES renaser.roles_personalizados (id);

-- Contar las personas de un rol (no se borra con personas, reemplazo en bloque) y armar la copia en memoria de la
-- plataforma recorren solo las cuentas que tienen uno: parcial, casi vacío.
CREATE INDEX usuarios_rol_personalizado_idx ON renaser.usuarios (rol_personalizado_id)
    WHERE rol_personalizado_id IS NOT NULL;
