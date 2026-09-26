package com.renaser.os.rag.application.ports.in.conversacion;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * Lo que el acompanante sabe de la persona al empezar un turno (o una llamada en vivo): dia, fase,
 * fecha y, desde D-176, el estado de sus habitos de hoy.
 *
 * <p>Es la {@code situacion} de {@code ConsultarSituacionDelAprendizPort} mas los habitos. No se
 * agrego al puerto porque los habitos son de otro modulo y ya tienen sus puertos propios en
 * {@code rag} (los que usan las herramientas): componerlos es trabajo de la aplicacion, no de un
 * adaptador.
 */
public interface ConsultarSituacionDelTurnoUseCase {

    /**
     * Vacio si no cursa el programa, igual que el puerto. Si no se pudieron leer los habitos, la
     * situacion llega igual, con {@code habitos() == null}: el turno nunca se cae por esto.
     */
    Optional<SituacionDelAprendiz> de(UserId participanteId);
}
