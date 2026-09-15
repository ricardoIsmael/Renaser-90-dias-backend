package com.renaser.os.rag.infrastructure.adapter.out.participante;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Delega en el contrato publico de {@code users} (D-41): {@code participantes_programa} es tabla
 * de {@code users}, no de {@code rag}.
 *
 * <p>Solo responde por quien esta INSCRITO. Un mentor o un administrador que conversa con el
 * agente no tiene dia de programa, y devolver uno inventado seria peor que no devolver nada.
 */
@Component
class ConsultarSituacionDelAprendizAdapter implements ConsultarSituacionDelAprendizPort {

    private final ParticipacionProgramaFinder participacionFinder;

    ConsultarSituacionDelAprendizAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public Optional<SituacionDelAprendiz> de(UserId participanteId) {
        return participacionFinder.deParticipante(participanteId)
                .filter(ParticipacionPrograma::inscrito)
                .map(p -> new SituacionDelAprendiz(p.diaPrograma(), numeroDeFase(p.diaPrograma())));
    }

    /**
     * La fase sale del DIA, nunca de la columna guardada — el javadoc de
     * {@link FasePrograma#paraDiaPrograma(int)} lo pide explicitamente por el bug D-66 (dos filas
     * en dia 17 con fases distintas).
     *
     * <p>El {@code switch} es exhaustivo y explicito en vez de {@code ordinal() + 1}: reordenar
     * las constantes del enum no puede cambiar en silencio el numero que ve la persona.
     */
    private static int numeroDeFase(int diaPrograma) {
        return switch (FasePrograma.paraDiaPrograma(diaPrograma)) {
            case PHASE_1_REBIRTH -> 1;
            case PHASE_2_DEVELOPMENT -> 2;
            case PHASE_3_ALCHEMIST_WARRIOR -> 3;
            case PHASE_4_ASCENSION -> 4;
        };
    }
}
