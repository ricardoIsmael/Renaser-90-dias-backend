package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * La ruta de una carrera con GPS leída desde otro módulo (spec 042, FR-041): la ficha del mentor o la guía ({@code mentoring}) y la
 * del líder, Administración y Alquimista ({@code leadership}). <b>No autoriza a nadie</b>: quien lo llama ya comprobó que puede ver
 * a esa persona con sus reglas de siempre (el acompañante vigente del grupo, {@code AccesoDeLiderazgo}, el aislamiento de
 * formaciones), igual que {@code onboarding.api.DiagnosticoDeLaPersonaFinder}.
 *
 * <p>Deja constancia de cada lectura ajena: publica {@link RutaDeCarreraConsultadaEvent} en su transacción, y {@code leadership} lo
 * anota en {@code consultas_de_ficha}. Solo si había una ruta que mostrar: un 404 no enseña nada.
 */
public interface RutaDeCarreraFinder {

    /** Vacío si esa persona no tiene esa carrera, si no guardó la ruta o si ya se borró (180 días). */
    Optional<RutaLeida> rutaDe(UserId persona, UUID carreraId, LectorDeLaRuta lector);

    /** La polilínea codificada (algoritmo de Google), ya sin los primeros ni los últimos 200 m, y cuántos puntos tiene. */
    record RutaLeida(String polyline, int puntos) {
    }

    /**
     * Quién la abre y desde dónde.
     *
     * @param grupoId  el grupo desde el que se la miró; {@code null} si no pasó por un grupo
     * @param mentorId el mentor vigente de ese grupo; {@code null} si no tenía
     */
    record LectorDeLaRuta(UserId consultorId, UUID grupoId, UserId mentorId) {

        public LectorDeLaRuta {
            Objects.requireNonNull(consultorId, "consultorId es obligatorio");
        }
    }
}
