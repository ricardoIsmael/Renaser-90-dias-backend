package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadarId;
import com.renaser.os.shared.application.EntregaSinRedDePrueba;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El Código Renaser respondido sin red (spec 048, fase 3, FR-019). La persona responde el lunes 9 a las 21:40 de Lima (02:40 UTC del
 * 10, el día local ANTERIOR en UTC) y la app lo envía el martes 10 a las 08:00 de Lima.
 */
class CodigoRenaserSinRedServiceTest {

    private static final String LIMA = "America/Lima";
    /** 21:40 del lunes 9 de noviembre en Lima. */
    private static final Instant HECHO = Instant.parse("2026-11-10T02:40:00Z");
    /** 08:00 del martes 10 en Lima. */
    private static final Instant LLEGA = Instant.parse("2026-11-10T13:00:00Z");
    private static final UUID ENVIO = UUID.fromString("5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10");
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final HechoSinRed SIN_RED = new HechoSinRed(HECHO, ENVIO, OrigenDeLaHora.RELOJ_DEL_SISTEMA);

    private final LoadRegistroRadarPort loadPort = mock(LoadRegistroRadarPort.class);
    private final SaveRegistroRadarPort savePort = mock(SaveRegistroRadarPort.class);
    private final LoadMiRadarPort miRadarPort = mock(LoadMiRadarPort.class);
    private final ConsultarProgresoParticipanteHabitsPort progresoPort = mock(ConsultarProgresoParticipanteHabitsPort.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final HorarioDelDiaFinder horarios = mock(HorarioDelDiaFinder.class);

    private EntregaSinRedDePrueba entrega;
    private CodigoRenaserSinRedService servicio;
    private UserId persona;

    @BeforeEach
    void preparar() {
        FixedClock reloj = FixedClock.at(LLEGA);
        entrega = EntregaSinRedDePrueba.nueva(reloj);
        servicio = new CodigoRenaserSinRedService(new AccesoAlRadar(progresoPort), loadPort, savePort, miRadarPort,
                new AgendaDelRadar(horarios, reloj), entrega.puerto(), reloj, idGenerator);
        persona = UserId.of(UUID.randomUUID());
        when(idGenerator.newId()).thenReturn(ID_GENERADO);
        when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loadPort.entre(any(), any(), any())).thenReturn(List.of());
        when(miRadarPort.deParticipante(persona)).thenReturn(MiRadar.apagado());
        when(miRadarPort.actualizadoEn(persona)).thenReturn(Optional.empty());
    }

    @AfterEach
    void terminar() {
        entrega.terminar();
    }

