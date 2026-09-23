package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.DiarioYRadarPort.BitacoraDeHoy;
import com.renaser.os.habits.api.DiarioYRadarPort.CheckInRadar;
import com.renaser.os.habits.api.DiarioYRadarPort.CheckInRadarRegistrado;
import com.renaser.os.habits.api.DiarioYRadarPort.RespuestasRadar;
import com.renaser.os.habits.application.ports.in.diario.ConsultarBitacoraNocturnaUseCase;
import com.renaser.os.habits.application.ports.in.diario.ConsultarBitacoraNocturnaUseCase.EstadoBitacoraHoy;
import com.renaser.os.habits.application.ports.in.diario.EscribirBitacoraNocturnaUseCase;
import com.renaser.os.habits.application.ports.in.diario.EscribirBitacoraNocturnaUseCase.EscribirBitacoraNocturnaCommand;
import com.renaser.os.habits.application.ports.in.radar.ConsultarUltimoRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.habits.domain.model.diario.EntradaDiarioId;
import com.renaser.os.habits.domain.model.diario.TipoEntradaDiario;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadarId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fachada de {@code habits.api.DiarioYRadarPort}: delega en los casos de uso de siempre y
 * resuelve la hora local en la zona del participante.
 *
 * <p>El reloj esta a las 03:30 UTC: en Lima son las 22:30 del DIA ANTERIOR (regla 02). Si la hora
 * local se calculara en UTC, el check-in de las 22:10 de Lima apareceria "a las 03:10 del 24".
 */
class DiarioYRadarServiceTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final Instant AHORA = Instant.parse("2026-09-24T03:30:00Z");
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 9, 23);

    private final ConsultarBitacoraNocturnaUseCase consultarBitacora = mock(ConsultarBitacoraNocturnaUseCase.class);
    private final EscribirBitacoraNocturnaUseCase escribirBitacora = mock(EscribirBitacoraNocturnaUseCase.class);
    private final ConsultarUltimoRadarUseCase ultimoRadar = mock(ConsultarUltimoRadarUseCase.class);
    private final RegistrarCheckInRadarUseCase registrarRadar = mock(RegistrarCheckInRadarUseCase.class);
    private final ConsultarProgresoParticipanteHabitsPort progresoPort = mock(ConsultarProgresoParticipanteHabitsPort.class);
    private final DiarioYRadarService service = new DiarioYRadarService(consultarBitacora, escribirBitacora,
            ultimoRadar, registrarRadar, progresoPort, FixedClock.at(AHORA));

    private void enLima() {
        when(progresoPort.deParticipante(APRENDIZ)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(12, "America/Lima", RolParticipante.TRAINEE, false, true)));
    }

    private static RegistroRadar radar(UUID id, Instant creadoEn) {
        return RegistroRadar.rehydrate(RegistroRadarId.of(id), APRENDIZ, "hago", "pienso", "siento", 7, "evito",
                creadoEn);
    }

    private static EntradaDiario entrada(String texto, String audioRuta) {
        return EntradaDiario.rehydrate(EntradaDiarioId.of(UUID.randomUUID()), APRENDIZ, HOY_EN_LIMA,
                TipoEntradaDiario.BITACORA_NOCTURNA, texto, audioRuta == null ? null : "audios", audioRuta, null,
                AHORA, AHORA);
    }

    @Test
    @DisplayName("la bitacora de hoy es la del caso de uso: fecha en su zona, texto y si tiene audio")
    void bitacoraDeHoy() {
        when(consultarBitacora.consultarHoy(APRENDIZ)).thenReturn(new EstadoBitacoraHoy(HOY_EN_LIMA, null));
        assertThat(service.bitacoraDeHoy(APRENDIZ)).isEqualTo(new BitacoraDeHoy(HOY_EN_LIMA, false, null, false));

        when(consultarBitacora.consultarHoy(APRENDIZ))
                .thenReturn(new EstadoBitacoraHoy(HOY_EN_LIMA, entrada("hoy fue duro", "ruta.m4a")));
        assertThat(service.bitacoraDeHoy(APRENDIZ))
                .isEqualTo(new BitacoraDeHoy(HOY_EN_LIMA, true, "hoy fue duro", true));
    }

    @Test
    @DisplayName("escribir delega en el caso de uso del PUT con solo texto, sin audio")
    void escribirBitacora() {
        when(escribirBitacora.escribir(any())).thenReturn(entrada("nuevo texto", null));

        BitacoraDeHoy escrita = service.escribirBitacoraDeHoy(APRENDIZ, "nuevo texto");

        verify(escribirBitacora).escribir(new EscribirBitacoraNocturnaCommand(APRENDIZ, "nuevo texto", null, null));
        assertThat(escrita).isEqualTo(new BitacoraDeHoy(HOY_EN_LIMA, true, "nuevo texto", false));
    }

    @Test
    @DisplayName("el ultimo radar sale en hora de Lima (dia anterior al UTC) y marca si ocupa la hora en curso")
    void ultimoRadarEnHoraLocal() {
        enLima();
        when(ultimoRadar.ultimo(APRENDIZ, APRENDIZ))
                .thenReturn(Optional.of(radar(UUID.randomUUID(), Instant.parse("2026-09-24T03:10:00Z"))));

        CheckInRadar ultimo = service.ultimoCheckInRadar(APRENDIZ).orElseThrow();

        assertThat(ultimo.registradoEn()).isEqualTo(LocalDateTime.of(2026, 9, 23, 22, 10));
        assertThat(ultimo.deEstaHora()).isTrue();
        assertThat(ultimo.respuestas()).isEqualTo(new RespuestasRadar("hago", "pienso", "siento", 7, "evito"));
    }

    @Test
    @DisplayName("un radar de la hora anterior no ocupa la franja; sin ninguno, vacio")
    void ultimoRadarDeOtraHoraOVacio() {
        enLima();
        when(ultimoRadar.ultimo(APRENDIZ, APRENDIZ))
                .thenReturn(Optional.of(radar(UUID.randomUUID(), Instant.parse("2026-09-24T02:59:00Z"))));
        assertThat(service.ultimoCheckInRadar(APRENDIZ).orElseThrow().deEstaHora()).isFalse();

        when(ultimoRadar.ultimo(APRENDIZ, APRENDIZ)).thenReturn(Optional.empty());
        assertThat(service.ultimoCheckInRadar(APRENDIZ)).isEmpty();
    }

    @Test
    @DisplayName("registrar delega en el caso de uso del POST sobre el propio actor; uno nuevo no 'ya existia'")
    void registrarNuevo() {
        enLima();
        when(ultimoRadar.ultimo(APRENDIZ, APRENDIZ))
                .thenReturn(Optional.of(radar(UUID.randomUUID(), Instant.parse("2026-09-24T01:00:00Z"))));
        when(registrarRadar.registrar(any())).thenReturn(radar(UUID.randomUUID(), AHORA));

        CheckInRadarRegistrado registrado = service.registrarCheckInRadar(APRENDIZ,
                new RespuestasRadar("hago", "pienso", "siento", 7, "evito"));

        verify(registrarRadar).registrar(new RegistrarCheckInRadarCommand(APRENDIZ, APRENDIZ, "hago", "pienso",
                "siento", 7, "evito"));
        assertThat(registrado).isEqualTo(new CheckInRadarRegistrado(LocalDateTime.of(2026, 9, 23, 22, 30), false));
    }

    @Test
    @DisplayName("si el caso de uso devuelve el mismo de esta hora, se informa que ya existia")
    void registrarDevuelveElExistente() {
        enLima();
        RegistroRadar existente = radar(UUID.randomUUID(), Instant.parse("2026-09-24T03:05:00Z"));
        when(ultimoRadar.ultimo(APRENDIZ, APRENDIZ)).thenReturn(Optional.of(existente));
        when(registrarRadar.registrar(any())).thenReturn(existente);

        CheckInRadarRegistrado registrado = service.registrarCheckInRadar(APRENDIZ,
                new RespuestasRadar("otro", "otro", "otro", 3, "otro"));

        assertThat(registrado).isEqualTo(new CheckInRadarRegistrado(LocalDateTime.of(2026, 9, 23, 22, 5), true));
    }
}
