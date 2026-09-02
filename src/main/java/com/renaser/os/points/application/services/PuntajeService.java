package com.renaser.os.points.application.services;

import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.api.ResumenAjustePuntos;
import com.renaser.os.points.application.ports.in.puntaje.AjustarPuntosManualmenteUseCase;
import com.renaser.os.points.application.ports.in.puntaje.AjustarPuntosUseCase;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.points.application.ports.in.puntaje.RegistrarCoherenciaDiariaUseCase;
import com.renaser.os.points.application.ports.out.ajuste.SaveAjustePort;
import com.renaser.os.points.application.ports.out.puntaje.LoadPuntajePort;
import com.renaser.os.points.application.ports.out.puntaje.SaveHistorialCoherenciaPort;
import com.renaser.os.points.application.ports.out.puntaje.SavePuntajePort;
import com.renaser.os.points.application.ports.out.puntaje.VerificarActorAdministrativoPort;
import com.renaser.os.points.domain.model.ajuste.AjustePuntos;
import com.renaser.os.points.domain.model.ajuste.ResultadoAjuste;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class PuntajeService implements AjustarPuntosUseCase, AjustarPuntosManualmenteUseCase, ConsultarPuntajeUseCase,
        RegistrarCoherenciaDiariaUseCase, AjustarPuntosPort {

    private final LoadPuntajePort loadPuntajePort;
    private final SavePuntajePort savePuntajePort;
    private final SaveAjustePort saveAjustePort;
    private final SaveHistorialCoherenciaPort saveHistorialCoherenciaPort;
    private final VerificarActorAdministrativoPort verificarActorAdministrativoPort;
    private final UserSummaryFinder userSummaryFinder;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final Clock clock;

    public PuntajeService(LoadPuntajePort loadPuntajePort, SavePuntajePort savePuntajePort,
                           SaveAjustePort saveAjustePort, SaveHistorialCoherenciaPort saveHistorialCoherenciaPort,
                           VerificarActorAdministrativoPort verificarActorAdministrativoPort,
                           UserSummaryFinder userSummaryFinder,
                           ParticipacionProgramaFinder participacionProgramaFinder, Clock clock) {
        this.loadPuntajePort = loadPuntajePort;
        this.savePuntajePort = savePuntajePort;
        this.saveAjustePort = saveAjustePort;
        this.saveHistorialCoherenciaPort = saveHistorialCoherenciaPort;
        this.verificarActorAdministrativoPort = verificarActorAdministrativoPort;
        this.userSummaryFinder = userSummaryFinder;
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AjustePuntos ajustar(AjustarPuntosCommand command) {
        return aplicarAjuste(command.participanteId(), command.motivo(), command.delta(), command.nota());
    }

    @Override
    @Transactional
    public AjustePuntos ajustarManualmente(AjustarPuntosManualmenteCommand command) {
        if (!verificarActorAdministrativoPort.esAdministrativoActivo(command.actorId())) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST activos hacen ajustes manuales de puntos");
        }
        // MotivoPuntos forzado server-side: el comando publico no acepta un motivo
        // arbitrario (mismo blindaje que SubmitAccountRequestCommand sin campo role).
        return aplicarAjuste(command.participanteId(), MotivoPuntos.MANUAL_ADJUSTMENT, command.delta(),
                command.nota());
    }

    @Override
    @Transactional
    public ResumenAjustePuntos ajustar(UserId participanteId, MotivoPuntos motivo, int delta, String nota) {
        AjustePuntos ajuste = aplicarAjuste(participanteId, motivo, delta, nota);
        return new ResumenAjustePuntos(participanteId, ajuste.deltaAplicado(), ajuste.saldoPosterior());
    }

    @Override
    public PuntajeParticipante consultar(UserId actorId, UserId participanteId) {
        boolean esElMismo = actorId.equals(participanteId);
        if (!esElMismo && !verificarActorAdministrativoPort.esAdministrativoActivo(actorId)) {
            throw new NotAuthorizedException("Solo el propio participante o un administrativo pueden ver este puntaje");
        }
        // Al administrativo ya lo valida esAdministrativoActivo (exige ACTIVO); falta la
        // misma capa 3 de defensa cuando el actor consulta su propio puntaje.
        if (esElMismo && !requireActive(actorId)) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return loadPuntajePort.byParticipanteId(participanteId)
                .orElseGet(() -> PuntajeParticipante.inicial(participanteId, clock));
    }

    private boolean requireActive(UserId actorId) {
        return userSummaryFinder.findById(actorId)
                .map(resumen -> resumen.status() == UserStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    @Transactional
    public void registrar(RegistrarCoherenciaDiariaCommand command) {
        PuntajeParticipante puntaje = cargarOInicializar(command.participanteId());

        puntaje.actualizarCoherencia(command.valor(), clock);
        saveHistorialCoherenciaPort.upsert(command.participanteId(), command.fecha(), command.valor());

        boolean correspondeBono = puntaje.actualizarRachaTrasDia(command.diaHabitosPerfecto(), clock);
        savePuntajePort.save(puntaje);

        if (correspondeBono) {
            aplicarAjusteSobre(puntaje, MotivoPuntos.STREAK_BONUS, PuntajeParticipante.RACHA_BONO_PUNTOS,
                    "Bono de racha: " + puntaje.rachaActual() + " dias perfectos consecutivos");
        }
    }

    private AjustePuntos aplicarAjuste(UserId participanteId, MotivoPuntos motivo, int delta, String nota) {
        PuntajeParticipante puntaje = cargarOInicializar(participanteId);
        return aplicarAjusteSobre(puntaje, motivo, delta, nota);
    }

    /** Aplica el ajuste sobre un agregado YA cargado (evita pisar cambios de racha/coherencia hechos antes en la misma transacción). */
    private AjustePuntos aplicarAjusteSobre(PuntajeParticipante puntaje, MotivoPuntos motivo, int delta,
                                             String nota) {
        ResultadoAjuste resultado = puntaje.registrarAjuste(delta, clock);
        savePuntajePort.save(puntaje);

        AjustePuntos ajuste = AjustePuntos.registrar(puntaje.participanteId(), motivo, resultado, nota, clock);
        return saveAjustePort.save(ajuste);
    }

    private PuntajeParticipante cargarOInicializar(UserId participanteId) {
        return loadPuntajePort.byParticipanteIdParaEscritura(participanteId)
                .orElseGet(() -> crearFilaYCargarParaEscritura(participanteId));
    }

    /**
     * C-12: {@code FOR UPDATE} no bloquea una fila que todavia no existe, asi que dos
     * ajustes concurrentes sobre un participante recien inscrito (ej. sus dos primeros
     * habitos completados casi a la vez) llegaban aca los dos, cada uno con un
     * {@code PuntajeParticipante.inicial(...)} en memoria, y el segundo {@code save()}
     * (merge) violaba la PK de {@code puntajes_participante} -> 409 en el primer habito
     * del programa. Se resuelve con INSERT ... ON CONFLICT DO NOTHING (idempotente, nunca
     * lanza) y una relectura con el mismo bloqueo pesimista de siempre: quien pierde la
     * carrera de creacion simplemente ve la fila que ya gano, la bloquea y aplica su ajuste
     * arriba — ningun punto se pierde, solo se serializa.
     */
    private PuntajeParticipante crearFilaYCargarParaEscritura(UserId participanteId) {
        requireInscrito(participanteId);
        savePuntajePort.crearFilaInicialSiFalta(PuntajeParticipante.inicial(participanteId, clock));
        return loadPuntajePort.byParticipanteIdParaEscritura(participanteId)
                .orElseThrow(() -> new IllegalStateException(
                        "Fila de puntaje no encontrada tras asegurar su existencia: " + participanteId));
    }

    /**
     * `puntajes_participante.participante_id` referencia `participantes_programa(usuario_id)`
     * — sin esta verificacion, el primer ajuste a alguien sin fila de programa reventaba
     * con un 500 (violacion de FK) en vez de un 404 claro.
     */
    private void requireInscrito(UserId participanteId) {
        boolean inscrito = participacionProgramaFinder.deParticipante(participanteId)
                .map(ParticipacionPrograma::inscrito)
                .orElse(false);
        if (!inscrito) {
            throw new NoSuchElementException("Participante no encontrado: " + participanteId);
        }
    }
}
