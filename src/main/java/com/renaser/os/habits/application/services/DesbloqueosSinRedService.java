package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase.CambiarEstadoHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.DesbloqueosSinRedUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.QuitarHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.QuitarHabitoDelPlanUseCase.QuitarHabitoCommand;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.sinred.UltimaEdicionDelPlanDeHabitosPort;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.EntregaSinRed;
import com.renaser.os.shared.application.EntregaSinRed.Aplicado;
import com.renaser.os.shared.application.EntregaSinRed.Envio;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.RevisionDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Elegir, pausar o sacar un hábito del plan, hecho sin red (spec 048, fase 3, FR-019). Las reglas de {@code DesbloqueoHabitoService} corridas con la hora
 * real; aquí solo se decide si se aplica: si el hábito cambió en el servidor DESPUÉS de lo que hizo la persona, gana el servidor
 * ({@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}, D13). Un hábito que no existe o ya no está en el plan es {@code RECURSO_YA_NO_EXISTE}; sacar del plan lo
 * que ya no está se aplica sin más.
 */
@Service
class DesbloqueosSinRedService implements DesbloqueosSinRedUseCase {

    private static final String TIPO = "HABITO_DESBLOQUEO";

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    private final UltimaEdicionDelPlanDeHabitosPort ultima;
    private final ElegirHabitoUseCase elegir;
    private final CambiarEstadoHabitoDelPlanUseCase cambiarEstado;
    private final QuitarHabitoDelPlanUseCase quitar;
    private final EntregaSinRed entrega;
    private final Clock clock;

    DesbloqueosSinRedService(ConsultarProgresoParticipanteHabitsPort progresoPort, LoadHabitoPort loadHabitoPort,
                             LoadDesbloqueoHabitoPort loadDesbloqueoPort, UltimaEdicionDelPlanDeHabitosPort ultima,
                             ElegirHabitoUseCase elegir, CambiarEstadoHabitoDelPlanUseCase cambiarEstado,
                             QuitarHabitoDelPlanUseCase quitar, EnviosSinRedPort envios, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadDesbloqueoPort = loadDesbloqueoPort;
        this.ultima = ultima;
        this.elegir = elegir;
        this.cambiarEstado = cambiarEstado;
        this.quitar = quitar;
        this.entrega = new EntregaSinRed(envios, clock);
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public DesbloqueoHabito elegir(ElegirHabitoCommand command, HechoSinRed sinRed) {
        AccesoAlPlanDeHabitos.exigir(progresoPort, command.actorId());
        Envio envio = new Envio(command.actorId(), sinRed, TIPO, "habito:" + command.habitoId().value());
        return entrega.entregar(envio, () -> {
            RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
            exigirElHabito(command.habitoId());
            exigirSinCambioPosterior(command.actorId(), command.habitoId(), hora);
            return Aplicado.de(elegir.elegir(command, hora.referencia()));
        }, previo -> delPlan(command.actorId(), command.habitoId()));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public DesbloqueoHabito cambiarEstado(CambiarEstadoHabitoCommand command, HechoSinRed sinRed) {
        AccesoAlPlanDeHabitos.exigir(progresoPort, command.actorId());
        Envio envio = new Envio(command.actorId(), sinRed, TIPO, "habito:" + command.habitoId().value());
        return entrega.entregar(envio, () -> {
            RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
            exigirElHabito(command.habitoId());
            delPlan(command.actorId(), command.habitoId());
            exigirSinCambioPosterior(command.actorId(), command.habitoId(), hora);
            return Aplicado.de(cambiarEstado.cambiarEstado(command, hora.referencia()));
        }, previo -> delPlan(command.actorId(), command.habitoId()));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public void quitar(QuitarHabitoCommand command, HechoSinRed sinRed) {
        AccesoAlPlanDeHabitos.exigir(progresoPort, command.actorId());
        Envio envio = new Envio(command.actorId(), sinRed, TIPO, "habito:" + command.habitoId().value());
        entrega.entregar(envio, () -> {
            RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
            if (loadHabitoPort.byId(command.habitoId()).isEmpty()
                    || loadDesbloqueoPort.deParticipanteYHabito(command.actorId(), command.habitoId()).isEmpty()) {
                return Aplicado.de(Boolean.TRUE);
            }
            exigirSinCambioPosterior(command.actorId(), command.habitoId(), hora);
            quitar.quitar(command);
            return Aplicado.de(Boolean.TRUE);
        }, previo -> Boolean.TRUE);
    }

    /** El hábito tiene que estar en el plan de la persona: si no, ya no existe lo que tocaba. */
    private DesbloqueoHabito delPlan(UserId actor, HabitoId habito) {
        return loadDesbloqueoPort.deParticipanteYHabito(actor, habito)
                .orElseThrow(() -> new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));
    }

    private void exigirElHabito(HabitoId habito) {
        if (loadHabitoPort.byId(habito).isEmpty()) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE);
        }
    }

    private void exigirSinCambioPosterior(UserId actor, HabitoId habito, RevisionDeLaHora hora) {
        if (ultima.delDesbloqueo(actor, habito).filter(hora::hayCambioMasNuevo).isPresent()) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
    }
}
