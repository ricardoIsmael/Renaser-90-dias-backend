package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase;
import com.renaser.os.habits.application.ports.out.habitosaprendiz.LeerHabitosPersonalizadosPort;
import com.renaser.os.habits.application.ports.out.habitosaprendiz.LeerHabitosPersonalizadosPort.FilaHabitoDeAprendiz;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Panel admin — hábitos de UN aprendiz. Solo lectura, cuatro consultas fijas en total
 * (actor, progreso del aprendiz, proyeccion de habitos, cuota), ninguna dentro de un bucle.
 *
 * <p>Reusa piezas que ya existen en vez de duplicarlas: el gate ADMIN/ALCHEMIST es
 * {@link HabitoAdminGuard} (el mismo del admin de catalogo), el contador de la cuota es
 * {@link HistorialCambioHorarioPort} (el mismo que cobra el cupo en
 * {@link PreferenciaHorarioService}) y la precedencia preferencia-sobre-catalogo es la
 * misma que aplican {@code RegistroService}/{@code TracksDelDiaProyeccionService}.
 */
@Service
public class HabitosDeAprendizAdminService implements ConsultarHabitosDeAprendizUseCase {

    private final HabitoAdminGuard guard;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LeerHabitosPersonalizadosPort leerHabitosPort;
    private final HistorialCambioHorarioPort historialPort;
    private final Clock clock;

    HabitosDeAprendizAdminService(HabitoAdminGuard guard, ConsultarProgresoParticipanteHabitsPort progresoPort,
                                   LeerHabitosPersonalizadosPort leerHabitosPort,
                                   HistorialCambioHorarioPort historialPort, Clock clock) {
        this.guard = guard;
        this.progresoPort = progresoPort;
        this.leerHabitosPort = leerHabitosPort;
        this.historialPort = historialPort;
        this.clock = clock;
    }

    @Override
    public VistaHabitosDeAprendiz consultar(ConsultarHabitosDeAprendizCommand command) {
        ProgresoParticipanteHabits progreso = requireAprendiz(command.aprendizId());
        guard.requireAdmin(command.actorId());

        ZoneId zona = ZoneId.of(progreso.timezone());
        LocalDate hoy = clock.now().atZone(zona).toLocalDate();

        List<HabitoDeAprendiz> habitos = leerHabitosPort
                .deAprendiz(command.aprendizId(), progreso.diaPrograma(), tipoDiaDe(hoy), lunesDe(hoy))
                .stream().map(HabitosDeAprendizAdminService::aVista).toList();

        return new VistaHabitosDeAprendiz(command.aprendizId(), progreso.diaPrograma(), hoy, progreso.timezone(),
                cuotaDeLaSemana(command.aprendizId(), progreso.diaPrograma(), hoy), habitos);
    }

