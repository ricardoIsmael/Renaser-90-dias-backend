package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SaveCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Hueco #12 — edicion de horario personal (`preferencias_horario`/`cambios_horario_pendientes`).
 * Traduccion SIMPLIFICADA de {@code updateHabitPreference} (repo viejo, service.ts:2021) — ver
 * javadoc de {@link EditarPreferenciaHorarioUseCase} para lo que quedo afuera.
 */
@Service
public class PreferenciaHorarioService implements EditarPreferenciaHorarioUseCase {

    /** limits.ts — semana 1 de acomodo, sin cupo. */
    public static final int FREE_SCHEDULE_EDITS_UNTIL_DAY = 7;
    /** limits.ts — habitos DISTINTOS reacomodables por semana de programa, pasada la semana libre. */
    public static final int WEEKLY_SCHEDULE_EDIT_LIMIT = 3;

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final SavePreferenciaHorarioPort savePreferenciaPort;
    private final SaveCambioHorarioPendientePort saveCambioPendientePort;
    private final HistorialCambioHorarioPort historialPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final Clock clock;

    public PreferenciaHorarioService(ConsultarProgresoParticipanteHabitsPort progresoPort,
                                      LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadHorarioPort,
                                      LoadPreferenciaHorarioPort loadPreferenciaPort,
                                      SavePreferenciaHorarioPort savePreferenciaPort,
                                      SaveCambioHorarioPendientePort saveCambioPendientePort,
                                      HistorialCambioHorarioPort historialPort,
                                      LoadRegistroHabitoPort loadRegistroPort, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.savePreferenciaPort = savePreferenciaPort;
        this.saveCambioPendientePort = saveCambioPendientePort;
        this.historialPort = historialPort;
        this.loadRegistroPort = loadRegistroPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ResultadoEdicionPreferencia editar(EditarPreferenciaHorarioCommand command) {
        ProgresoParticipanteHabits progreso = requireProgreso(command.actorId());
        Habito habito = requireHabito(command.habitoId());
        requireOrdenHorario(command.horaDisparo(), command.horaLimite());

        ZoneId zona = ZoneId.of(progreso.timezone());
        Instant ahora = clock.now();
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        int diaPrograma = progreso.diaPrograma();

        boolean semanaLibreGlobal = diaPrograma <= FREE_SCHEDULE_EDITS_UNTIL_DAY;
        int libreHasta = Math.max(FREE_SCHEDULE_EDITS_UNTIL_DAY,
                habito.diaLimiteEdicionLibre() != null ? habito.diaLimiteEdicionLibre() : FREE_SCHEDULE_EDITS_UNTIL_DAY);
        boolean habitoLibre = diaPrograma <= libreHasta;
        boolean diferido = ventanaDeHoyYaArranco(command.actorId(), command.habitoId(), zona, ahora, hoy);

        List<HabitoId> tocados = List.of();
        if (!semanaLibreGlobal) {
            LocalDate inicioSemana = inicioSemanaPrograma(hoy, diaPrograma);
            tocados = historialPort.distintosHabitosCambiadosDesde(command.actorId(), inicioSemana);
            if (!habitoLibre && !diferido && tocados.size() >= WEEKLY_SCHEDULE_EDIT_LIMIT
                    && !tocados.contains(command.habitoId())) {
                throw new IllegalStateException("Esta semana ya reacomodaste " + WEEKLY_SCHEDULE_EDIT_LIMIT
                        + " habitos. Puedes seguir ajustando esos, y el resto la semana que viene.");
            }
        }

        LocalDate fechaEfectiva = null;
        if (diferido) {
            fechaEfectiva = hoy.plusDays(1);
            CambioHorarioPendiente pendiente = CambioHorarioPendiente.programar(command.actorId(), command.habitoId(),
                    command.horaDisparo(), command.horaLimite(), command.recordatorioActivo(),
                    command.minutosRecordatorio(), fechaEfectiva, ahora);
            saveCambioPendientePort.save(pendiente);
        } else {
            aplicarInmediato(command, ahora);
            saveCambioPendientePort.borrar(command.actorId(), command.habitoId());
            historialPort.registrar(command.actorId(), command.habitoId(), hoy, command.horaDisparo(),
                    command.horaLimite(), ahora);
        }

        return construirResultado(command, diaPrograma, semanaLibreGlobal, habitoLibre, diferido, fechaEfectiva,
                tocados);
    }

    private void aplicarInmediato(EditarPreferenciaHorarioCommand command, Instant ahora) {
        PreferenciaHorario pref = loadPreferenciaPort.porParticipanteYHabito(command.actorId(), command.habitoId())
                .orElseGet(() -> PreferenciaHorario.crear(command.actorId(), command.habitoId(), command.horaDisparo(),
                        command.horaLimite(), ahora));
        pref.aplicarAhora(command.horaDisparo(), command.horaLimite(), ahora);
        pref.actualizarRecordatorio(command.recordatorioActivo(), command.minutosRecordatorio(), ahora);
        savePreferenciaPort.save(pref);
    }

    /**
     * "No se improvisa el dia": si la ventana de HOY de este habito ya arranco (la hora de
     * disparo vigente ya paso), el cambio no rige hoy — se programa para manana.
     */
    private boolean ventanaDeHoyYaArranco(UserId participanteId, HabitoId habitoId, ZoneId zona, Instant ahora,
                                           LocalDate hoy) {
        Optional<RegistroHabito> registro = loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habitoId,
                hoy);
        if (registro.isEmpty()) {
            return false;
        }
        RegistroHabito r = registro.get();
        LocalTime horaDisparo = resolverHoraDisparoVigente(participanteId, habitoId, r.diaPrograma(), r.tipoDia());
        if (horaDisparo == null) {
            return false;
        }
        return !ahora.atZone(zona).toLocalTime().isBefore(horaDisparo);
    }

