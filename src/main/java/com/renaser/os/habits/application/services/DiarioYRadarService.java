package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.DiarioYRadarPort;
import com.renaser.os.habits.application.ports.in.diario.ConsultarBitacoraNocturnaUseCase;
import com.renaser.os.habits.application.ports.in.diario.ConsultarBitacoraNocturnaUseCase.EstadoBitacoraHoy;
import com.renaser.os.habits.application.ports.in.diario.EscribirBitacoraNocturnaUseCase;
import com.renaser.os.habits.application.ports.in.diario.EscribirBitacoraNocturnaUseCase.EscribirBitacoraNocturnaCommand;
import com.renaser.os.habits.application.ports.in.radar.ConsultarUltimoRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Implementa {@link DiarioYRadarPort} (2026-09-23). Fachada delgada, mismo patron que
 * {@link PlanDeHabitosService}: cada operacion es la del caso de uso que ya usa la app
 * ({@link BitacoraNocturnaService} y {@link RadarService}), con todas sus guardas. Aca no se
 * reimplementa ninguna regla:
 * <ul>
 *   <li>"hoy" de la bitacora lo resuelve {@code ConsultarBitacoraNocturnaUseCase};</li>
 *   <li>"ya hay uno en esta hora" se pregunta a {@link RadarService#mismaHora}, la misma que usa
 *       {@code registrar} para devolver el existente;</li>
 *   <li>la hora local se calcula con la zona del participante (regla 02), nunca con la del servidor.</li>
 * </ul>
 *
 * <p>Sin {@code @Transactional}: cada caso de uso trae la suya. Nada de lo que la persona escribio
 * se loguea.
 */
@Service
public class DiarioYRadarService implements DiarioYRadarPort {

    private final ConsultarBitacoraNocturnaUseCase consultarBitacora;
    private final EscribirBitacoraNocturnaUseCase escribirBitacora;
    private final ConsultarUltimoRadarUseCase ultimoRadar;
    private final RegistrarCheckInRadarUseCase registrarRadar;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final Clock clock;

    public DiarioYRadarService(ConsultarBitacoraNocturnaUseCase consultarBitacora,
                               EscribirBitacoraNocturnaUseCase escribirBitacora,
                               ConsultarUltimoRadarUseCase ultimoRadar, RegistrarCheckInRadarUseCase registrarRadar,
                               ConsultarProgresoParticipanteHabitsPort progresoPort, Clock clock) {
        this.consultarBitacora = consultarBitacora;
        this.escribirBitacora = escribirBitacora;
        this.ultimoRadar = ultimoRadar;
        this.registrarRadar = registrarRadar;
        this.progresoPort = progresoPort;
        this.clock = clock;
    }

    @Override
    public BitacoraDeHoy bitacoraDeHoy(UserId participanteId) {
        EstadoBitacoraHoy estado = consultarBitacora.consultarHoy(participanteId);
        return estado.existe() ? bitacoraDe(estado.entrada()) : new BitacoraDeHoy(estado.fecha(), false, null, false);
    }

    @Override
    public BitacoraDeHoy escribirBitacoraDeHoy(UserId actorId, String texto) {
        // Solo texto: el audio sigue el patron upload-url de la app y el acompanante no graba.
        return bitacoraDe(escribirBitacora.escribir(new EscribirBitacoraNocturnaCommand(actorId, texto, null, null)));
    }

    @Override
    public Optional<CheckInRadar> ultimoCheckInRadar(UserId participanteId) {
        Optional<RegistroRadar> ultimo = ultimoRadar.ultimo(participanteId, participanteId);
        if (ultimo.isEmpty()) {
            return Optional.empty();
        }
        RegistroRadar registro = ultimo.get();
        return Optional.of(new CheckInRadar(horaLocal(registro, participanteId),
                RadarService.mismaHora(registro.creadoEn(), clock.now()), respuestasDe(registro)));
    }

    /**
     * El "ya existia" se deriva comparando con el ultimo ANTES de registrar: si el caso de uso
     * devolvio ese mismo registro, las respuestas nuevas no se guardaron.
     */
    @Override
    public CheckInRadarRegistrado registrarCheckInRadar(UserId actorId, RespuestasRadar respuestas) {
        Optional<RegistroRadar> previo = ultimoRadar.ultimo(actorId, actorId);
        RegistroRadar registro = registrarRadar.registrar(new RegistrarCheckInRadarCommand(actorId, actorId,
                respuestas.queHago(), respuestas.quePienso(), respuestas.queSiento(), respuestas.nivelEnergia(),
                respuestas.queEvito()));
        boolean yaExistia = previo.map(anterior -> anterior.id().equals(registro.id())).orElse(false);
        return new CheckInRadarRegistrado(horaLocal(registro, actorId), yaExistia);
    }

    private static BitacoraDeHoy bitacoraDe(EntradaDiario entrada) {
        return new BitacoraDeHoy(entrada.fecha(), true, entrada.contenidoTexto(), entrada.audioRuta() != null);
    }

    private static RespuestasRadar respuestasDe(RegistroRadar registro) {
        return new RespuestasRadar(registro.queHago(), registro.quePienso(), registro.queSiento(),
                registro.nivelEnergia(), registro.queEvito());
    }

    private LocalDateTime horaLocal(RegistroRadar registro, UserId participanteId) {
        ZoneId zona = progresoPort.deParticipante(participanteId)
                .map(progreso -> ZoneId.of(progreso.timezone()))
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        return LocalDateTime.ofInstant(registro.creadoEn(), zona);
    }
}
