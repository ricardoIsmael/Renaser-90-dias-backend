package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.QuitarHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.desbloqueo.SaveDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Hueco #12 — lectura del plan de desbloqueo escalonado (ver javadoc de
 * {@link ConsultarDesbloqueosHabitoUseCase} para lo que sigue sin portarse, D-H2) MAS el alta
 * autoservicio simple de {@link ElegirHabitoUseCase}: el aprendiz agrega un habito del catalogo
 * a su plan. Esto ultimo NO es el algoritmo de escalonamiento por lotes del repo viejo.
 */
@Service
public class DesbloqueoHabitoService implements ConsultarDesbloqueosHabitoUseCase, ElegirHabitoUseCase,
        CambiarEstadoHabitoDelPlanUseCase, QuitarHabitoDelPlanUseCase {

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadDesbloqueoHabitoPort loadPort;
    private final SaveDesbloqueoHabitoPort savePort;
    private final LoadHabitoPort loadHabitoPort;
    private final Clock clock;

    public DesbloqueoHabitoService(ConsultarProgresoParticipanteHabitsPort progresoPort,
                                    LoadDesbloqueoHabitoPort loadPort, SaveDesbloqueoHabitoPort savePort,
                                    LoadHabitoPort loadHabitoPort, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.loadHabitoPort = loadHabitoPort;
        this.clock = clock;
    }

    @Override
    public PlanDesbloqueo consultar(UserId actorId) {
        requireProgreso(actorId);
        List<ItemDesbloqueo> items = loadPort.deParticipante(actorId).stream()
                .map(d -> new ItemDesbloqueo(d.habitoId(), d.diaDesbloqueo(), d.elegidoEn())).toList();
        return new PlanDesbloqueo(!items.isEmpty(), items);
    }

    /**
     * Idempotente (ver javadoc de {@link SaveDesbloqueoHabitoPort#elegirSiFalta}): elegir dos
     * veces el mismo habito no falla ni duplica, simplemente devuelve el mismo resultado.
     */
    @Override
    @Transactional
    public DesbloqueoHabito elegir(ElegirHabitoCommand command) {
        ProgresoParticipanteHabits progreso = requireProgreso(command.actorId());
        if (progreso.diaPrograma() == 0) {
            throw new NotAuthorizedException(
                    "El Dia 0 es una vista previa — podras elegir habitos a partir del Dia 1");
        }
        if (progreso.diaPrograma() > 90) {
            throw new IllegalArgumentException("El dia de programa esta fuera del rango de desbloqueo (1-90)");
        }
        Habito habito = requireHabito(command.habitoId());
        if (!habito.esDeSistema()) {
            throw new IllegalArgumentException("Solo se eligen habitos del catalogo, no habitos personales");
        }
        if (!habito.activo()) {
            throw new IllegalArgumentException("Este habito no esta activo en el catalogo");
        }

        Instant ahora = clock.now();
        savePort.elegirSiFalta(command.actorId(), command.habitoId(), progreso.diaPrograma(), ahora, ahora);
        return loadPort.deParticipanteYHabito(command.actorId(), command.habitoId())
                .orElseThrow(() -> new IllegalStateException(
                        "Desbloqueo no encontrado tras asegurar su existencia: " + command.habitoId()));
    }

    /**
      * D-87: el interruptor ACTIVO/PAUSADO de Plan, que hasta ahora no tenia backend al que
      * llamar. Escribe `desbloqueos_habito.pausado_en` — SOLO de este aprendiz —, nunca
      * `habitos.activo`, que es del catalogo compartido y es de admin.
      */
    @Override
    @Transactional
    public DesbloqueoHabito cambiarEstado(CambiarEstadoHabitoCommand command) {
        requireProgreso(command.actorId());
        Habito habito = requireHabito(command.habitoId());
        DesbloqueoHabito desbloqueo = loadPort.deParticipanteYHabito(command.actorId(), command.habitoId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Este habito no esta en tu plan: " + command.habitoId()));

        if (command.activo()) {
            desbloqueo.reactivar(clock.now());
        } else {
            desbloqueo.pausar(habito.desactivable(), clock.now());
        }
        return savePort.save(desbloqueo);
    }

    /**
     * D-87: faltaba la contraparte de {@link #elegir} — se podia agregar al plan pero no sacar,
     * asi que un plan solo crecia. Idempotente a proposito: quitar algo que no esta no es un
     * error, es el estado que el llamador queria.
     */
    @Override
    @Transactional
    public void quitar(QuitarHabitoCommand command) {
        requireProgreso(command.actorId());
        Habito habito = requireHabito(command.habitoId());
        if (!habito.desactivable()) {
            throw new IllegalStateException("Este habito es obligatorio y no se puede sacar de tu plan");
        }
        savePort.borrar(command.actorId(), command.habitoId());
    }

    private Habito requireHabito(HabitoId id) {
        return loadHabitoPort.byId(id).orElseThrow(() -> new NoSuchElementException("Habito no encontrado: " + id));
    }

    private ProgresoParticipanteHabits requireProgreso(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }
}
