package com.renaser.os.users.application.ports.out.participante;

import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

import java.util.List;

/**
 * D-66: pagina de participantes con el reloj del programa ACTIVADO (`programaActivadoEn`
 * no nulo), para que {@code AvanzarDiaProgramaUseCase} pueda recorrer miles de filas sin
 * traerlas todas a memoria de una sola vez. Framework-agnostico a proposito (offset/limit
 * planos, no {@code Pageable} de Spring Data) — mismo criterio que
 * {@link ConsultarResumenParticipacionPort#listarAprendices(int, int)}.
 */
public interface ListarParticipantesConProgramaActivoPort {

    /**
     * @param offset debe ser multiplo de {@code limit} (el llamador solo avanza pagina a
     *               pagina: 0, limit, 2*limit, ...)
     * @param limit  tamaño de la pagina
     */
    List<ParticipacionPrograma> pagina(int offset, int limit);
}
