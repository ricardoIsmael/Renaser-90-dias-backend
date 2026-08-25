package com.renaser.os.community.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.community.application.ports.out.participante.ConsultarMiembrosCelulaPort;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Delega en el contrato publico de `users` (D-41): `participantes_programa` es de
 * `users`, no de `community`.
 *
 * <p><b>Semantica preservada:</b> aca se listan TODOS los miembros, sin filtrar por
 * estado — a diferencia de `calendar`, que solo quiere destinatarios activos. Listar la
 * composicion de una celula y elegir a quien notificar son dos preguntas distintas: un
 * aprendiz suspendido sigue perteneciendo a su celula. Por eso el contrato expone los
 * dos metodos y este usa {@code miembrosDeCelula}, no {@code miembrosActivosDeCelula}.
 */
@Component
class ConsultarMiembrosCelulaCommunityPersistenceAdapter implements ConsultarMiembrosCelulaPort {

    private final ParticipacionProgramaFinder participacionFinder;

    ConsultarMiembrosCelulaCommunityPersistenceAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public List<UserId> deCelula(CelulaId celulaId) {
        return participacionFinder.miembrosDeCelula(celulaId.value());
    }

    @Override
    public int contarMiembros(CelulaId celulaId) {
        return participacionFinder.contarMiembrosDeCelula(celulaId.value());
    }
}