    /**
     * El aprendiz se carga PRIMERO y el gate de admin va DESPUES — mismo orden que
     * {@code ParticipacionProgramaService.obtener} (docs/BITACORA_ERRORES.md E-42). El motivo
     * es concreto: {@link HabitoAdminGuard} lanza {@code NoSuchElementException} cuando el
     * ACTOR no existe, y si corriera antes de confirmar el recurso, ese 404 se confundiria
     * con el 404 del aprendiz inexistente. Un actor real sin permiso siempre cae a 403.
     *
     * <p>La suspension del APRENDIZ no se chequea a proposito — un operador tiene que poder
     * auditar justamente a quien acaba de suspender. La del ACTOR si: la cubre el guard.
     */
    private ProgresoParticipanteHabits requireAprendiz(UserId aprendizId) {
        return progresoPort.deParticipante(aprendizId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + aprendizId));
    }

    /**
     * Cuota informativa, contada con el MISMO puerto que la cobra en
     * {@link PreferenciaHorarioService}: habitos DISTINTOS reacomodados desde el inicio de
     * la semana de programa. Es una foto de lo ya gastado, no una simulacion de un cambio
     * nuevo — por eso, a diferencia del autoservicio, aca nunca se suma el habito que se
     * esta editando (no se esta editando ninguno).
     */
    private CuotaCambiosHorario cuotaDeLaSemana(UserId aprendizId, int diaPrograma, LocalDate hoy) {
        List<HabitoId> tocados = historialPort.distintosHabitosCambiadosDesde(aprendizId,
                inicioSemanaPrograma(hoy, diaPrograma));
        int limite = PreferenciaHorarioService.WEEKLY_SCHEDULE_EDIT_LIMIT;
        String periodo = diaPrograma <= PreferenciaHorarioService.FREE_SCHEDULE_EDITS_UNTIL_DAY ? "FREE" : "WEEK";
        return new CuotaCambiosHorario(tocados.size(), Math.max(0, limite - tocados.size()), limite, periodo);
    }

    /** Preferencia del aprendiz si esta seteada; si no, el default del catalogo. */
    private static HabitoDeAprendiz aVista(FilaHabitoDeAprendiz fila) {
        boolean personalizado = fila.horaDisparoPreferencia() != null || fila.horaLimitePreferencia() != null;
        return new HabitoDeAprendiz(fila.habitoId(), fila.tituloCatalogo(), fila.tituloPersonal(), fila.esPersonal(),
                fila.tipo(), fila.categoriaClave(),
                primeroNoNulo(fila.horaDisparoPreferencia(), fila.horaDisparoCatalogo()),
                primeroNoNulo(fila.horaLimitePreferencia(), fila.horaLimiteCatalogo()), personalizado,
                fila.recordatorioActivo(), fila.minutosRecordatorio(), cambioPendienteDe(fila), desbloqueoDe(fila),
                fila.eleccionDiaSemanal(), fila.diaSemanalElegido());
    }

    private static LocalTime primeroNoNulo(LocalTime dePreferencia, LocalTime deCatalogo) {
        return dePreferencia != null ? dePreferencia : deCatalogo;
    }

    /** La FILA de `cambios_horario_pendientes` ES el "hay cambio programado" (P-13). */
    private static CambioHorarioProgramado cambioPendienteDe(FilaHabitoDeAprendiz fila) {
        if (fila.fechaEfectivaPendiente() == null) {
            return null;
        }
        return new CambioHorarioProgramado(fila.horaDisparoPendiente(), fila.horaLimitePendiente(),
                fila.fechaEfectivaPendiente());
    }

    private static DesbloqueoDeAprendiz desbloqueoDe(FilaHabitoDeAprendiz fila) {
        if (fila.diaDesbloqueo() == null) {
            return null;
        }
        return new DesbloqueoDeAprendiz(fila.diaDesbloqueo(), Boolean.TRUE.equals(fila.desbloqueoElegidoPorLaPersona()));
    }

    /** {@code resolverTipoDia} de {@code RegistroService}: domingo es especial, el resto es disciplina. */
    private static TipoDia tipoDiaDe(LocalDate fecha) {
        return fecha.getDayOfWeek() == DayOfWeek.SUNDAY ? TipoDia.DOMINGO : TipoDia.DISCIPLINA;
    }

    /** WEEK_ANCHOR=MONDAY, igual que {@code EleccionDiaSemanalService}. */
    private static LocalDate lunesDe(LocalDate fecha) {
        return fecha.minusDays(fecha.getDayOfWeek().getValue() - 1);
    }

    /**
     * {@code programWeekStart(today, programDay)} — la semana de PROGRAMA (no la de
     * calendario) arranca {@code (diaPrograma-1) % 7} dias atras. Se repite el calculo en
     * vez de exponer el de {@link PreferenciaHorarioService} para no tocar ese archivo
     * (hay trabajo concurrente sobre `preferencia`); son dos lineas y el javadoc deja
     * atada la fuente.
     */
    private static LocalDate inicioSemanaPrograma(LocalDate hoy, int diaPrograma) {
        return hoy.minusDays((Math.max(diaPrograma, 1) - 1) % 7);
    }
}
