package com.renaser.os.community.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.community.application.ports.out.participante.ConsultarCelulaDeParticipantePort;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Delega en el contrato publico de `users` (D-41). La celula a la que pertenece alguien
 * vive en `participantes_programa.celula_id`, tabla de `users`.
 *
 * <p>{@code celulaId} llega en {@code null} tanto si el usuario no esta inscrito al
 * programa como si lo esta pero todavia no tiene celula asignada: para esta pregunta
 * ambos casos son lo mismo — no pertenece a ninguna celula — y se responde vacio, igual
 * que antes.
 */
@Component
class ConsultarCelulaDeParticipantePersistenceAdapter implements ConsultarCelulaDeParticipantePort {

    private final ParticipacionProgramaFinder participacionFinder;

    ConsultarCelulaDeParticipantePersistenceAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public Optional<CelulaId> celulaDeUsuario(UserId usuarioId) {
        return participacionFinder.deParticipante(usuarioId)
                .map(ParticipacionPrograma::celulaId)
                .map(CelulaId::of);
    }
}
