package com.renaser.os.rocks.application.ports.out.medicion;

import com.renaser.os.shared.domain.UserId;

/**
 * Que declaro la persona en el Mapa sobre como se mide cada objetivo. El puerto dice <b>que
 * necesito</b>, no de donde sale: hoy lo resuelve {@code onboarding.api.MedicionDelMapaFinder}, y
 * si manana el Mapa tuviera tablas propias, cambia el adaptador y nada mas.
 *
 * <p>El tipo que viaja es propio de {@code rocks} por la misma razon por la que
 * {@code ConsultarProgresoParticipanteRocksPort} tiene su {@code RolParticipante} local: la capa de
 * aplicacion no importa tipos de otro modulo, ni siquiera de su {@code api}.
 */
public interface ConsultarMedicionDelMapaPort {

    /** Nunca {@code null}: sin Mapa recorrido vienen todos los campos vacios. */
    MedicionDelParticipante deParticipante(UserId participanteId);

    /**
     * Texto crudo del Mapa, sin interpretar. {@code null} en lo que la persona no haya contestado.
     *
     * @param relacionesBase de 1 a 10; el unico lugar donde vive el punto de partida de Relaciones,
     *                       que no viaja a {@code rocas_maestras} como meta cuantitativa.
     */
    record MedicionDelParticipante(String saludTipo, String saludUnidad, String negocioTipo, String negocioPeriodo,
                                    Integer relacionesBase, Integer relacionesMeta) {
    }
}
