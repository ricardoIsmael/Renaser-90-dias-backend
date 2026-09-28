package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasAgendadasUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.FechasPlanificables;
import com.renaser.os.rocks.domain.model.rocasemanal.EstadoPlazo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * {@code GET /api/v1/rocks/upcoming} (D-217). Clase propia y no un metodo mas de
 * {@code RocaDiariaService}, que ya pasa el techo de 300 lineas.
 *
 * <p>El rango sale de {@link FechasPlanificables}, la misma regla que acepta o rechaza agendar: asi la
 * lista cubre exactamente los dias en los que puede haber algo agendado, ni uno menos (una accion sin
 * alarma) ni uno mas (una consulta inutil). Hoy se incluye siempre —con la ventana nocturna abierta ya
 * no se agenda hoy, pero lo agendado antes sigue necesitando su alarma—, y por eso se pasa
 * {@link EstadoPlazo#EN_PLAZO}: solo se usa el {@code hasta}, que no depende del plazo.
 */
@Service
public class RocasAgendadasService implements ConsultarRocasAgendadasUseCase {

    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final LoadRocaDiariaPort loadRocaDiariaPort;
    private final Clock clock;

    public RocasAgendadasService(ConsultarProgresoParticipanteRocksPort progresoPort,
                                  LoadRocaDiariaPort loadRocaDiariaPort, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadRocaDiariaPort = loadRocaDiariaPort;
        this.clock = clock;
    }

    @Override
    public RocasAgendadas agendadas(UserId actorId) {
        ProgresoParticipanteRocks progreso = AccesoARocas.exigir(progresoPort, actorId);
        LocalDate hoy = clock.now().atZone(progreso.zona()).toLocalDate();
        LocalDate hasta = hastaCuando(progreso, hoy);
        return new RocasAgendadas(hoy, hasta, loadRocaDiariaPort.deParticipanteEntreFechas(actorId, hoy, hasta));
    }

    /** Sin Dia 1 elegido no hay semanas: mañana, como {@code /tomorrow}. Pasado el dia 90, solo hoy. */
    private static LocalDate hastaCuando(ProgresoParticipanteRocks progreso, LocalDate hoy) {
        LocalDate hasta = progreso.semanas(hoy)
                .map(semanas -> FechasPlanificables.para(hoy, EstadoPlazo.EN_PLAZO, semanas).hasta())
                .orElse(hoy.plusDays(1));
        return hasta.isBefore(hoy) ? hoy : hasta;
    }
}