    private void enElDia(int dia) {
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(dia, LIMA, RolParticipante.TRAINEE, false, true)));
    }

    private RegistrarCheckInRadarCommand comando() {
        return new RegistrarCheckInRadarCommand(persona, persona, "escribiendo", "pensando", "tranquilo", 6, "nada", false);
    }

    @Test
    @DisplayName("días 1 a 7: se guarda con la hora real (creado_en y hora prevista de las 21 locales no existe: fuera de 8 a 19), sin mirar Mi Radar")
    void primeraSemanaSeGuardaConLaHoraReal() { // spec 048 FR-019
        enElDia(7);

        RegistroRadar registro = servicio.registrar(comando(), SIN_RED);

        assertThat(registro.creadoEn()).isEqualTo(HECHO);
        assertThat(registro.horaPrevista()).isNull();
        verify(savePort).save(registro);
        verify(miRadarPort, never()).deParticipante(any());
        assertThat(entrega.registro(ENVIO)).isPresent();
    }

    @Test
    @DisplayName("días 1 a 7: la hora prevista sale de la hora LOCAL real (11:30 de Lima), no de la llegada")
    void horaPrevistaDeLaHoraReal() { // spec 048 FR-019
        enElDia(7);
        Instant lasOnceYMedia = Instant.parse("2026-11-09T16:30:00Z");

        RegistroRadar registro = servicio.registrar(comando(), new HechoSinRed(lasOnceYMedia, UUID.randomUUID(), null));

        assertThat(registro.creadoEn()).isEqualTo(lasOnceYMedia);
        assertThat(registro.horaPrevista()).isEqualTo(11);
    }

    @Test
    @DisplayName("el día del programa se cuenta a la hora real: la llegada ya es el día 8 pero lo hecho anoche era el 7 y no pide Mi Radar")
    void elDiaDelProgramaSeCuentaALaHoraReal() { // spec 048 FR-019
        enElDia(8);

        RegistroRadar registro = servicio.registrar(comando(), SIN_RED);

        assertThat(registro.creadoEn()).isEqualTo(HECHO);
        verify(miRadarPort, never()).deParticipante(any());
    }

    @Test
    @DisplayName("desde el día 8 con Mi Radar encendido se revisa con las horas de ESA fecha (Despertar 05:00, Dormir 22:30): las 21:40 caben")
    void desdeElOchoConMiRadar() { // spec 048 FR-019
        enElDia(9);
        when(miRadarPort.deParticipante(persona)).thenReturn(new MiRadar(true, List.of()));
        HorarioDeRadarDePrueba.siempre(horarios, LocalTime.of(5, 0), LocalTime.of(22, 30));

        RegistroRadar registro = servicio.registrar(comando(), SIN_RED);

        assertThat(registro.creadoEn()).isEqualTo(HECHO);
        assertThat(registro.horaPrevista()).isEqualTo(21);
    }

    @Test
    @DisplayName("desde el día 8 sin Mi Radar encendido no se aplica: MI_RADAR_NO_APLICA y queda anotado, sin guardar nada")
    void sinMiRadarNoAplica() { // spec 048 FR-019
        enElDia(9);

        assertThatThrownBy(() -> servicio.registrar(comando(), SIN_RED)).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.MI_RADAR_NO_APLICA));

        verify(savePort, never()).save(any());
        assertThat(entrega.noAplicado(ENVIO)).contains(CodigoNoAplicado.MI_RADAR_NO_APLICA);
    }

    @Test
    @DisplayName("un día sin horario (sin Despertar) no tenía Radar: MI_RADAR_NO_APLICA aunque Mi Radar estuviera encendido")
    void unDiaSinHorarioNoTeniaRadar() { // spec 048 FR-019
        enElDia(9);
        when(miRadarPort.deParticipante(persona)).thenReturn(new MiRadar(true, List.of()));
        HorarioDeRadarDePrueba.siempre(horarios, null, LocalTime.of(22, 30));

        assertThatThrownBy(() -> servicio.registrar(comando(), SIN_RED)).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.MI_RADAR_NO_APLICA));
    }

    @Test
    @DisplayName("fuera de 03:00 a 23:59 a la HORA REAL (02:30 de Lima) tampoco se aplica, aunque a la llegada sea de día")
    void fueraDeLaVentanaALaHoraReal() { // spec 048 FR-019
        enElDia(9);
        when(miRadarPort.deParticipante(persona)).thenReturn(new MiRadar(true, List.of()));
        HorarioDeRadarDePrueba.siempre(horarios, LocalTime.of(5, 0), LocalTime.of(22, 30));
        Instant lasDosYMedia = Instant.parse("2026-11-09T07:30:00Z");

        assertThatThrownBy(() -> servicio.registrar(comando(), new HechoSinRed(lasDosYMedia, UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.MI_RADAR_NO_APLICA));
    }

    @Test
    @DisplayName("D13: si Mi Radar se cambió en el servidor DESPUÉS de lo que hiciste, no es «no aplica» sino CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR")
    void miRadarCambiadoDespues() { // spec 048 FR-019
        enElDia(9);
        when(miRadarPort.actualizadoEn(persona)).thenReturn(Optional.of(HECHO.plusSeconds(120)));

        assertThatThrownBy(() -> servicio.registrar(comando(), SIN_RED)).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
    }

    @Test
    @DisplayName("P4: una hora que el servidor ya tiene respondida se queda con la del servidor: YA_ESTABA_HECHO, nunca dos por hora")
    void unaPorHora() { // spec 048 FR-019
        enElDia(7);
        RegistroRadar delServidor = RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), persona, "a", "b", "c", 5, "d",
                Instant.parse("2026-11-10T02:10:00Z"));
        when(loadPort.entre(any(), any(), any())).thenReturn(List.of(delServidor));

        assertThatThrownBy(() -> servicio.registrar(comando(), SIN_RED)).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.YA_ESTABA_HECHO));

        ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> hasta = ArgumentCaptor.forClass(Instant.class);
        verify(loadPort).entre(any(), desde.capture(), hasta.capture());
        assertThat(desde.getValue()).isEqualTo(Instant.parse("2026-11-10T02:00:00Z"));
        assertThat(hasta.getValue()).isEqualTo(Instant.parse("2026-11-10T03:00:00Z"));
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("el mismo envío dos veces guarda un solo registro y devuelve el que quedó")
    void repetirElEnvio() { // spec 048 FR-008
        enElDia(7);
        RegistroRadar primero = servicio.registrar(comando(), SIN_RED);
        when(loadPort.entre(any(), any(), any())).thenReturn(List.of(primero));

        RegistroRadar otraVez = servicio.registrar(comando(), SIN_RED);

        assertThat(otraVez).isSameAs(primero);
        verify(savePort, times(1)).save(any());
    }

    @Test
    @DisplayName("una cuenta suspendida recibe el 403 de siempre")
    void cuentaSuspendida() { // spec 048 FR-019
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(7, LIMA, RolParticipante.TRAINEE, true, true)));

        assertThatThrownBy(() -> servicio.registrar(comando(), SIN_RED)).isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).save(any());
    }
}
