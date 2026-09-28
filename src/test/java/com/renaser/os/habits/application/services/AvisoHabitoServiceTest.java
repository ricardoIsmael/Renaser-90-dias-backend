package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.AvisoHabitoDebidoEvent;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.aviso.CalculadoraAvisosHabito;
import com.renaser.os.habits.domain.model.aviso.TipoAvisoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El caso de uso de los avisos automaticos, con la zona real del padron.
 *
 * <p><b>El reloj de estos tests esta a proposito entre 00:00 y 05:00 UTC</b> (regla 03): a esa
 * hora la fecha del servidor y la del aprendiz en Lima son DISTINTAS. Un test con el reloj a las
 * 10:00 UTC pasaria igual con una implementacion que usara {@code clock.today()} en vez de la
 * zona del participante — que es exactamente el bug E-91 — y no probaria nada.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvisoHabitoServiceTest {

    /** 01:50 UTC del 6 = 20:50 del 5 en Lima. La fecha del servidor y la del aprendiz difieren. */
    private static final Instant MADRUGADA_UTC = Instant.parse("2026-09-06T01:50:00Z");
    private static final LocalDate DIA_EN_LIMA = LocalDate.of(2026, 9, 5);
    private static final LocalDate DIA_EN_EL_SERVIDOR = LocalDate.of(2026, 9, 6);

    private static final UserId PARTICIPANTE = UserId.of(UUID.randomUUID());
    private static final HabitoId HABITO = HabitoId.of(UUID.randomUUID());
    private static final String CATEGORIA_DEL_HABITO = "MENTE";
    private static final RegistroHabitoId REGISTRO = RegistroHabitoId.of(UUID.randomUUID());

    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private ApplicationEventPublisher events;

    private AvisoHabitoService servicioCon(Instant ahora) {
        return new AvisoHabitoService(loadRegistroPort, loadHabitoPort, loadHorarioPort, loadPreferenciaPort,
                progresoPort, new CalculadoraAvisosHabito(Duration.ofMinutes(15), Duration.ofMinutes(30)), events,
                FixedClock.at(ahora));
    }

    private void participanteEnLima(boolean suspendido) {
        when(progresoPort.deParticipante(PARTICIPANTE)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, suspendido, false)));
    }

    /** Habito de 21:00 a 22:00 hora de Lima, con un track PENDIENTE del dia local del aprendiz. */
    private void habitoNocturnoPendiente() {
        RegistroHabito registro = RegistroHabito.rehydrate(REGISTRO, PARTICIPANTE, HABITO, DIA_EN_LIMA, 5,
                TipoDia.TODOS, false, EstadoRegistro.PENDIENTE, 0, null, null, null, null, MADRUGADA_UTC,
                MADRUGADA_UTC);
        when(loadRegistroPort.porParticipanteYFecha(PARTICIPANTE, DIA_EN_LIMA)).thenReturn(List.of(registro));
        when(loadRegistroPort.porParticipanteYFecha(PARTICIPANTE, DIA_EN_EL_SERVIDOR)).thenReturn(List.of());
        when(loadHabitoPort.porIds(anyCollection())).thenReturn(List.of(Habito.crearDeSistema(HABITO,
                "Meditacion nocturna", TipoHabito.CHECKBOX, CATEGORIA_DEL_HABITO, ExigenciaEvidencia.OPCIONAL, MADRUGADA_UTC)));
        when(loadHorarioPort.porHabitos(anyCollection())).thenReturn(List.of(
                HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), HABITO, 1, null, TipoDia.TODOS,
                        LocalTime.of(21, 0), LocalTime.of(22, 0), MADRUGADA_UTC)));
        when(loadPreferenciaPort.porParticipanteHabitosYFecha(any(), anyCollection(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("busca los registros del dia del APRENDIZ, no el del servidor (regla 02, E-91)")
    void usaElDiaDelAprendizYNoElDelServidor() {
        participanteEnLima(false);
        habitoNocturnoPendiente();

        servicioCon(MADRUGADA_UTC).despacharDe(PARTICIPANTE);

        verify(loadRegistroPort).porParticipanteYFecha(PARTICIPANTE, DIA_EN_LIMA);
        verify(loadRegistroPort, never()).porParticipanteYFecha(PARTICIPANTE, DIA_EN_EL_SERVIDOR);
    }

    @Test
    @DisplayName("a 10 min del inicio publica el aviso de INICIO con titulo, minutos y puntos en juego")
    void publicaElAvisoDeInicio() {
        participanteEnLima(false);
        habitoNocturnoPendiente();

        int publicados = servicioCon(MADRUGADA_UTC).despacharDe(PARTICIPANTE);

        assertThat(publicados).isEqualTo(1);
        AvisoHabitoDebidoEvent evento = eventoPublicado();
        assertThat(evento.tipoAviso()).isEqualTo(TipoAvisoHabito.INICIO.name());
        assertThat(evento.tituloHabito()).isEqualTo("Meditacion nocturna");
        assertThat(evento.minutosQueFaltan()).isEqualTo(10);
        assertThat(evento.puntosEnJuego()).isEqualTo(10);
        assertThat(evento.participanteId()).isEqualTo(PARTICIPANTE);
        assertThat(evento.registroId()).isEqualTo(REGISTRO.value());
        // D-218: el habito y su categoria viajan para que el toque abra Training en esa dimension.
        assertThat(evento.habitoId()).isEqualTo(HABITO.value());
        assertThat(evento.categoriaHabito()).isEqualTo(CATEGORIA_DEL_HABITO);
    }

    @Test
    @DisplayName("la clave del evento es la deterministica del tipo de aviso: dos corridas no duplican")
    void laClaveDelEventoEsLaDeDeduplicacion() {
        participanteEnLima(false);
        habitoNocturnoPendiente();

        servicioCon(MADRUGADA_UTC).despacharDe(PARTICIPANTE);

        assertThat(eventoPublicado().claveEvento()).isEqualTo(TipoAvisoHabito.INICIO.claveIdempotencia(REGISTRO));
    }

    @Test
    @DisplayName("un aprendiz suspendido no recibe avisos, aunque tenga habitos vivos")
    void elSuspendidoNoRecibeAvisos() {
        participanteEnLima(true);

        assertThat(servicioCon(MADRUGADA_UTC).despacharDe(PARTICIPANTE)).isZero();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("sin progreso (no es aprendiz activo) no se avisa ni se consulta nada mas")
    void sinProgresoNoSeAvisa() {
        when(progresoPort.deParticipante(PARTICIPANTE)).thenReturn(Optional.empty());

        assertThat(servicioCon(MADRUGADA_UTC).despacharDe(PARTICIPANTE)).isZero();
        verify(loadRegistroPort, never()).porParticipanteYFecha(any(), any());
    }

    @Test
    @DisplayName("un registro ya COMPLETADO no genera ningun aviso")
    void elCompletadoNoGeneraAvisos() {
        participanteEnLima(false);
        RegistroHabito completado = RegistroHabito.rehydrate(REGISTRO, PARTICIPANTE, HABITO, DIA_EN_LIMA, 5,
                TipoDia.TODOS, false, EstadoRegistro.COMPLETADO, 10, null, null, null, MADRUGADA_UTC, MADRUGADA_UTC,
                MADRUGADA_UTC);
        when(loadRegistroPort.porParticipanteYFecha(PARTICIPANTE, DIA_EN_LIMA)).thenReturn(List.of(completado));

        assertThat(servicioCon(MADRUGADA_UTC).despacharDe(PARTICIPANTE)).isZero();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("fuera de las dos franjas no se publica nada: el barrido de cada 5 min no hace ruido")
    void fueraDeLaFranjaNoPublicaNada() {
        participanteEnLima(false);
        habitoNocturnoPendiente();
        // 23:00 UTC del 5 = 18:00 en Lima: faltan tres horas para las 21:00.
        when(loadRegistroPort.porParticipanteYFecha(PARTICIPANTE, DIA_EN_LIMA)).thenReturn(
                List.of(RegistroHabito.rehydrate(REGISTRO, PARTICIPANTE, HABITO, DIA_EN_LIMA, 5, TipoDia.TODOS, false,
                        EstadoRegistro.PENDIENTE, 0, null, null, null, null, MADRUGADA_UTC, MADRUGADA_UTC)));

        assertThat(servicioCon(Instant.parse("2026-09-05T23:00:00Z")).despacharDe(PARTICIPANTE)).isZero();
        verify(events, never()).publishEvent(any(Object.class));
    }

    /**
     * D-200, con el reloj en madrugada UTC (20:50 del 5 en Lima): el track es del dia 5 (snapshot
     * real), pero el horario del habito arranca el dia 8. Existe porque el habito ya habia corrido y
     * a la persona la retrocedieron: se lee con el horario de su primer dia, asi que avisa como
     * cualquier otro. Contra el codigo viejo no habia horario vigente, ni ventana, ni aviso.
     */
    @Test
    @DisplayName("D-200: un track generado por debajo del inicio de su horario avisa con la hora de ese horario")
    void unTrackGeneradoPorDebajoDelInicioDeSuHorarioAvisaIgual() {
        participanteEnLima(false);
        habitoNocturnoPendiente();
        when(loadHorarioPort.porHabitos(anyCollection())).thenReturn(List.of(
                HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), HABITO, 8, null, TipoDia.TODOS,
                        LocalTime.of(21, 0), LocalTime.of(22, 0), MADRUGADA_UTC)));

        int publicados = servicioCon(MADRUGADA_UTC).despacharDe(PARTICIPANTE);

        assertThat(publicados).isEqualTo(1);
        assertThat(eventoPublicado().tipoAviso()).isEqualTo(TipoAvisoHabito.INICIO.name());
    }

    // ─── D-184: el recordatorio que el aprendiz configuro para ese habito ─────────────────────

    private void conPreferencia(boolean recordatorioActivo, Integer minutos) {
        when(loadPreferenciaPort.porParticipanteHabitosYFecha(any(), anyCollection(), any())).thenReturn(List.of(
                PreferenciaHorario.rehydrate(PARTICIPANTE, HABITO, LocalTime.of(21, 0), LocalTime.of(22, 0),
                        recordatorioActivo, minutos, MADRUGADA_UTC, MADRUGADA_UTC)));
    }

    @Test
    @DisplayName("D-184: con recordatorio de 30 min, a las 20:35 de Lima (01:35 UTC) ya sale el aviso de inicio")
    void usaLaAntelacionDelRecordatorioDelHabito() {
        participanteEnLima(false);
        habitoNocturnoPendiente();
        conPreferencia(true, 30);

        int publicados = servicioCon(Instant.parse("2026-09-06T01:35:00Z")).despacharDe(PARTICIPANTE);

        assertThat(publicados).isEqualTo(1);
        AvisoHabitoDebidoEvent evento = eventoPublicado();
        assertThat(evento.tipoAviso()).isEqualTo(TipoAvisoHabito.INICIO.name());
        assertThat(evento.minutosQueFaltan()).isEqualTo(25);
        assertThat(evento.recordatorioActivo()).isTrue();
        assertThat(evento.minutosRecordatorio()).isEqualTo(30);
    }

    @Test
    @DisplayName("D-184: recordatorio apagado -> el evento sale igual, con el dato, para que notifications no empuje")
    void recordatorioApagadoViajaEnElEvento() {
        participanteEnLima(false);
        habitoNocturnoPendiente();
        conPreferencia(false, null);

        servicioCon(MADRUGADA_UTC).despacharDe(PARTICIPANTE);

        AvisoHabitoDebidoEvent evento = eventoPublicado();
        assertThat(evento.recordatorioActivo()).isFalse();
        assertThat(evento.minutosRecordatorio()).isNull();
    }

    @Test
    @DisplayName("D-184: sin preferencia el evento lleva null (nunca configurado), no 'apagado'")
    void sinPreferenciaViajaNull() {
        participanteEnLima(false);
        habitoNocturnoPendiente();

        servicioCon(MADRUGADA_UTC).despacharDe(PARTICIPANTE);

        assertThat(eventoPublicado().recordatorioActivo()).isNull();
    }

    private AvisoHabitoDebidoEvent eventoPublicado() {
        ArgumentCaptor<AvisoHabitoDebidoEvent> captor = ArgumentCaptor.forClass(AvisoHabitoDebidoEvent.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }
}
