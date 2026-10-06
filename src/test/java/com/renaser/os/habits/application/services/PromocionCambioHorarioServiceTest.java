package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SaveCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromocionCambioHorarioServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 05:00 UTC del 25/8: la medianoche de Lima, el instante en que el 25 le empieza a su gente. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T05:00:00Z"));
    private static final LocalDate HOY = LocalDate.of(2026, 8, 25);

    @Mock
    private LoadCambioHorarioPendientePort loadCambioPendientePort;
    @Mock
    private SaveCambioHorarioPendientePort saveCambioPendientePort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private SavePreferenciaHorarioPort savePreferenciaPort;
    @Mock
    private HistorialCambioHorarioPort historialPort;
    @Mock
    private ZonasDelPadron zonas;
    /** Ver comentario equivalente en RegistroServiceTest: no necesita stubbing. */
    @Mock
    private PlatformTransactionManager transactionManager;

    private PromocionCambioHorarioService service;
    private UserId participanteId;
    private HabitoId habitoId;

    @BeforeEach
    void setUp() {
        service = servicioCon(CLOCK);
        participanteId = UserId.of(UUID.randomUUID());
        habitoId = HabitoId.of(UUID.randomUUID());
        lenient().when(savePreferenciaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveCambioPendientePort.borrar(any(), any())).thenReturn(true);
        lenient().when(zonas.leerLote(any())).thenReturn(Map.of());
        lenient().when(zonas.de(any(), any())).thenReturn(LIMA);
    }

    private PromocionCambioHorarioService servicioCon(FixedClock reloj) {
        return new PromocionCambioHorarioService(loadCambioPendientePort, saveCambioPendientePort,
                loadPreferenciaPort, savePreferenciaPort, historialPort, zonas, reloj, transactionManager);
    }

    private CambioHorarioPendiente pendiente(Boolean recordatorioActivo, Integer minutos) {
        return CambioHorarioPendiente.programar(participanteId, habitoId, LocalTime.of(6, 0), LocalTime.of(8, 0),
                recordatorioActivo, minutos, HOY, CLOCK.now());
    }

    @Test
    void sinPendientesNoTocaNada() {
        when(loadCambioPendientePort.queYaRigenEn(HOY)).thenReturn(List.of());

        assertThat(service.promoverLosQueYaRigen()).isZero();

        verify(savePreferenciaPort, never()).save(any());
        verify(historialPort, never()).registrar(any(), any(), any(), any(), any(), any());
        verify(saveCambioPendientePort, never()).borrar(any(), any());
    }

    @Test
    void escribeLasHorasProgramadasEnLaPreferenciaVigente() {
        PreferenciaHorario vigente = PreferenciaHorario.crear(participanteId, habitoId, LocalTime.of(9, 0),
                LocalTime.of(11, 0), Instant.parse("2026-08-24T09:00:00Z"));
        when(loadCambioPendientePort.queYaRigenEn(HOY)).thenReturn(List.of(pendiente(null, null)));
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId)).thenReturn(Optional.of(vigente));

        assertThat(service.promoverLosQueYaRigen()).isEqualTo(1);

        ArgumentCaptor<PreferenciaHorario> guardada = ArgumentCaptor.forClass(PreferenciaHorario.class);
        verify(savePreferenciaPort).save(guardada.capture());
        assertThat(guardada.getValue().horaDisparo()).isEqualTo(LocalTime.of(6, 0));
        assertThat(guardada.getValue().horaLimite()).isEqualTo(LocalTime.of(8, 0));
    }

    /** El pendiente sin recordatorio explicito (null) no debe pisar el que ya tenia la preferencia. */
    @Test
    void elRecordatorioNuloNoPisaElVigente() {
        PreferenciaHorario vigente = PreferenciaHorario.crear(participanteId, habitoId, LocalTime.of(9, 0),
                LocalTime.of(11, 0), CLOCK.now());
        vigente.actualizarRecordatorio(true, 30, CLOCK.now());
        when(loadCambioPendientePort.queYaRigenEn(HOY)).thenReturn(List.of(pendiente(null, null)));
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId)).thenReturn(Optional.of(vigente));

        service.promoverLosQueYaRigen();

        assertThat(vigente.recordatorioActivo()).isTrue();
        assertThat(vigente.minutosRecordatorio()).isEqualTo(30);
    }

    @Test
    void elRecordatorioProgramadoSiSeAplica() {
        PreferenciaHorario vigente = PreferenciaHorario.crear(participanteId, habitoId, LocalTime.of(9, 0),
                LocalTime.of(11, 0), CLOCK.now());
        when(loadCambioPendientePort.queYaRigenEn(HOY)).thenReturn(List.of(pendiente(false, 5)));
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId)).thenReturn(Optional.of(vigente));

        service.promoverLosQueYaRigen();

        assertThat(vigente.recordatorioActivo()).isFalse();
        assertThat(vigente.minutosRecordatorio()).isEqualTo(5);
    }

    /** La decision documentada: el cambio diferido cobra cupo el dia que pasa a regir, no antes. */
    @Test
    void promoverCobraCupoRegistrandoEnElHistorialConLaFechaEfectiva() {
        when(loadCambioPendientePort.queYaRigenEn(HOY)).thenReturn(List.of(pendiente(null, null)));
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId))
                .thenReturn(Optional.of(PreferenciaHorario.crear(participanteId, habitoId, null, null, CLOCK.now())));

        service.promoverLosQueYaRigen();

        verify(historialPort).registrar(participanteId, habitoId, HOY, LocalTime.of(6, 0), LocalTime.of(8, 0),
                CLOCK.now());
    }

    @Test
    void borraElPendienteDespuesDePromoverloParaQueUnaSegundaCorridaNoDupliqueNada() {
        when(loadCambioPendientePort.queYaRigenEn(HOY)).thenReturn(List.of(pendiente(null, null)))
                .thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId))
                .thenReturn(Optional.of(PreferenciaHorario.crear(participanteId, habitoId, null, null, CLOCK.now())));

        assertThat(service.promoverLosQueYaRigen()).isEqualTo(1);
        assertThat(service.promoverLosQueYaRigen()).isZero();

        verify(saveCambioPendientePort).borrar(participanteId, habitoId);
        verify(historialPort).registrar(any(), any(), any(), any(), any(), any());
    }

    /** Defensa: si la preferencia padre no estuviera, promover igual tiene que poder crearla. */
    @Test
    void sinPreferenciaPreviaLaCreaConLoProgramado() {
        when(loadCambioPendientePort.queYaRigenEn(HOY)).thenReturn(List.of(pendiente(null, null)));
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId)).thenReturn(Optional.empty());

        service.promoverLosQueYaRigen();

        ArgumentCaptor<PreferenciaHorario> creada = ArgumentCaptor.forClass(PreferenciaHorario.class);
        verify(savePreferenciaPort).save(creada.capture());
        assertThat(creada.getValue().horaDisparo()).isEqualTo(LocalTime.of(6, 0));
        assertThat(creada.getValue().horaLimite()).isEqualTo(LocalTime.of(8, 0));
    }

    /**
     * C-6 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): un pendiente que
     * falla al guardar su preferencia no debe impedir que el otro pendiente, vencido en el
     * mismo barrido, se promueva igual.
     */
    @Test
    @DisplayName("promoverLosQueYaRigen(): un pendiente que falla al guardar no tumba el barrido de los demas")
    void promoverAislaElPendienteQueFalla() {
        UserId otroParticipante = UserId.of(UUID.randomUUID());
        HabitoId otroHabito = HabitoId.of(UUID.randomUUID());
        CambioHorarioPendiente fallido = CambioHorarioPendiente.programar(participanteId, habitoId,
                LocalTime.of(6, 0), LocalTime.of(8, 0), null, null, HOY, CLOCK.now());
        CambioHorarioPendiente exitoso = CambioHorarioPendiente.programar(otroParticipante, otroHabito,
                LocalTime.of(7, 0), LocalTime.of(9, 0), null, null, HOY, CLOCK.now());
        PreferenciaHorario vigenteFallida = PreferenciaHorario.crear(participanteId, habitoId, null, null,
                CLOCK.now());
        PreferenciaHorario vigenteExitosa = PreferenciaHorario.crear(otroParticipante, otroHabito, null, null,
                CLOCK.now());

        when(loadCambioPendientePort.queYaRigenEn(HOY)).thenReturn(List.of(fallido, exitoso));
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId))
                .thenReturn(Optional.of(vigenteFallida));
        when(loadPreferenciaPort.porParticipanteYHabito(otroParticipante, otroHabito))
                .thenReturn(Optional.of(vigenteExitosa));
        when(savePreferenciaPort.save(vigenteFallida)).thenThrow(new IllegalStateException("fila corrupta simulada"));
        when(savePreferenciaPort.save(vigenteExitosa)).thenAnswer(inv -> inv.getArgument(0));

        int promovidos = service.promoverLosQueYaRigen();

        assertThat(promovidos).as("solo el pendiente exitoso se promovio").isEqualTo(1);
        verify(saveCambioPendientePort).borrar(otroParticipante, otroHabito);
        // El borrado del fallido se deshace con su transaccion (el pendiente sigue); lo que no puede haber es cobro.
        verify(historialPort, never()).registrar(eq(participanteId), eq(habitoId), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------------------------------------
    // E-557: la fecha efectiva se compara contra el dia LOCAL de cada participante, no contra el UTC.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Lima: a las 04:40 UTC (23:40 del dia anterior suyas) el cambio del 25 todavia NO rige; a las 05:00 si")
    void limaNoPromueveAntesDeSuMedianoche() {
        when(loadCambioPendientePort.queYaRigenEn(any())).thenReturn(List.of(pendiente(null, null)));
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId)).thenReturn(Optional.empty());

        assertThat(servicioCon(FixedClock.at(Instant.parse("2026-08-25T04:40:00Z"))).promoverLosQueYaRigen())
                .isZero();
        verify(saveCambioPendientePort, never()).borrar(any(), any());

        assertThat(servicioCon(CLOCK).promoverLosQueYaRigen()).isEqualTo(1);
    }

    @Test
    @DisplayName("Los Angeles (UTC-8): a las 05:00 UTC el 25 todavia no empezo para ella; rige a las 08:00 UTC")
    void losAngelesPromueveEnSuMedianoche() {
        ZoneId losAngeles = ZoneId.of("America/Los_Angeles");
        when(zonas.de(any(), any())).thenReturn(losAngeles);
        when(loadCambioPendientePort.queYaRigenEn(any())).thenReturn(List.of(pendiente(null, null)));
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId)).thenReturn(Optional.empty());

        assertThat(servicioCon(CLOCK).promoverLosQueYaRigen()).as("21:00 del 24 en Los Angeles").isZero();
        verify(historialPort, never()).registrar(any(), any(), any(), any(), any(), any());

        assertThat(servicioCon(FixedClock.at(Instant.parse("2026-08-25T08:00:00Z"))).promoverLosQueYaRigen())
                .as("00:00 del 25 en Los Angeles").isEqualTo(1);
    }

    @Test
    @DisplayName("Tokio (UTC+9): rige a las 15:00 UTC del 24, cuando le empieza el 25, no a las 04:40 UTC del 25")
    void tokioPromueveEnSuMedianoche() {
        when(zonas.de(any(), any())).thenReturn(ZoneId.of("Asia/Tokyo"));
        when(loadCambioPendientePort.queYaRigenEn(any())).thenReturn(List.of(pendiente(null, null)));
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId)).thenReturn(Optional.empty());

        assertThat(servicioCon(FixedClock.at(Instant.parse("2026-08-24T14:59:00Z"))).promoverLosQueYaRigen())
                .isZero();
        assertThat(servicioCon(FixedClock.at(Instant.parse("2026-08-24T15:00:00Z"))).promoverLosQueYaRigen())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("dos promociones simultaneas del mismo pendiente: la que pierde la fila no aplica ni cobra nada")
    void quienNoBorroElPendienteNoLoCobra() {
        when(loadCambioPendientePort.queYaRigenEn(any())).thenReturn(List.of(pendiente(null, null)));
        when(saveCambioPendientePort.borrar(participanteId, habitoId)).thenReturn(false);

        assertThat(service.promoverLosQueYaRigen()).isZero();

        verify(savePreferenciaPort, never()).save(any());
        verify(historialPort, never()).registrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("un participante cuya zona no se puede leer no frena a los demas")
    void unaZonaRotaNoFrenaElBarrido() {
        UserId otro = UserId.of(UUID.randomUUID());
        HabitoId otroHabito = HabitoId.of(UUID.randomUUID());
        CambioHorarioPendiente delOtro = CambioHorarioPendiente.programar(otro, otroHabito, LocalTime.of(7, 0),
                LocalTime.of(9, 0), null, null, HOY, CLOCK.now());
        when(loadCambioPendientePort.queYaRigenEn(any())).thenReturn(List.of(pendiente(null, null), delOtro));
        when(zonas.de(eq(participanteId), any())).thenThrow(new IllegalStateException("zona rota simulada"));
        when(loadPreferenciaPort.porParticipanteYHabito(otro, otroHabito)).thenReturn(Optional.empty());

        assertThat(service.promoverLosQueYaRigen()).isEqualTo(1);

        verify(saveCambioPendientePort).borrar(otro, otroHabito);
        verify(saveCambioPendientePort, never()).borrar(participanteId, habitoId);
    }

    @Test
    @DisplayName("promoverLosDe(): solo lo de ESA persona que ya rige en su hoy; lo de manana queda pendiente")
    void promoverLosDeUnParticipante() {
        CambioHorarioPendiente deHoy = pendiente(null, null);
        HabitoId otroHabito = HabitoId.of(UUID.randomUUID());
        CambioHorarioPendiente deManana = CambioHorarioPendiente.programar(participanteId, otroHabito,
                LocalTime.of(7, 0), LocalTime.of(9, 0), null, null, HOY.plusDays(1), CLOCK.now());
        when(loadCambioPendientePort.deParticipante(participanteId)).thenReturn(List.of(deHoy, deManana));
        when(loadPreferenciaPort.porParticipanteYHabito(participanteId, habitoId)).thenReturn(Optional.empty());

        assertThat(service.promoverLosDe(participanteId, HOY)).isEqualTo(1);

        verify(saveCambioPendientePort).borrar(participanteId, habitoId);
        verify(saveCambioPendientePort, never()).borrar(participanteId, otroHabito);
        verify(historialPort, times(1)).registrar(any(), any(), any(), any(), any(), any());
    }
}
