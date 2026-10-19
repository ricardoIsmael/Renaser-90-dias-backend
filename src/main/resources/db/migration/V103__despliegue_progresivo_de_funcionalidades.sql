-- V103 — Despliegue progresivo de funcionalidades: porcentaje y probadores (spec 002, fase 2, US4; D-271)
--
-- Por qué se toca la base:
--   El dueño pidió que una funcionalidad nueva llegue primero a una lista de probadores y después a un porcentaje
--   estable de personas (5 → 25 → 50 → 100 %), subiendo o bajando el escalón a mano desde la consola Soporte TI
--   (spec 002, FR-018 a FR-021; contrato fase 2 §3). El escalón tiene que valer igual en todas las instancias y
--   sobrevivir a un reinicio: vive en la base, y cada instancia guarda una copia en memoria que se pone al día por el
--   canal Redis `renaser:funcionalidades` y cada 30 s, como los interruptores de V101.
--
-- Por qué tablas nuevas y no columnas en `funcionalidades_estado` (V101):
--   Son dos cosas que se cambian por separado y con reglas distintas. Un interruptor apagado gana sobre el despliegue
--   (FR-021), y `funcionalidades_estado` guarda solo desvíos del valor por defecto: una funcionalidad en despliegue y
--   prendida no tiene fila ahí. Con columnas, «prendida y en despliegue al 25 %» obligaría a crear una fila de estado
--   que no es un desvío, y cada recarga mezclaría las dos versiones.
--
-- Qué se guarda:
--   * `funcionalidades_despliegue`: una fila por funcionalidad EN despliegue. Sin fila, no está en despliegue y llega a
--     todos. «Prender para todos» (100 %) BORRA la fila, por eso el porcentaje va de 0 a 99. La clave existe en el
--     catálogo de código (lo valida la consola al escribir; una clave que ya no está se ignora al evaluar).
--   * `version` sale de la secuencia global de V101 (`funcionalidades_version_seq`): es el bloqueo optimista del
--     `PUT` (409) y nunca se repite aunque la fila se borre y se vuelva a crear, así una pantalla vieja no pisa un
--     despliegue nuevo con la versión del anterior.
--   * `funcionalidades_probadores`: quiénes la ven aunque su cubeta quede fuera del porcentaje. Se borran con la fila
--     del despliegue (CASCADE) y con la cuenta de la persona (CASCADE: una cuenta purgada deja de ser probadora).
--   * El historial de escalones es la auditoría de V101 (`cambios_auditados`, tipo MANUAL), no una tabla más.
--   * La cubeta de cada persona NO se guarda: es una función pura de la clave y el id (SHA-256), así es estable sin
--     que haya que asignarla ni mantenerla.
--
-- Desvío del contrato fase 2 §3.1, a la vista (mismo criterio que V101): `actualizado_por` y `sumado_por` NO llevan FK
--   a `usuarios`. Una FK sin CASCADE impediría el borrado definitivo de la cuenta Soporte TI que hizo el cambio (D-243,
--   la purga de la gracia de 30 días) y, en las pruebas, la limpieza de las cuentas de prueba. El id queda como dato;
--   si la cuenta ya no existe, la consola lo muestra sin nombre. `usuario_id` (la probadora) sí lleva FK, con CASCADE.

CREATE TABLE renaser.funcionalidades_despliegue (
    clave            text        PRIMARY KEY,
    porcentaje       smallint    NOT NULL CHECK (porcentaje BETWEEN 0 AND 99),
    version          bigint      NOT NULL CHECK (version > 0),
    iniciado_en      timestamptz NOT NULL,
    actualizado_por  uuid        NOT NULL,
    actualizado_en   timestamptz NOT NULL
);

CREATE TABLE renaser.funcionalidades_probadores (
    clave       text        NOT NULL REFERENCES renaser.funcionalidades_despliegue (clave) ON DELETE CASCADE,
    usuario_id  uuid        NOT NULL REFERENCES renaser.usuarios (id) ON DELETE CASCADE,
    sumado_por  uuid        NOT NULL,
    sumado_en   timestamptz NOT NULL,
    PRIMARY KEY (clave, usuario_id)
);
