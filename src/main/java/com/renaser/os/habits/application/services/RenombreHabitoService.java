package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.renombre.QuitarRenombreHabitoUseCase;
import com.renaser.os.habits.application.ports.in.renombre.RenombrarHabitoUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.renombre.LoadRenombreHabitoPort;
import com.renaser.os.habits.application.ports.out.renombre.SaveRenombreHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.renombre.RenombreHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * "Reemplazo de las bebidas" (tabla `renombres_habito`) — traduccion de {@code renameHabit}/
 * {@code clearHabitRename} (repo viejo, renameableKeys.ts). Emparejado por
 * {@code claveSistema}, NUNCA por titulo — el titulo es editable y renombrar el catalogo no
 * debe hacer desaparecer la funcionalidad en silencio.
 */
@Service
public class RenombreHabitoService implements RenombrarHabitoUseCase, QuitarRenombreHabitoUseCase {

    /** JUGO VERDE / AGUA TIBIA CON LIMON — las dos bebidas, renameableKeys.ts. */
    public static final List<String> CLAVES_RENOMBRABLES = List.of("GREEN_JUICE", "WARM_LEMON_WATER");
    /**
     * <b>Se puede reemplazar cualquier dia del programa</b> (decision del dueño, 2026-09-15, D-127).
     *
     * <p>Antes habia una ventana: {@code RENAME_ALLOWED_UNTIL_PROGRAM_DAY = 0}, portada de
     * `renameableKeys.ts` del repo viejo — solo antes de que el programa arrancara. El motivo por
     * el que existe este renombre la desmiente: las dos bebidas se reemplazan porque hay personas
     * que <b>no las toleran</b> (gastritis, reflujo, diabetes), y eso no se descubre el dia 0, se
     * descubre tomandolas. Con la ventana cerrada, a quien reaccionaba el dia 12 le quedaban 78
     * dias de incumplir un habito que no podia hacer — y tampoco podia deshacer un reemplazo
     * puesto, porque {@code quitar} miraba la misma ventana.
     *
     * <p>Lo que NO cambia: sigue siendo individual (nadie mas ve el cambio), sigue limitado a las
     * dos claves de {@link #CLAVES_RENOMBRABLES}, y sigue exigiendo un motivo escrito, asi que
     * queda registrado quien lo cambio y por que.
     */

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadRenombreHabitoPort loadPort;
    private final SaveRenombreHabitoPort savePort;
    private final Clock clock;

    public RenombreHabitoService(ConsultarProgresoParticipanteHabitsPort progresoPort, LoadHabitoPort loadHabitoPort,
                                  LoadRenombreHabitoPort loadPort, SaveRenombreHabitoPort savePort, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RenombreHabito renombrar(RenombrarHabitoCommand command) {
        requireProgreso(command.actorId());
        Habito habito = requireRenombrable(command.habitoId());

        Instant ahora = clock.now();
        Optional<RenombreHabito> existente = loadPort.porParticipanteYHabito(command.actorId(), command.habitoId());
        RenombreHabito renombre = existente.orElseGet(() -> RenombreHabito.crear(command.actorId(),
                command.habitoId(), command.tituloPersonal(), command.motivo(), ahora));
        if (existente.isPresent()) {
            renombre.actualizar(command.tituloPersonal(), command.motivo(), ahora);
        }
        return savePort.save(renombre);
    }

    @Override
    @Transactional
    public void quitar(QuitarRenombreHabitoCommand command) {
        requireProgreso(command.actorId());
        requireRenombrable(command.habitoId());
        savePort.borrar(command.actorId(), command.habitoId());
    }

    /**
     * Que el habito sea de los reemplazables. {@code requireProgreso} sigue corriendo antes en los
     * dos casos de uso —es el que rechaza a una cuenta suspendida y al que no esta inscripto—,
     * pero su dia de programa ya no se mira: ver el javadoc de {@link #CLAVES_RENOMBRABLES}.
     */
    private Habito requireRenombrable(HabitoId habitoId) {
        Habito habito = requireHabito(habitoId);
        if (habito.claveSistema() == null || !CLAVES_RENOMBRABLES.contains(habito.claveSistema())) {
            throw new IllegalArgumentException("Este habito no se puede reemplazar");
        }
        return habito;
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
