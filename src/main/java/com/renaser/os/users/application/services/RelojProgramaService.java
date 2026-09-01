package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.participante.ActivateProgramUseCase;
import com.renaser.os.users.application.ports.in.participante.AvanzarDiaProgramaUseCase;
import com.renaser.os.users.application.ports.in.participante.ConsultarActivacionProgramaUseCase;
import com.renaser.os.users.application.ports.out.participante.ListarParticipantesConProgramaActivoPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * D-66: el reloj del programa de 90 dias — separado de {@link ParticipacionProgramaService}
 * (que ya esta en el techo de 300 lineas de CLAUDE.MD §5.4.8) porque ademas es una
 * responsabilidad genuinamente distinta: activar/consultar son self-service del propio
 * aprendiz, y el avance masivo es el cron nocturno — ninguno de los dos es "administrar
 * el perfil de otro participante" como el resto de esa clase.
 */
@Service
public class RelojProgramaService
        implements ActivateProgramUseCase, ConsultarActivacionProgramaUseCase, AvanzarDiaProgramaUseCase {

    /** Tamaño de pagina del barrido nocturno — ni tan chico que sean miles de
     * round-trips a la base, ni tan grande que cargue medio padron a memoria de una. */
    private static final int TAMANO_LOTE = 500;

    private final RequireActiveUserGuard requireActiveUserGuard;
    private final LoadParticipacionProgramaPort loadParticipacionProgramaPort;
    private final SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    private final ListarParticipantesConProgramaActivoPort listarParticipantesConProgramaActivoPort;
    private final Clock clock;

    public RelojProgramaService(RequireActiveUserGuard requireActiveUserGuard,
                                 LoadParticipacionProgramaPort loadParticipacionProgramaPort,
                                 SaveParticipacionProgramaPort saveParticipacionProgramaPort,
                                 ListarParticipantesConProgramaActivoPort listarParticipantesConProgramaActivoPort,
                                 Clock clock) {
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.loadParticipacionProgramaPort = loadParticipacionProgramaPort;
        this.saveParticipacionProgramaPort = saveParticipacionProgramaPort;
        this.listarParticipantesConProgramaActivoPort = listarParticipantesConProgramaActivoPort;
        this.clock = clock;
    }

    /**
     * Self-only (el comando no recibe un id ajeno). Requiere que la fila de
     * `participantes_programa` ya exista — la crea {@code ApproveAccountRequestUseCase}
     * al aprobar la cuenta (CLAUDE.MD §5.3.3), esto solo la activa.
     */
    @Override
    @Transactional
    public ParticipacionPrograma activarPrograma(ActivateProgramCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        ParticipacionPrograma participacion = requireParticipacionDe(actor.id());
        participacion.activarPrograma(command.startDate(), clock);
        return saveParticipacionProgramaPort.save(participacion);
    }

    @Override
    public EstadoActivacionPrograma consultarEstado(ConsultarActivacionProgramaQuery query) {
        User actor = requireActiveUserGuard.of(query.actorId());
        ParticipacionPrograma participacion = requireParticipacionDe(actor.id());
        if (participacion.estaActivado()) {
            return new EstadoActivacionPrograma(true, List.of());
        }
        return new EstadoActivacionPrograma(false, participacion.opcionesDeActivacion(clock));
    }

    /**
     * Cron nocturno (docs/MODULO_PHASECONTRACTS.md §0.2, bloqueante cerrado por D-66).
     * Sin {@code @Transactional} a proposito: cada {@code save()} de Spring Data corre en
     * su propia transaccion implicita, asi que un fallo a mitad del barrido deja lo ya
     * guardado guardado — el propio chequeo de idempotencia de
     * {@code avanzarDiaDelPrograma} hace seguro reintentar sin duplicar avances.
     */
    @Override
    public ResultadoAvance avanzarParticipantesActivos() {
        int evaluados = 0;
        int avanzados = 0;
        int offset = 0;
        List<ParticipacionPrograma> lote;
        do {
            lote = listarParticipantesConProgramaActivoPort.pagina(offset, TAMANO_LOTE);
            for (ParticipacionPrograma participacion : lote) {
                evaluados++;
                LocalDate hoyEnSuZona = clock.now().atZone(participacion.timezone()).toLocalDate();
                if (participacion.avanzarDiaDelPrograma(hoyEnSuZona, clock)) {
                    saveParticipacionProgramaPort.save(participacion);
                    avanzados++;
                }
            }
            offset += TAMANO_LOTE;
        } while (lote.size() == TAMANO_LOTE);
        return new ResultadoAvance(evaluados, avanzados);
    }

    private ParticipacionPrograma requireParticipacionDe(UserId usuarioId) {
        return loadParticipacionProgramaPort.byParticipanteId(usuarioId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No tenes una inscripcion al programa de 90 dias: " + usuarioId));
    }
}
