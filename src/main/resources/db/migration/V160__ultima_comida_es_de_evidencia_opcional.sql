-- V160 — «ÚLTIMA COMIDA DEL DÍA» es de evidencia OPCIONAL (spec 038, decisión del dueño del 2026-11-02).
--
-- Qué problema resuelve
-- ---------------------
-- El seed del catálogo (V4) dejó «ÚLTIMA COMIDA DEL DÍA» (`3a8a82a0-787d-46bc-a69e-b29c110a37bc`, «Foto de la última comida del día 6pm») con
-- `exigencia_evidencia = 'OBLIGATORIA'`. El dueño decidió (2026-10-31, punto 8 del rediseño de Training) que ese hábito se marca de un toque con el
-- círculo, igual que Despertar y Dormir, sin pedir foto. La app ya lo hace así por el id de su plantilla de catálogo, PERO solo cuando el
-- servidor dice que la evidencia NO es obligatoria («si en algún entorno su evidencia fuera obligatoria, no se cierra con el círculo: pide su
-- registro»). Con el dato del seed, la app muestra «Subir» y SER le pide foto: coherentes entre sí y contra lo que decidió el dueño.
--
-- Qué cambia
-- ----------
-- * `exigencia_evidencia = 'OPCIONAL'` para ESE id y nada más. Con eso, en todos los entornos la app muestra el círculo y SER la marca con
--   `marcar_habito_completado` (propuesta con «Confirmar») en vez de `proponer_registrar_con_foto`. Si la persona igual quiere dejar una foto, un
--   texto, un audio o un video, lo sube ella en Training (es opcional).
-- * No toca `es_opcional` (qué cuenta para el porcentaje del día), ni el horario, ni la guía, ni ningún otro hábito. «PRIMERA COMIDA (ROMPO EL
--   AYUNO)» y el resto del catálogo siguen como estaban: el dueño nombró solo la última comida.
--
-- Idempotente: si el hábito ya es OPCIONAL (o el id no existe en ese entorno) no cambia ninguna fila.
SET search_path TO renaser, public;

UPDATE habitos
   SET exigencia_evidencia = 'OPCIONAL',
       actualizado_en = now()
 WHERE id = '3a8a82a0-787d-46bc-a69e-b29c110a37bc'
   AND exigencia_evidencia <> 'OPCIONAL';
