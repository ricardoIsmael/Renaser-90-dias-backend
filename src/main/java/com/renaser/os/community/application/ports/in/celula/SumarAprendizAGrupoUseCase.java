package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Suma un aprendiz a un grupo <b>sin sacarlo de los que ya tiene</b>.
 *
 * <p><b>Por qué existe, si ya estaba {@link AsignarAprendizCelulaUseCase}.</b> Porque ese otro
 * hace lo contrario a propósito: es un <i>traslado</i>. Cierra todas las pertenencias vigentes
 * del aprendiz y abre una sola en el destino, que es justo lo que hay que hacer cuando alguien
 * se MUEVE de grupo. Decisión del dueño del producto (2026-09-16, confirmada dos veces): un
 * aprendiz también puede estar en varios grupos a la vez. Eso no es un parámetro del traslado —
 * es otra operación, y por eso entra por su propio caso de uso, su propio endpoint y su propia
 * clave de idempotencia. El traslado queda exactamente como estaba.
 *
 * <p><b>Lo que NO hace:</b> no toca {@code participantes_programa.celula_id} cuando el aprendiz
 * ya tiene grupo. Esa columna es una sola y siete lecturas del producto dependen de ella; sigue
 * nombrando al grupo PRINCIPAL. Solo se estrena cuando está vacía, porque entonces el grupo que
 * se suma ES el primero. Ver {@code SumarAprendizAGrupoService} y D-139.
 */
public interface SumarAprendizAGrupoUseCase {

    /** Proyeccion del grupo DESTINO dentro de la misma transaccion (CLAUDE.MD sec. 5.4.6). */
    CelulaDetalle sumar(SumarAprendizAGrupoCommand command);

    record SumarAprendizAGrupoCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId,
                                       @NotNull UserId traineeId) {

        public SumarAprendizAGrupoCommand {
            SelfValidating.validateConstructorArgs(SumarAprendizAGrupoCommand.class, actorId, celulaId, traineeId);
        }
    }
}
