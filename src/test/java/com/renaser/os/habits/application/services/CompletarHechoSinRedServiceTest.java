package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.CompletarHechoSinRedUseCase.CompletadoSinRed;
import com.renaser.os.habits.application.ports.in.registro.CompletarHechoSinRedUseCase.HechoSinRed;
import com.renaser.os.habits.application.ports.in.registro.CompletarHechoSinRedUseCase.MotivoSinPuntos;
import com.renaser.os.habits.application.ports.in.registro.CompletarHechoSinRedUseCase.OrigenDeLaHora;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.sinred.EnviosSinRedPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 048 (D14): lo marcado sin red nunca rebota. En regla suma con su hora real; fuera de regla queda sin puntos y dice por
 * qué; un mismo envío se registra una sola vez. Reloj: 10:00 del martes 10/11 en Lima (15:00 UTC); el registro es del lunes 9.
 */
class CompletarHechoSinRedServiceTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final Instant AHORA = Instant.parse("2026-11-10T15:00:00Z");
    /** 21:10 del lunes 9 en Lima. */
    private static final Instant ANOCHE = Instant.parse("2026-11-10T02:10:00Z");
    private static final UUID ENVIO = UUID.fromString("48480000-0000-0000-0000-000000000001");

    private final RegistroService registros = mock(RegistroService.class);
    private final LoadRegistroHabitoPort loadRegistro = mock(LoadRegistroHabitoPort.class);
    private final ConsultarProgresoParticipanteHabitsPort progreso = mock(ConsultarProgresoParticipanteHabitsPort.class);
    private final EnviosSinRedPort envios = mock(EnviosSinRedPort.class);
    private final CompletarHechoSinRedService servicio = new CompletarHechoSinRedService(registros, loadRegistro, progreso,
            envios, FixedClock.at(AHORA));

    private final RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), PERSONA,
            HabitoId.of(UUID.randomUUID()), LocalDate.of(2026, 11, 9), 40, TipoDia.TODOS, false, Instant.parse("2026-11-09T10:00:00Z"));
    private final CompletarRegistroCommand comando = new CompletarRegistroCommand(PERSONA, registro.id(), "Hecho", null);

    private CompletadoSinRed hechoA(Instant hechoEn) {
        when(loadRegistro.byId(registro.id())).thenReturn(Optional.of(registro));
        when(envios.buscar(ENVIO, PERSONA)).thenReturn(Optional.empty());
        when(progreso.deParticipante(PERSONA)).thenReturn(Optional.of(new ProgresoParticipanteHabits(40, "America/Lima",
                ConsultarProgresoParticipanteHabitsPort.RolParticipante.values()[0], false, true)));
        when(registros.completarConHoraReal(eq(comando), eq(hechoEn), anyBoolean())).thenReturn(registro);
        return servicio.completar(comando, new HechoSinRed(hechoEn, ENVIO, OrigenDeLaHora.RELOJ_DEL_SISTEMA));
    }

    @Test
    @DisplayName("Hecho anoche dentro de su día y llegado hoy: suma con la hora real, aunque el día ya cerró")
    void enRegla() { // spec 048 FR-014
        CompletadoSinRed resultado = hechoA(ANOCHE);

        assertThat(resultado.motivoSinPuntos()).isNull();
        verify(registros).completarConHoraReal(comando, ANOCHE, true);
        verify(envios).registrar(ENVIO, PERSONA, registro.id().value(), null);
    }

    @Test
    @DisplayName("Llegado más de 48 h después: queda registrado sin puntos y lo dice")
    void masDe48Horas() { // spec 048 FR-015
        Instant hace49 = AHORA.minusSeconds(49 * 3600);
        assertThat(hechoA(hace49).motivoSinPuntos()).isEqualTo(MotivoSinPuntos.LLEGO_DESPUES_DE_48_H);
        verify(registros).completarConHoraReal(comando, hace49, false);
    }

    @Test
    @DisplayName("Con una hora del futuro: sin puntos")
    void horaFutura() { // spec 048 FR-015
        Instant futuro = AHORA.plusSeconds(600);
        assertThat(hechoA(futuro).motivoSinPuntos()).isEqualTo(MotivoSinPuntos.HORA_FUTURA);
        verify(registros).completarConHoraReal(comando, futuro, false);
    }

    @Test
    @DisplayName("Con una hora de otro día: sin puntos")
    void fueraDeSuDia() { // spec 048 FR-015
        Instant elDomingo = Instant.parse("2026-11-08T20:00:00Z");
        assertThat(hechoA(elDomingo).motivoSinPuntos()).isEqualTo(MotivoSinPuntos.FUERA_DE_SU_DIA);
    }

    @Test
    @DisplayName("El mismo envío repetido no vuelve a completar: devuelve lo que quedó")
    void repetidoUnaSolaVez() { // spec 048 FR-008
        when(loadRegistro.byId(registro.id())).thenReturn(Optional.of(registro));
        when(envios.buscar(ENVIO, PERSONA)).thenReturn(Optional.of(new EnviosSinRedPort.EnvioRegistrado(registro.id().value(),
                "LLEGO_DESPUES_DE_48_H")));

        CompletadoSinRed resultado = servicio.completar(comando, new HechoSinRed(ANOCHE, ENVIO, null));

        assertThat(resultado.motivoSinPuntos()).isEqualTo(MotivoSinPuntos.LLEGO_DESPUES_DE_48_H);
        verify(registros, never()).completarConHoraReal(any(), any(), anyBoolean());
        verify(envios, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ya estaba hecho (con red o desde otro teléfono): no rebota ni se toca")
    void yaEstabaHecho() { // spec 048 FR-015
        registro.completar(10, null, null, null, Instant.parse("2026-11-09T22:00:00Z"));
        when(loadRegistro.byId(registro.id())).thenReturn(Optional.of(registro));
        when(envios.buscar(ENVIO, PERSONA)).thenReturn(Optional.empty());

        assertThat(servicio.completar(comando, new HechoSinRed(ANOCHE, ENVIO, null)).motivoSinPuntos())
                .isEqualTo(MotivoSinPuntos.YA_ESTABA_HECHO);
        verify(registros, never()).completarConHoraReal(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("El registro de otra persona no se toca")
    void otraPersona() {
        UserId otra = UserId.of(UUID.randomUUID());
        when(loadRegistro.byId(registro.id())).thenReturn(Optional.of(registro));
        when(envios.buscar(ENVIO, otra)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.completar(new CompletarRegistroCommand(otra, registro.id(), null, null),
                new HechoSinRed(ANOCHE, ENVIO, null))).isInstanceOf(NotAuthorizedException.class);
    }
}
