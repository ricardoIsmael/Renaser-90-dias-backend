package com.renaser.os.academy.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Copia PROPIA de `academy` del patron documentado en
 * `docs/MODULO_PHASECONTRACTS.md` §2 y replicado por `rocks`
 * ({@code ConsultarProgresoParticipanteRocksPort}): en vez de importar
 * internals de `users`, cada modulo lee `participantes_programa`+`usuarios`
 * con su propia query nativa y su propio enum local `RolParticipante`.
 *
 * <p><b>Diferencia deliberada con `rocks`/`phasecontracts` — AC-04:</b> alli
 * el JOIN es INNER porque esos modulos son exclusivos de TRAINEE (sin fila
 * de `participantes_programa` no hay nada que hacer). Academy es accesible
 * para los 5 roles (`requireRole(... ['TRAINEE','MENTOR','MENTOR_LEAD','ADMIN','ALCHEMIST'])`
 * en el repo viejo), y la fila de `participantes_programa` es OPCIONAL para
 * todo rol que no sea TRAINEE (CLAUDE.MD, comentario de la tabla en el
 * baseline). Por eso {@code diaPrograma}/{@code zona} son nullable: un
 * MENTOR sin inscripcion en el programa sigue pudiendo ver el catalogo, solo
 * que sin gate de dia (que de todos modos no le aplica).
 */
public interface ConsultarProgresoParticipanteAcademyPort {

    Optional<ProgresoParticipanteAcademy> deParticipante(UserId participanteId);

    record ProgresoParticipanteAcademy(Integer diaPrograma, ZoneId zona, RolParticipante rol, boolean suspendido) {
    }

    /** Espejo LOCAL (a este modulo) del enum Postgres `rol_usuario`. */
    enum RolParticipante {
        ALCHEMIST,
        ADMIN,
        MENTOR_LEAD,
        MENTOR,
        TRAINEE
    }
}
