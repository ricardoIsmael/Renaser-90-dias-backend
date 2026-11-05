-- V166 — Tipo de preferencia propio para «recordatorios de eventos por WhatsApp» (spec 041; D-322)
--
-- Por qué se toca la base: la baja de los recordatorios por WhatsApp (el botón «No recibir más», responder «BAJA» o el interruptor de
-- Yo → Notificaciones) tiene que vivir en un solo lugar, y ese lugar ya existe: `preferencias_notificacion`, cuya llave es el tipo. Con
-- otro tipo (por ejemplo `RECORDATORIO_EVENTO`, que es el del aviso de la app y el del chat), darse de baja del WhatsApp silenciaría
-- también el aviso del teléfono. Mismo criterio que V46, V59, V70, V87, V109 y V154: un valor más en el enum, no una tabla de permisos.
--
-- Sin fila de preferencia el tipo está ENCENDIDO (`PreferenciaNotificacion.DEFAULT_HABILITADA`): a quien nunca lo tocó le llega. Solo se
-- guarda una fila cuando alguien lo apaga o lo vuelve a encender.
--
-- Por qué ese nombre: nombra lo que la persona recibe y por dónde. Va en su propia migración porque ALTER TYPE ... ADD VALUE no se puede
-- usar en la misma transacción que lo estrena (igual que V109 y V154). La app instalada valida `type` como texto abierto: un valor
-- desconocido no la rompe.

SET search_path TO renaser, public;

ALTER TYPE tipo_notificacion ADD VALUE IF NOT EXISTS 'RECORDATORIO_EVENTO_WHATSAPP';
