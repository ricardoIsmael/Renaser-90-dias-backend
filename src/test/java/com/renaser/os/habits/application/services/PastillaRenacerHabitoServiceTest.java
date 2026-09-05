package com.renaser.os.habits.application.services;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PastillaRenacerHabitoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final LocalDate HOY = LocalDate.of(2026, 8, 24);
    private static final String RESUMEN = "Lo que interprete del audio de hoy";

    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private CompletarRegistroUseCase completarRegistroUseCase;

    private PastillaRenacerHabitoService service() {
        return new PastillaRenacerHabitoService(loadHabitoPort, loadRegistroPort, progresoPort,
                completarRegistroUseCase, CLOCK);
    }

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    private static Habito habitoPastilla() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Pastilla Renacer", TipoHabito.JOURNALING,
                "ESPIRITU", ExigenciaEvidencia.OPCIONAL, CLOCK.now());
    }

    private static RegistroHabito registroPendiente(UserId participanteId, HabitoId habitoId) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participanteId, habitoId, HOY, 8,
                TipoDia.DISCIPLINA, true, CLOCK.now());
    }

    private void mockProgresoActivo(UserId participanteId) {
        when(progresoPort.deParticipante(participanteId))
                .thenReturn(Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false)));
    }

    @Test
    @DisplayName("Localiza el habito PASTILLA_RENACER de hoy y delega el completado en CompletarRegistroUseCase")
    void completarDeHoyDelegaEnRegistroService() {
        UserId participanteId = participante();
        Habito habito = habitoPastilla();
        RegistroHabito pendiente = registroPendiente(participanteId, habito.id());
        RegistroHabito completado = registroPendiente(participanteId, habito.id());
        completado.completar(10, RESUMEN, null, null, CLOCK.now());

        mockProgresoActivo(participanteId);
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_PASTILLA_RENACER)).thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habito.id(), HOY))
                .thenReturn(Optional.of(pendiente));
        when(completarRegistroUseCase.completar(any())).thenReturn(completado);

        var resultado = service().completarDeHoy(participanteId, RESUMEN);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().registroId()).isEqualTo(completado.id().value());
        assertThat(resultado.get().puntosOtorgados()).isEqualTo(10);
        verify(completarRegistroUseCase)
                .completar(new CompletarRegistroCommand(participanteId, pendiente.id(), RESUMEN, null));
    }

    @Test
    @DisplayName("El resumen del audio viaja como respuestaTexto del registro, no se pierde")
    void elResumenQuedaGuardadoComoRespuestaDelRegistro() {
        UserId participanteId = participante();
        Habito habito = habitoPastilla();
        RegistroHabito pendiente = registroPendiente(participanteId, habito.id());

        mockProgresoActivo(participanteId);
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_PASTILLA_RENACER)).thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habito.id(), HOY))
                .thenReturn(Optional.of(pendiente));
        when(completarRegistroUseCase.completar(any())).thenAnswer(inv -> {
            CompletarRegistroCommand c = inv.getArgument(0);
            pendiente.completar(10, c.respuestaTexto(), null, null, CLOCK.now());
            return pendiente;
        });

        service().completarDeHoy(participanteId, RESUMEN);

        assertThat(pendiente.respuestaTexto()).isEqualTo(RESUMEN);
    }

    @Test
    @DisplayName("Idempotente: si el registro de hoy ya esta COMPLETADO no vuelve a completar ni a pagar puntos")
    void esIdempotenteSiYaCompletado() {
        UserId participanteId = participante();
        Habito habito = habitoPastilla();
        RegistroHabito yaCompletado = registroPendiente(participanteId, habito.id());
        yaCompletado.completar(10, "resumen original", null, null, CLOCK.now());

        mockProgresoActivo(participanteId);
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_PASTILLA_RENACER)).thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habito.id(), HOY))
                .thenReturn(Optional.of(yaCompletado));

        var resultado = service().completarDeHoy(participanteId, "otro resumen");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().puntosOtorgados()).isEqualTo(10);
        verify(completarRegistroUseCase, never()).completar(any());
    }

    @Test
    @DisplayName("Sin track de hoy (habito pausado o no generado) devuelve vacio: no hay nada que reflejar, no falla")
    void sinTrackDeHoyDevuelveVacio() {
        UserId participanteId = participante();
        Habito habito = habitoPastilla();

        mockProgresoActivo(participanteId);
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_PASTILLA_RENACER)).thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habito.id(), HOY))
                .thenReturn(Optional.empty());

        assertThat(service().completarDeHoy(participanteId, RESUMEN)).isEmpty();
        verify(completarRegistroUseCase, never()).completar(any());
    }

    @Test
    @DisplayName("Sin el habito en el catalogo de este entorno devuelve vacio, sin tocar el progreso del participante")
    void sinHabitoEnElCatalogoDevuelveVacio() {
        UserId participanteId = participante();
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_PASTILLA_RENACER)).thenReturn(Optional.empty());

        assertThat(service().completarDeHoy(participanteId, RESUMEN)).isEmpty();
        verify(completarRegistroUseCase, never()).completar(any());
    }

    @Test
    @DisplayName("Cuenta suspendida -> NotAuthorizedException (CLAUDE.MD §0.3), aunque el llamador no lo haya chequeado")
    void rechazaSuspendido() {
        UserId participanteId = participante();
        when(loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_PASTILLA_RENACER)).thenReturn(Optional.of(habitoPastilla()));
        when(progresoPort.deParticipante(participanteId))
                .thenReturn(Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service().completarDeHoy(participanteId, RESUMEN))
                .isInstanceOf(NotAuthorizedException.class);
    }
}
