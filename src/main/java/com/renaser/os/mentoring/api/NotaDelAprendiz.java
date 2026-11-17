package com.renaser.os.mentoring.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Una nota privada sobre un aprendiz, tal como la ven quienes pueden leerla (spec 055): el acompañante vigente, el liderazgo de su
 * formación y Administración. Nunca el aprendiz.
 *
 * @param puedeCorregir true solo para su autor y solo el mismo día en que la escribió (en la zona del autor); siempre false para el
 *                      liderazgo y para Administración, que solo leen
 * @param corregidoEn   null mientras nadie la corrigió
 */
public record NotaDelAprendiz(UUID id, String texto, Autor autor, Instant creadoEn, Instant corregidoEn,
                              boolean puedeCorregir) {

    /** @param nombre el nombre completo de quien la escribió; null si su cuenta ya no se encuentra */
    public record Autor(UUID id, String nombre) {
    }
}
