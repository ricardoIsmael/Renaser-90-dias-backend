package com.renaser.os.calendar.application.ports.in.audiencia;

import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * «Para quién es» un evento, con sus cifras (spec 044, contrato §3). Solo lo ven quien lo creó y quien lo gestiona (FR-018).
 *
 * @param total las personas que hoy están en la audiencia, cada una una vez (sin quien lo creó)
 */
public record ResumenDeAudiencia(List<Parte> partes, int total) {

    /**
     * @param id       el grupo ({@link TipoDeParte#GRUPO}); {@code null} en las demás
     * @param nombre   el nombre del grupo, o {@code null}
     * @param personas las personas de una parte por nombre (personas o personal); vacía en las demás
     */
    public record Parte(TipoDeParte tipo, UUID id, String nombre, ColorDeAudiencia color, int cuantos, Instant resueltoEn,
                        List<PersonaConvocable> personas) {
    }
}
