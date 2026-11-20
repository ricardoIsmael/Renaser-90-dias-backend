package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Los grupos que el mentor ya no acompaña (spec 057, FR-020 y FR-021), para «Mis grupos → Anteriores». Autoconsulta y solo lectura: sale de
 * los tramos del propio actor y no lleva ids de personas ni ningún dato de hoy de ellas (el mentor saliente conserva su resumen, no el
 * expediente: P-07).
 */
public interface ConsultarHistorialDeGruposUseCase {

    /** Del más reciente al más viejo; vacío si no tiene grupos anteriores. */
    HistorialDeGrupos historialDe(UserId actorId);

    record HistorialDeGrupos(List<GrupoAnterior> grupos) {
    }

    /**
     * @param desde           el primer día que lo acompañó
     * @param hasta           el último día que lo acompañó (el fin del grupo si el grupo terminó con él)
     * @param aprendices      cuántas personas pasaron por el grupo mientras lo acompañó
     * @param puestoFinal     su puesto en el ranking del último mes del tramo; null si ese mes no tuvo muestra
     * @param colorFinal      {@code VERDE}, {@code AMARILLO}, {@code ROJO} o {@code SIN_DATOS}: el de la última semana cerrada DENTRO del tramo
     * @param porcentajeFinal el promedio de esa semana; null si no hubo semana cerrada dentro del tramo
     * @param personas        solo nombre y color de esa semana, sin ids
     */
    record GrupoAnterior(UUID grupoId, String nombre, LocalDate desde, LocalDate hasta, int aprendices, PuestoFinal puestoFinal,
                         String colorFinal, BigDecimal porcentajeFinal, List<PersonaAnterior> personas) {
    }

    record PuestoFinal(int posicion, int total) {
    }

    record PersonaAnterior(String nombre, String colorFinal) {
    }
}
