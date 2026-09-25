package com.renaser.os.users.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

import java.util.Collection;
import java.util.List;

/**
 * Varias participaciones en UNA consulta (D-168): la tabla del semáforo de un grupo pide las fechas
 * del programa de todos sus aprendices a la vez. Un puerto aparte de
 * {@link LoadParticipacionProgramaPort} para no romper a quienes ya lo implementan.
 */
public interface CargarParticipacionesPort {

    /** Los que no tienen fila en {@code participantes_programa} simplemente no aparecen. */
    List<ParticipacionPrograma> deVarios(Collection<UserId> participantes);
}
