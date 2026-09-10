package com.renaser.os.mentoring.domain.model.aviso;

import com.renaser.os.shared.domain.UserId;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un aviso lógico. Todavía no es una notificación: es la afirmación de que se da una condición.
 *
 * @param ancla    fecha que identifica el EPISODIO, no el día en que se detectó. Mientras la
 *                 condición siga sin resolverse el ancla no se mueve, así que la clave tampoco
 *                 y no se crea otro aviso (P-06: "resolver la condición antes de crear otro").
 * @param magnitud días sin actividad, o cantidad de evidencias vencidas. Para el texto.
 */
public record AvisoDeAcompanamiento(UserId mentorId, UserId alumnoId, UUID grupoId, MotivoAviso motivo,
                                     LocalDate ancla, int magnitud) {

    /**
     * Clave estable de deduplicación, derivada de destinatario, alumno, motivo y episodio.
     *
     * <p>Se usa como {@code origenEventoId}, que ya tiene índice único en {@code notificaciones}
     * (V16). Es determinista a propósito: dos corridas del job que ven la misma condición
     * calculan el mismo UUID, y la segunda no inserta nada. No hace falta consultar antes ni
     * confiar en un check-then-insert que pierde carreras.
     *
     * <p>Incluye al mentor: cuando rota, el entrante recibe su propio aviso y el del saliente
     * queda como historia en vez de desaparecer.
     */
    public UUID claveDeDeduplicacion() {
        String semilla = mentorId + "|" + alumnoId + "|" + motivo + "|" + ancla;
        return UUID.nameUUIDFromBytes(semilla.getBytes(StandardCharsets.UTF_8));
    }
}
