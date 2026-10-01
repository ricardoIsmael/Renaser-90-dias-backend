package com.renaser.os.leadership.application.ports.out;

import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.ConteoDeObservaciones;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LeerObservacionesPort {

    Optional<ObservacionDeMentor> porAutorYClave(UserId autorId, String claveOperacion);

    /** Las más recientes primero; {@code antesDe} null = desde la más nueva. */
    List<ObservacionDeMentor> deMentor(UserId mentorId, Instant antesDe, int limite);

    /** Cuántas de cada tipo recibió cada mentor en {@code [desde, hasta)}. Sin clave = ninguna. */
    Map<UserId, ConteoDeObservaciones> conteoPorMentor(Instant desde, Instant hasta);
}
