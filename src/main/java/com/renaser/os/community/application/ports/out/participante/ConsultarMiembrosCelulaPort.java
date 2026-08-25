package com.renaser.os.community.application.ports.out.participante;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Copia PROPIA de `community` sobre `participantes_programa` (dueno futuro: `users`,
 * mismo criterio que {@link ConsultarCelulaDeParticipantePort}) — el sentido contrario:
 * dada una celula, quienes son sus miembros. Solo el UUID: nombre/avatar los resuelve
 * {@code ConsultarPerfilUsuarioPort} (otra tabla, `usuarios`); coherencia/puntos viven en
 * `puntajes_participante`, tabla de `points`, fuera de alcance de este puerto
 * (docs/MODULO_COMMUNITY.md sec. 6).
 */
public interface ConsultarMiembrosCelulaPort {

    List<UserId> deCelula(CelulaId celulaId);

    int contarMiembros(CelulaId celulaId);
}
