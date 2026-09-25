package com.renaser.os.rag.application.services.vozenvivo;

import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaVozEnVivoPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarZonaDelParticipantePort;
import com.renaser.os.rag.domain.model.conversacion.CuotaDeVozEnVivo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Los minutos de voz en vivo de una persona (D-162): cuanto le queda hoy y cobrar lo que se hablo.
 *
 * <p><b>"Hoy" es el dia de la persona</b>, con su zona ({@code participantes_programa.timezone}),
 * nunca {@code clock.today()} (regla 02, E-91). A las 03:00 UTC en Lima todavia es el dia anterior:
 * si el contador usara la fecha del servidor, la cuota se renovaria a las 19:00 hora local.
 */
@Service
public class TiempoDeVozEnVivo {

    private final ControlCuotaVozEnVivoPort cuotaPort;
    private final ConsultarZonaDelParticipantePort zonaPort;
    private final CuotaDeVozEnVivo cuota;
    private final Clock clock;

    public TiempoDeVozEnVivo(ControlCuotaVozEnVivoPort cuotaPort, ConsultarZonaDelParticipantePort zonaPort,
                             CuotaDeVozEnVivo cuota, Clock clock) {
        this.cuotaPort = cuotaPort;
        this.zonaPort = zonaPort;
        this.cuota = cuota;
        this.clock = clock;
    }

    public Duration restanteHoy(UserId actorId) {
        return cuota.restante(cuotaPort.usadoEn(actorId, hoyDe(actorId)));
    }

    /** Suma lo hablado al dia de hoy de la persona y devuelve lo que le queda. */
    public Duration cobrar(UserId actorId, Duration tramo) {
        if (tramo.isZero() || tramo.isNegative()) {
            return restanteHoy(actorId);
        }
        return cuota.restante(cuotaPort.sumar(actorId, hoyDe(actorId), tramo));
    }

    public CuotaDeVozEnVivo cuota() {
        return cuota;
    }

    private LocalDate hoyDe(UserId actorId) {
        return CuotaDeVozEnVivo.diaDe(clock.now(), zonaPort.de(actorId));
    }
}