    /** Preferencia actual (previa a este pedido) si tiene hora propia; si no, el default del catalogo. */
    private LocalTime resolverHoraDisparoVigente(UserId participanteId, HabitoId habitoId, int diaPrograma,
                                                  TipoDia tipoDia) {
        Optional<PreferenciaHorario> pref = loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId);
        if (pref.isPresent() && pref.get().horaDisparo() != null) {
            return pref.get().horaDisparo();
        }
        return loadHorarioPort.porHabito(habitoId).stream().filter(h -> h.aplicaEnDia(diaPrograma, tipoDia))
                .findFirst().map(HorarioHabito::horaDisparo).orElse(null);
    }

    /** programWeekStart(today, programDay) del repo viejo: la semana de programa arranca offset dias atras. */
    private static LocalDate inicioSemanaPrograma(LocalDate hoy, int diaPrograma) {
        int offset = (Math.max(diaPrograma, 1) - 1) % 7;
        return hoy.minusDays(offset);
    }

    private static void requireOrdenHorario(LocalTime horaDisparo, LocalTime horaLimite) {
        if (!horaDisparo.isBefore(horaLimite)) {
            throw new IllegalArgumentException("horaLimite debe ser posterior a horaDisparo");
        }
    }

    /**
     * Cuota informativa — simplificacion documentada: a diferencia del repo viejo, no excluye
     * de {@code cambiosUsados} a OTROS habitos que hoy tengan su propia ventana extendida
     * (`readExtendedFreeWindows`) — solo se resuelve la ventana extendida DEL habito que se
     * esta editando ({@code habitoLibre}). Ver docs/MODULO_HABITS.md.
     */
    private static ResultadoEdicionPreferencia construirResultado(EditarPreferenciaHorarioCommand command,
                                                                    int diaPrograma, boolean semanaLibreGlobal,
                                                                    boolean habitoLibre, boolean diferido,
                                                                    LocalDate fechaEfectivaDiferido,
                                                                    List<HabitoId> tocados) {
        String periodo = semanaLibreGlobal ? "FREE" : "WEEK";
        int usados;
        if (semanaLibreGlobal || diferido || habitoLibre) {
            usados = tocados.size();
        } else {
            Set<HabitoId> conjunto = new LinkedHashSet<>(tocados);
            conjunto.add(command.habitoId());
            usados = conjunto.size();
        }
        int restantes = Math.max(0, WEEKLY_SCHEDULE_EDIT_LIMIT - usados);
        return new ResultadoEdicionPreferencia(command.habitoId(), command.horaDisparo(), command.horaLimite(),
                diferido, fechaEfectivaDiferido, usados, restantes, WEEKLY_SCHEDULE_EDIT_LIMIT, periodo);
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
