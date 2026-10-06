package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Los candidatos del barrido de expiracion (E-534), por paginas: quien tiene algo {@code PENDIENTE} que pudo haber
 * vencido, y desde que fecha. Con la fecha mas vieja de cada uno, el dominio decide en memoria si a esa persona ya se
 * le termino algun dia ({@code CorteDeExpiracion.yaTermino}); solo entonces se leen sus registros.
 */
public interface ConsultarPendientesVencidosPort {

    /**
     * @param tope      solo cuenta lo {@code PENDIENTE} con fecha anterior a esta
     *                  ({@code CorteDeExpiracion.fechaMasTardiaPosible})
     * @param despuesDe cursor: el ultimo participante de la pagina anterior; {@code null} en la primera
     * @param limite    tamaño de la pagina
     * @return ordenados por participante, a lo sumo {@code limite}
     */
    List<PendientesDeParticipante> pagina(LocalDate tope, UserId despuesDe, int limite);

    /** @param masVieja la fecha mas vieja de lo que esa persona tiene {@code PENDIENTE} antes del tope. */
    record PendientesDeParticipante(UserId participanteId, LocalDate masVieja) {
    }
}
