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
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.CuotaEdicionHorario;
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
    public static final int FREE_SCHEDULE_EDITS_UNTIL_DAY = CuotaEdicionHorario.DIAS_DE_ACOMODO_LIBRE;
    /** limits.ts — habitos DISTINTOS reacomodables por semana de programa, pasada la semana libre. */
    public static final int WEEKLY_SCHEDULE_EDIT_LIMIT = CuotaEdicionHorario.HABITOS_POR_SEMANA;

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

        ContextoCuota contexto = resolverContextoCuota(command, habito, progreso.diaPrograma(), zona, ahora);
        aplicarEdicion(command, contexto, ahora, hoy);

        return construirResultado(command, contexto);
    }

    /**
     * Resuelve si el pedido entra en la ventana libre, si hay cupo semanal, y si se difiere a
     * manana. Separado de {@link #editar} para que el caso de uso quede como una lectura de
     * arriba a abajo (§5.4.8: metodo publico corto, cada paso con nombre) en vez de una sola
     * funcion con toda la logica de cuota entremezclada.
     */
    private ContextoCuota resolverContextoCuota(EditarPreferenciaHorarioCommand command, Habito habito,
                                                 int diaPrograma, ZoneId zona, Instant ahora) {
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        boolean semanaLibreGlobal = CuotaEdicionHorario.esSemanaDeAcomodoLibre(diaPrograma);
        int libreHasta = Math.max(FREE_SCHEDULE_EDITS_UNTIL_DAY,
                habito.diaLimiteEdicionLibre() != null ? habito.diaLimiteEdicionLibre() : FREE_SCHEDULE_EDITS_UNTIL_DAY);
        boolean habitoLibre = diaPrograma <= libreHasta;
        VentanaVigenteHoy vigente = resolverVentanaVigenteHoy(command, zona, ahora);

        List<HabitoId> tocados = List.of();
        if (!semanaLibreGlobal) {
            LocalDate inicioSemana = CuotaEdicionHorario.inicioSemanaPrograma(hoy, diaPrograma);
            tocados = historialPort.distintosHabitosCambiadosDesde(command.actorId(), inicioSemana);
            requireCupoDisponible(command.habitoId(), habitoLibre, vigente.yaArranco(), tocados);
        }

        LocalDate fechaEfectiva = vigente.yaArranco() ? hoy.plusDays(1) : null;
        return new ContextoCuota(semanaLibreGlobal, habitoLibre, vigente, fechaEfectiva, tocados);
    }

    private static void requireCupoDisponible(HabitoId habitoId, boolean habitoLibre, boolean diferido,
                                                List<HabitoId> tocados) {
        if (!habitoLibre && !diferido && tocados.size() >= WEEKLY_SCHEDULE_EDIT_LIMIT
                && !tocados.contains(habitoId)) {
            throw new IllegalStateException("Esta semana ya reacomodaste " + WEEKLY_SCHEDULE_EDIT_LIMIT
                    + " habitos. Puedes seguir ajustando esos, y el resto la semana que viene.");
        }
    }

    private void aplicarEdicion(EditarPreferenciaHorarioCommand command, ContextoCuota contexto, Instant ahora,
                                 LocalDate hoy) {
        if (contexto.diferido()) {
            asegurarPreferenciaVigente(command, contexto.ventanaVigente(), ahora);
            CambioHorarioPendiente pendiente = CambioHorarioPendiente.programar(command.actorId(), command.habitoId(),
                    command.horaDisparo(), command.horaLimite(), command.recordatorioActivo(),
                    command.minutosRecordatorio(), contexto.fechaEfectivaDiferido(), ahora);
            saveCambioPendientePort.save(pendiente);
        } else {
            aplicarInmediato(command, ahora);
            saveCambioPendientePort.borrar(command.actorId(), command.habitoId());
            historialPort.registrar(command.actorId(), command.habitoId(), hoy, command.horaDisparo(),
                    command.horaLimite(), ahora);
        }
    }

    /**
     * E-54: `cambios_horario_pendientes` tiene FK compuesta a `preferencias_horario`, y la rama
     * diferida no creaba la fila padre — el primer cambio diferido de un habito nunca editado
     * violaba la FK. Se crea con lo que YA rige hoy, nunca con lo pedido: un cambio diferido no
     * puede tocar el dia en curso ("no se improvisa el dia"). Los valores pedidos los escribe la
     * promocion nocturna ({@code PromocionCambioHorarioService}), el dia que corresponde.
     */
    private void asegurarPreferenciaVigente(EditarPreferenciaHorarioCommand command, VentanaVigenteHoy vigente,
                                             Instant ahora) {
        if (vigente.conPreferenciaPropia()) {
            return;
        }
        savePreferenciaPort.save(PreferenciaHorario.crear(command.actorId(), command.habitoId(),
                vigente.horaDisparo(), vigente.horaLimite(), ahora));
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
     * Lo que rige HOY para este habito: horas vigentes (preferencia propia si la tiene, si no el
     * default del catalogo) y si esa ventana ya arranco — "no se improvisa el dia": con la hora
     * de disparo vigente ya pasada, el cambio se programa para manana en vez de aplicarse.
     * Sin registro de hoy no hay ventana que respetar, asi que nunca se difiere.
     */
    private VentanaVigenteHoy resolverVentanaVigenteHoy(EditarPreferenciaHorarioCommand command, ZoneId zona,
                                                         Instant ahora) {
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        Optional<PreferenciaHorario> preferencia = loadPreferenciaPort.porParticipanteYHabito(command.actorId(),
                command.habitoId());
        Optional<RegistroHabito> registroDeHoy = loadRegistroPort.porParticipanteHabitoYFecha(command.actorId(),
                command.habitoId(), hoy);
        HorarioHabito catalogo = registroDeHoy.map(r -> horarioDeCatalogoVigente(command.habitoId(), r)).orElse(null);

        LocalTime horaDisparo = primeraNoNula(preferencia.map(PreferenciaHorario::horaDisparo).orElse(null),
                catalogo != null ? catalogo.horaDisparo() : null);
        LocalTime horaLimite = primeraNoNula(preferencia.map(PreferenciaHorario::horaLimite).orElse(null),
                catalogo != null ? catalogo.horaLimite() : null);
        boolean yaArranco = registroDeHoy.isPresent() && horaDisparo != null
                && !ahora.atZone(zona).toLocalTime().isBefore(horaDisparo);
        return new VentanaVigenteHoy(horaDisparo, horaLimite, yaArranco, preferencia.isPresent());
    }

    private HorarioHabito horarioDeCatalogoVigente(HabitoId habitoId, RegistroHabito registroDeHoy) {
        return loadHorarioPort.porHabito(habitoId).stream()
                .filter(h -> h.aplicaEnDia(registroDeHoy.diaPrograma(), registroDeHoy.tipoDia()))
                .findFirst().orElse(null);
    }

    private static LocalTime primeraNoNula(LocalTime dePreferencia, LocalTime deCatalogo) {
        return dePreferencia != null ? dePreferencia : deCatalogo;
    }

    /** Sin hora de cierre no hay orden que validar: el habito no vence dentro del dia. */
    private static void requireOrdenHorario(LocalTime horaDisparo, LocalTime horaLimite) {
        if (horaLimite == null) {
            return;
        }
        if (!horaDisparo.isBefore(horaLimite)) {
            throw new IllegalArgumentException("horaLimite debe ser posterior a horaDisparo");
        }
    }

    /**
     * Cuota informativa — simplificacion documentada: a diferencia del repo viejo, no excluye
     * de {@code cambiosUsados} a OTROS habitos que hoy tengan su propia ventana extendida
     * (`readExtendedFreeWindows`) — solo se resuelve la ventana extendida DEL habito que se
     * esta editando ({@code habitoLibre}). Un cambio DIFERIDO tampoco suma aca: recien cobra
     * cupo el dia que pasa a regir (ver {@code PromocionCambioHorarioService}).
     * Ver docs/MODULO_HABITS.md.
     */
    private static ResultadoEdicionPreferencia construirResultado(EditarPreferenciaHorarioCommand command,
                                                                    ContextoCuota contexto) {
        int usados;
        if (contexto.semanaLibreGlobal() || contexto.diferido() || contexto.habitoLibre()) {
            usados = contexto.tocados().size();
        } else {
            Set<HabitoId> conjunto = new LinkedHashSet<>(contexto.tocados());
            conjunto.add(command.habitoId());
            usados = conjunto.size();
        }
        CuotaEdicionHorario cuota = CuotaEdicionHorario.de(usados, contexto.semanaLibreGlobal());
        return new ResultadoEdicionPreferencia(command.habitoId(), command.horaDisparo(), command.horaLimite(),
                contexto.diferido(), contexto.fechaEfectivaDiferido(), cuota.usados(), cuota.restantes(),
                cuota.limite(), cuota.periodo());
    }

    /**
     * Agrupa lo que {@link #resolverContextoCuota} resuelve para no repartir 7 parametros
     * sueltos entre {@link #aplicarEdicion} y {@link #construirResultado} (§5.4.8, techo de 4
     * parametros por metodo). {@code diaPrograma} no viaja aca: solo lo usa el propio calculo
     * de cuota, ningun consumidor de este record lo necesita.
     */
    private record ContextoCuota(boolean semanaLibreGlobal, boolean habitoLibre, VentanaVigenteHoy ventanaVigente,
                                  LocalDate fechaEfectivaDiferido, List<HabitoId> tocados) {

        boolean diferido() {
            return ventanaVigente.yaArranco();
        }
    }

    /** {@code conPreferenciaPropia}: si ya existe fila en `preferencias_horario` (la padre de la FK). */
    private record VentanaVigenteHoy(LocalTime horaDisparo, LocalTime horaLimite, boolean yaArranco,
                                      boolean conPreferenciaPropia) {
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
