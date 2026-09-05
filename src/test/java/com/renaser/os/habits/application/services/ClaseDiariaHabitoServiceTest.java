package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase.CompletarClaseDiariaHabitoCommand;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase.CLAVE_SISTEMA_DAILY_CLASS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaseDiariaHabitoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final LocalDate HOY = LocalDate.of(2026, 8, 24);

    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private CompletarRegistroUseCase completarRegistroUseCase;

    private ClaseDiariaHabitoService service() {
        return new ClaseDiariaHabitoService(loadHabitoPort, loadRegistroPort, progresoPort, completarRegistroUseCase,
                CLOCK);
    }

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    private static Habito habitoDailyClass() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Clase diaria", TipoHabito.CHECKBOX, "ACADEMIA",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
    }

    private static RegistroHabito registroPendiente(UserId participanteId, HabitoId habitoId) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participanteId, habitoId, HOY, 5,
                TipoDia.DISCIPLINA, false, CLOCK.now());
    }

    private void mockProgresoActivo(UserId participanteId) {
        when(progresoPort.deParticipante(participanteId))
                .thenReturn(Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
    }

    @Test
    @DisplayName("completarDeHoy(): localiza el habito DAILY_CLASS y delega el completado en CompletarRegistroUseCase")
    void completarDeHoyDelegaEnRegistroService() {
        UserId participanteId = participante();
        Habito habito = habitoDailyClass();
        RegistroHabito pendiente = registroPendiente(participanteId, habito.id());
        RegistroHabito completado = registroPendiente(participanteId, habito.id());
        completado.completar(10, "Buen resumen de la clase de hoy", null, null, CLOCK.now());

        mockProgresoActivo(participanteId);
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_DAILY_CLASS)).thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habito.id(), HOY))
                .thenReturn(Optional.of(pendiente));
        when(completarRegistroUseCase.completar(any())).thenReturn(completado);

        var resultado = service().completarDeHoy(
                new CompletarClaseDiariaHabitoCommand(participanteId, "Buen resumen de la clase de hoy"));

        assertThat(resultado.registroId()).isEqualTo(completado.id().value());
        assertThat(resultado.puntosOtorgados()).isEqualTo(10);
        verify(completarRegistroUseCase).completar(new CompletarRegistroCommand(participanteId, pendiente.id(),
                "Buen resumen de la clase de hoy", null));
    }

    @Test
    @DisplayName("completarDeHoy(): si el registro de hoy ya esta COMPLETADO, es idempotente y no vuelve a completar")
    void completarDeHoyEsIdempotenteSiYaCompletado() {
        UserId participanteId = participante();
        Habito habito = habitoDailyClass();
        RegistroHabito yaCompletado = registroPendiente(participanteId, habito.id());
        yaCompletado.completar(10, "resumen original de mas de veinte caracteres", null, null, CLOCK.now());

        mockProgresoActivo(participanteId);
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_DAILY_CLASS)).thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habito.id(), HOY))
                .thenReturn(Optional.of(yaCompletado));

        var resultado = service().completarDeHoy(
                new CompletarClaseDiariaHabitoCommand(participanteId, "otro resumen de mas de veinte caracteres"));

        assertThat(resultado.registroId()).isEqualTo(yaCompletado.id().value());
        assertThat(resultado.puntosOtorgados()).isEqualTo(10);
        verify(completarRegistroUseCase, never()).completar(any());
    }

    @Test
    @DisplayName("completarDeHoy(): cuenta suspendida -> NotAuthorizedException (CLAUDE.MD §0.3)")
    void completarDeHoyRechazaSuspendido() {
        UserId participanteId = participante();
        when(progresoPort.deParticipante(participanteId)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, true)));
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_DAILY_CLASS)).thenReturn(Optional.of(habitoDailyClass()));

        assertThatThrownBy(() -> service().completarDeHoy(
                new CompletarClaseDiariaHabitoCommand(participanteId, "resumen valido de mas de veinte caracteres")))
                .isInstanceOf(NotAuthorizedException.class);
        verify(completarRegistroUseCase, never()).completar(any());
    }

    @Test
    @DisplayName("completarDeHoy(): sin habito DAILY_CLASS en el catalogo -> NoSuchElementException")
    void completarDeHoySinHabitoEnCatalogo() {
        UserId participanteId = participante();
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_DAILY_CLASS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().completarDeHoy(
                new CompletarClaseDiariaHabitoCommand(participanteId, "resumen valido de mas de veinte caracteres")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    @DisplayName("completarDeHoy(): sin registro generado para hoy -> NoSuchElementException")
    void completarDeHoySinRegistroDeHoy() {
        UserId participanteId = participante();
        Habito habito = habitoDailyClass();
        mockProgresoActivo(participanteId);
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_DAILY_CLASS)).thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habito.id(), HOY))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().completarDeHoy(
                new CompletarClaseDiariaHabitoCommand(participanteId, "resumen valido de mas de veinte caracteres")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    @DisplayName("CompletarClaseDiariaHabitoCommand: resumen menor a 15 caracteres es rechazado en el constructor")
    void comandoRechazaResumenCorto() {
        assertThatThrownBy(() -> new CompletarClaseDiariaHabitoCommand(participante(), "muy corto"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("CompletarClaseDiariaHabitoCommand: los bordes exactos del resumen (15 y 2000) se aceptan; "
            + "14 y 2001 no")
    void comandoRespetaLosBordesExactosDelResumen() {
        assertThatThrownBy(() -> new CompletarClaseDiariaHabitoCommand(participante(), "a".repeat(14)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> new CompletarClaseDiariaHabitoCommand(participante(), "a".repeat(2001)))
                .isInstanceOf(ConstraintViolationException.class);

        assertThatCode(() -> new CompletarClaseDiariaHabitoCommand(participante(), "a".repeat(15)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new CompletarClaseDiariaHabitoCommand(participante(), "a".repeat(2000)))
                .doesNotThrowAnyException();
    }
}
