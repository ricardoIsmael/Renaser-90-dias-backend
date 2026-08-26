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
    /** Solo el dia 0 — antes de que el programa arranque (renameableKeys.ts). */
    public static final int RENAME_ALLOWED_UNTIL_PROGRAM_DAY = 0;

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
        ProgresoParticipanteHabits progreso = requireProgreso(command.actorId());
        Habito habito = requireRenombrable(command.habitoId(), progreso.diaPrograma());

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
        ProgresoParticipanteHabits progreso = requireProgreso(command.actorId());
        requireRenombrable(command.habitoId(), progreso.diaPrograma());
        savePort.borrar(command.actorId(), command.habitoId());
    }

    private Habito requireRenombrable(HabitoId habitoId, int diaPrograma) {
        Habito habito = requireHabito(habitoId);
        if (habito.claveSistema() == null || !CLAVES_RENOMBRABLES.contains(habito.claveSistema())) {
            throw new IllegalArgumentException("Este habito no se puede reemplazar");
        }
        if (diaPrograma > RENAME_ALLOWED_UNTIL_PROGRAM_DAY) {
            throw new IllegalStateException(
                    "Solo puedes reemplazarlo antes de que empiece tu formacion (hasta el dia "
                            + RENAME_ALLOWED_UNTIL_PROGRAM_DAY + ")");
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
