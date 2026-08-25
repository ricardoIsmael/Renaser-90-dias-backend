package com.renaser.os.community.application.ports.out.participante;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * Copia PROPIA de `community` del patron documentado en
 * `rocks/application/ports/out/participante/ConsultarProgresoParticipanteRocksPort` —
 * en vez de importar el detalle de `participantes_programa` (tabla cuyo dueno futuro es
 * `users`, CLAUDE.MD sec. "Ojo con cohortes/celulas"), este modulo lee la columna que
 * necesita con su propia query nativa.
 *
 * <p>Solo la celula: a diferencia de `rocks`, `community` no necesita dia de programa ni
 * fecha de inicio, y el rol/estado del actor se resuelve con
 * {@code users.api.UserSummaryFinder} (puerto publico sancionado, CLAUDE.MD).
 */
public interface ConsultarCelulaDeParticipantePort {

    Optional<CelulaId> celulaDeUsuario(UserId usuarioId);
}
