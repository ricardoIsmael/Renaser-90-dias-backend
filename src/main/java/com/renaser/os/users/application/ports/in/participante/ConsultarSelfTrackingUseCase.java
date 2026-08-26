package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Consulta del propio estado de seguimiento personal ({@code GET /api/v1/mentor/activate-tracking}).
 *
 * <p>Existe como caso de uso propio, en vez de que el controller llame directo a
 * {@code ParticipacionProgramaFinder}, porque ese finder es la API PUBLICA para otros
 * modulos (que ya validaron su propio actor antes de llamar) y por eso no verifica nada.
 * Un controller que lo usaba de atajo dejaba el GET sin la verificacion de actor que sus
 * hermanos POST/DELETE si hacian — un {@code X-Actor-Id} inventado devolvia 200
 * {@code {"active":false}} en vez de 404 (E-38, docs/BITACORA_ERRORES.md).
 */
public interface ConsultarSelfTrackingUseCase {

    /** @return true si el actor tiene seguimiento personal activo. */
    boolean estaActivo(ConsultarSelfTrackingQuery query);

    record ConsultarSelfTrackingQuery(@NotNull UserId actorId) {

        public ConsultarSelfTrackingQuery {
            SelfValidating.validateConstructorArgs(ConsultarSelfTrackingQuery.class, actorId);
        }
    }
}
