package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Pone a un mentor al frente de un grupo <b>sin sacarlo de los que ya lidera</b>.
 *
 * <p><b>Por qué existe, si ya estaba {@link AsignarMentorCelulaUseCase}.</b> Porque ese otro hace
 * lo contrario a propósito, y lo hacía en silencio: su {@code asignar} llama a
 * {@code cerrarMentorEnOtrosGrupos}, que cierra la asignación del mentor en todos sus demás
 * grupos, deja a cada uno <b>sin mentor</b> y pone en null el puntero de mentor de todos sus
 * aprendices. Como traslado está bien —es lo que hay que hacer cuando un mentor se MUEVE— pero
 * quien solo quería darle un segundo grupo se quedaba sin el primero y sin ningún error que se lo
 * dijera.
 *
 * <p>Decisión del dueño del producto (2026-09-17): un mentor puede liderar varios grupos a la vez.
 * Eso no es un parámetro del traslado — es otra operación, y por eso entra por su propio caso de
 * uso, su propio endpoint y su propia clave de idempotencia. El traslado queda exactamente como
 * estaba. Es el mismo reparto que D-139 dejó para el aprendiz un día antes
 * ({@link SumarAprendizAGrupoUseCase}).
 *
 * <p><b>Lo que NO cambia:</b> un grupo sigue teniendo un solo mentor. Sumar un mentor a un grupo
 * que ya tiene otro se rechaza igual que siempre — para eso está el traslado, o quitar primero al
 * que estaba. La base lo repite desde V45 con {@code asignaciones_un_mentor_por_celula}, que V58
 * conserva.
 */
public interface SumarMentorAGrupoUseCase {

    /** Proyeccion del grupo DESTINO dentro de la misma transaccion (CLAUDE.MD sec. 5.4.6). */
    CelulaDetalle sumar(SumarMentorAGrupoCommand command);

    record SumarMentorAGrupoCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId,
                                     @NotNull UserId mentorId) {

        public SumarMentorAGrupoCommand {
            SelfValidating.validateConstructorArgs(SumarMentorAGrupoCommand.class, actorId, celulaId, mentorId);
        }
    }
}
