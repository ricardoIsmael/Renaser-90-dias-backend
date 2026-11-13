-- V192 — El WhatsApp que cada cuenta confirmó con un código (spec 051, Yo → «WhatsApp»).
--
-- QUÉ RESUELVE. La recuperación de la contraseña por WhatsApp (spec 043) lee el número de la Ficha Inicial (`respuestas_onboarding`,
-- clave `whatsapp`). El personal (ALQUIMISTA, MENTOR, GUÍA, ADMIN y cualquier rol sin Ficha Inicial) no tiene ficha, así que el
-- código nunca le llega. El dueño decidió (12/11/2026) que cualquiera pueda agregar o cambiar su número en Yo, y que solo se guarde
-- después de confirmarlo con un código de 6 dígitos por WhatsApp.
--
-- POR QUÉ UNA TABLA NUEVA Y NO UNA COLUMNA NI LA RESPUESTA DE LA FICHA. `usuarios.telefono` existe pero nadie la lee ni la escribe y su
-- significado es ambiguo («teléfono declarado»); escribir la respuesta de la Ficha mezclaría lo que la persona declaró con lo que
-- demostró controlar, y el personal no tiene ficha. Un dato «confirmado» es otra cosa que un dato «declarado», y vive en `users`
-- (el módulo dueño de la cuenta), sin tocar tablas ajenas. La recuperación prefiere este número y, sin él, cae al de la ficha:
-- las cuentas que ya funcionan siguen igual.
--
-- QUÉ GUARDA. Una fila por cuenta (`usuario_id` PK, FK con CASCADE: borrar la cuenta se lleva el número) y el número en formato
-- internacional (`+51987654321`, el que normaliza `NumeroDeWhatsApp`). `numero` es UNIQUE: un número se confirma en una sola
-- cuenta, así un código de recuperación no puede ir a una cuenta ajena que lo declaró después. El CHECK solo mira la forma
-- (`+` y de 8 a 15 dígitos), como `NumeroDeWhatsApp`.
SET search_path TO renaser, public;

CREATE TABLE IF NOT EXISTS whatsapp_confirmados (
    usuario_id    uuid        PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    numero        text        NOT NULL UNIQUE CHECK (numero ~ '^\+[0-9]{8,15}$'),
    confirmado_en timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE whatsapp_confirmados IS 'El WhatsApp que la cuenta confirmó con un código enviado a ese número (spec 051). La recuperación de la contraseña lo prefiere al de la Ficha Inicial.';
