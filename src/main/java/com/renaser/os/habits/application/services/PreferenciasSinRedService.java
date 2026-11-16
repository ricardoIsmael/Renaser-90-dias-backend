package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.CambiarEstadoHabitoEnFechaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.ResultadoEdicionPreferencia;
import com.renaser.os.habits.application.ports.in.preferencia.PreferenciasSinRedUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.sinred.UltimaEdicionDelPlanDeHabitosPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * El horario, el aviso y la alarma de un hábito planificados sin red (spec 048, fase 3, FR-019). Las reglas de {@code PreferenciaHorarioService}
 * corridas con la hora real ({@link RevisionDeLaHora#referencia()}): la ventana «ya arrancó» (el cambio rige mañana, D-91), la cuota semanal y la
 * semana libre. Aquí solo se decide si se aplica: si la fila que se toca cambió en el servidor DESPUÉS de lo que hizo la persona, gana el servidor
 * ({@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}); un hábito que ya no existe es {@code RECURSO_YA_NO_EXISTE}, salvo en un {@code DELETE}, que sobre algo
 * que ya no está se aplica sin más.
 *
 * <p>Qué fila toca cada pedido: la hora, el aviso y la alarma <b>generales</b> son {@code preferencias_horario}; los de un <b>día de la semana</b>,
 * {@code horario_semanal_habito}; los de una <b>fecha</b>, {@code horarios_habito_por_fecha}.
 */
@Service
class PreferenciasSinRedService implements PreferenciasSinRedUseCase {

    private static final String TIPO = "HABITO_PREFERENCIA";

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final UltimaEdicionDelPlanDeHabitosPort ultima;
    private final EditarPreferenciaHorarioUseCase preferencias;
    private final CambiarEstadoHabitoEnFechaUseCase estadoDelDia;
    private final EditarHorarioSemanalUseCase semanal;
    private final EntregaSinRed entrega;
    private final Clock clock;

    PreferenciasSinRedService(ConsultarProgresoParticipanteHabitsPort progresoPort, LoadHabitoPort loadHabitoPort,
                              UltimaEdicionDelPlanDeHabitosPort ultima, EditarPreferenciaHorarioUseCase preferencias,
                              CambiarEstadoHabitoEnFechaUseCase estadoDelDia, EditarHorarioSemanalUseCase semanal,
                              EnviosSinRedPort envios, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.ultima = ultima;
        this.preferencias = preferencias;
        this.estadoDelDia = estadoDelDia;
        this.semanal = semanal;
        this.entrega = new EntregaSinRed(envios, clock);
        this.clock = clock;
    }

    /** A qué se le pone la mano: quién, qué hábito, cómo se anota en el rastro y si es un {@code DELETE} (que no rebota si ya no está). */
    private record Toque(UserId actor, HabitoId habito, String recurso, boolean esBorrado) {
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public ResultadoEdicionPreferencia editar(EditarPreferenciaHorarioCommand command, HechoSinRed sinRed) {
        AccesoAlPlanDeHabitos.exigir(progresoPort, command.actorId());
        String recurso = "preferencia:" + command.habitoId().value() + (command.fecha() == null ? "" : ":" + command.fecha());
        Envio envio = new Envio(command.actorId(), sinRed, TIPO, recurso);
        return entrega.entregar(envio, () -> {
            RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
            exigirElHabito(command.habitoId());
            exigirSinCambioPosterior(command.fecha() == null ? ultima.deLaPreferencia(command.actorId(), command.habitoId())
                    : ultima.delasFechas(command.actorId(), command.habitoId(), command.fecha(), command.fecha()), hora);
            return Aplicado.de(preferencias.editar(command, hora.referencia()));
        }, previo -> new ResultadoEdicionPreferencia(command.habitoId(), command.horaDisparo(), command.horaLimite(), true,
                command.fecha(), 0, 0, 0, "WEEK"));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public void cambiarEstadoDelDia(UserId actor, HabitoId habito, LocalDate fecha, boolean activo, HechoSinRed sinRed) {
        tocar(new Toque(actor, habito, "preferencia:" + habito.value() + ":" + fecha, false), sinRed,
                () -> ultima.delasFechas(actor, habito, fecha, fecha),
                referencia -> estadoDelDia.cambiarEstadoEnFecha(actor, habito, fecha, activo, referencia));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public void fijarDiaDeLaSemana(UserId actor, HabitoId habito, DayOfWeek dia, LocalTime horaDisparo, LocalTime horaLimite,
                                   HechoSinRed sinRed) {
        tocar(new Toque(actor, habito, "preferencia:" + habito.value() + ":" + dia, false), sinRed,
                () -> ultima.delDiaDeLaSemana(actor, habito, dia),
                referencia -> semanal.fijar(actor, habito, dia, horaDisparo, horaLimite, referencia));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public void fijarAvisoDelDia(UserId actor, HabitoId habito, DayOfWeek dia, boolean activo, List<Integer> antelaciones,
                                 HechoSinRed sinRed) {
        tocar(new Toque(actor, habito, "preferencia:" + habito.value() + ":" + dia, false), sinRed,
                () -> ultima.delDiaDeLaSemana(actor, habito, dia),
                referencia -> semanal.fijarRecordatorio(actor, habito, dia, activo, antelaciones, referencia));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public void fijarAlarma(UserId actor, HabitoId habito, CombinacionDeAlarma combinacion, HechoSinRed sinRed) {
        tocar(new Toque(actor, habito, "preferencia:" + habito.value(), false), sinRed,
                () -> ultima.deLaPreferencia(actor, habito),
                referencia -> preferencias.fijarCombinacion(actor, habito, combinacion, referencia));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public void fijarAlarmaDelDia(UserId actor, HabitoId habito, DayOfWeek dia, CombinacionDeAlarma combinacion, HechoSinRed sinRed) {
        tocar(new Toque(actor, habito, "preferencia:" + habito.value() + ":" + dia, false), sinRed,
                () -> ultima.delDiaDeLaSemana(actor, habito, dia),
                referencia -> semanal.fijarCombinacion(actor, habito, dia, combinacion, referencia));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public void apagarDiaDeLaSemana(UserId actor, HabitoId habito, DayOfWeek dia, HechoSinRed sinRed) {
        tocar(new Toque(actor, habito, "preferencia:" + habito.value() + ":" + dia, true), sinRed,
                () -> ultima.delDiaDeLaSemana(actor, habito, dia),
                referencia -> semanal.apagar(actor, habito, dia, referencia));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public void quitarDiaDeLaSemana(UserId actor, HabitoId habito, DayOfWeek dia, HechoSinRed sinRed) {
        tocar(new Toque(actor, habito, "preferencia:" + habito.value() + ":" + dia, true), sinRed,
                () -> ultima.delDiaDeLaSemana(actor, habito, dia),
                referencia -> semanal.quitar(actor, habito, dia));
    }

    /** Lo común de los pedidos sin respuesta: la puerta, el candado, el hábito, el choque y aplicar con la hora real. */
    private void tocar(Toque toque, HechoSinRed sinRed, Supplier<Optional<Instant>> filaQueToca, Consumer<Instant> aplicar) {
        AccesoAlPlanDeHabitos.exigir(progresoPort, toque.actor());
        Envio envio = new Envio(toque.actor(), sinRed, TIPO, toque.recurso());
        entrega.entregar(envio, () -> {
            RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
            if (toque.esBorrado() && loadHabitoPort.byId(toque.habito()).isEmpty()) {
                return Aplicado.de(Boolean.TRUE);
            }
            exigirElHabito(toque.habito());
            exigirSinCambioPosterior(filaQueToca.get(), hora);
            aplicar.accept(hora.referencia());
            return Aplicado.de(Boolean.TRUE);
        }, previo -> Boolean.TRUE);
    }

    private void exigirElHabito(HabitoId habito) {
        if (loadHabitoPort.byId(habito).isEmpty()) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE);
        }
    }

    private static void exigirSinCambioPosterior(Optional<Instant> actualizadoEn, RevisionDeLaHora hora) {
        if (actualizadoEn.filter(hora::hayCambioMasNuevo).isPresent()) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
    }
}
