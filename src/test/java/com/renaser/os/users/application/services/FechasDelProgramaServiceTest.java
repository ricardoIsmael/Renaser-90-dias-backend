package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.FechasDelProgramaFinder.FechasDelPrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Las fechas de «Mi aceptación» (spec 039): mañana en la zona de la persona o las reales si ya activó, y el último día con la cuenta del agregado. El reloj está fijado
 * en la madrugada UTC (regla 02): 04:30 UTC del 3 son las 23:30 del 2 en Lima.
 */
@ExtendWith(MockitoExtension.class)
class FechasDelProgramaServiceTest {

    private static final FixedClock MADRUGADA_UTC = FixedClock.at(Instant.parse("2026-11-03T04:30:00Z"));

    @Mock
    private LoadParticipacionProgramaPort participaciones;
    @Mock
    private LoadUserPort usuarios;

    private FechasDelProgramaService servicio;
    private UserId persona;

    @BeforeEach
    void setUp() {
        servicio = new FechasDelProgramaService(participaciones, usuarios, MADRUGADA_UTC);
        persona = UserId.of(UUID.randomUUID());
    }

    private void conRol(UserRole rol, UserStatus estado) {
        when(usuarios.byId(persona)).thenReturn(Optional.of(User.rehydrate(persona, new Email(persona + "@test.com"), rol, estado, "Ana Pérez", null, null, null, null)));
    }

    @Test
    @DisplayName("un aprendiz sin activar ve mañana (en Lima, no el día UTC) y el fin según su duración: 90 días, 3 meses y 1 año")
    void aprendizSinActivar() {
        conRol(UserRole.TRAINEE, UserStatus.ACTIVE);
        record Caso(Formacion formacion, int dias, String fin) { }
        for (Caso caso : new Caso[] {new Caso(Formacion.ESCUELA, 90, "2027-01-31"), new Caso(Formacion.DIPLOMADO, 90, "2027-01-31"),
                new Caso(Formacion.DIPLOMADO, 365, "2027-11-02")}) {
            when(participaciones.byParticipanteId(persona)).thenReturn(Optional.of(ParticipacionPrograma.inscribirTraineeAprobado(persona, MADRUGADA_UTC, caso.formacion(), caso.dias())));

            FechasDelPrograma fechas = servicio.de(persona).orElseThrow();

            assertThat(fechas.activado()).isFalse();
            assertThat(fechas.formacion()).isEqualTo(caso.formacion());
            assertThat(fechas.duracionDias()).isEqualTo(caso.dias());
            assertThat(fechas.inicio()).as("%s %s", caso.formacion(), caso.dias()).isEqualTo(LocalDate.parse("2026-11-03"));
            assertThat(fechas.fin()).as("%s %s", caso.formacion(), caso.dias()).isEqualTo(LocalDate.parse(caso.fin()));
        }
    }

    @Test
    @DisplayName("quien ya activó ve las fechas reales: el inicio que tiene y el último día de su programa")
    void yaActivado() {
        conRol(UserRole.TRAINEE, UserStatus.ACTIVE);
        ParticipacionPrograma activada = ParticipacionPrograma.rehydrate(persona, null, null, 5, FasePrograma.PHASE_1_REBIRTH, LocalDate.parse("2026-10-26"),
                Instant.parse("2026-10-25T17:00:00Z"), ZoneId.of("America/Lima"), false, 0, MADRUGADA_UTC.now(), MADRUGADA_UTC.now(), null, null, null, null, 0,
                Formacion.ESCUELA, 90);
        when(participaciones.byParticipanteId(persona)).thenReturn(Optional.of(activada));

        FechasDelPrograma fechas = servicio.de(persona).orElseThrow();

        assertThat(fechas.activado()).isTrue();
        assertThat(fechas.inicio()).isEqualTo(LocalDate.parse("2026-10-26"));
        assertThat(fechas.fin()).isEqualTo(LocalDate.parse("2026-10-26").plusDays(89));
    }

    @Test
    @DisplayName("el personal con una fila sin activar no ve fechas: no se le promete un inicio que no eligió; con el programa activado sí")
    void elPersonalSinActivarNoVeFechas() {
        conRol(UserRole.MENTOR, UserStatus.ACTIVE);
        when(participaciones.byParticipanteId(persona)).thenReturn(Optional.of(ParticipacionPrograma.inscribirPersonalSinActivar(persona, MADRUGADA_UTC)));

        FechasDelPrograma fechas = servicio.de(persona).orElseThrow();

        assertThat(fechas.inicio()).isNull();
        assertThat(fechas.fin()).isNull();
        assertThat(fechas.duracionDias()).isEqualTo(90);

        ParticipacionPrograma activada = ParticipacionPrograma.inscribirPersonalSinActivar(persona, MADRUGADA_UTC);
        activada.activarPrograma(null, MADRUGADA_UTC);
        when(participaciones.byParticipanteId(persona)).thenReturn(Optional.of(activada));

        assertThat(servicio.de(persona).orElseThrow().inicio()).isEqualTo(LocalDate.parse("2026-11-03"));
    }

    @Test
    @DisplayName("sin fila en el programa, o con la cuenta suspendida, no hay fechas")
    void sinFilaOSuspendida() {
        conRol(UserRole.TRAINEE, UserStatus.ACTIVE);
        when(participaciones.byParticipanteId(persona)).thenReturn(Optional.empty());
        assertThat(servicio.de(persona)).isEmpty();

        conRol(UserRole.TRAINEE, UserStatus.SUSPENDED);
        assertThat(servicio.de(persona)).isEmpty();
    }
}
