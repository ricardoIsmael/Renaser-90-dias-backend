package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-121: publicar en el Muro cierra el habito de post diario y paga sus puntos. Todos estos tests
 * fallan contra el codigo anterior por la razon mas simple posible — antes no existia nadie que
 * escuchara {@code PublicacionCreadaEvent}, asi que el registro se quedaba PENDIENTE hasta que el
 * barrido nocturno lo expiraba.
 */
@ExtendWith(MockitoExtension.class)
class PostDiarioComunidadHabitoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private CompletarRegistroUseCase completarRegistroUseCase;

    private PostDiarioComunidadHabitoService service() {
        return new PostDiarioComunidadHabitoService(loadHabitoPort, loadRegistroPort, progresoPort,
                completarRegistroUseCase);
    }

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    private static Habito habitoPostDiario() {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null,
                "POST DIARIO EN COMUNIDAD", null, TipoHabito.CHECKBOX, "CONSCIENCIA", "COMMUNITY_POST",
                PoliticaPostDiarioComunidad.CLAVE_SISTEMA, ExigenciaEvidencia.OPCIONAL, false, true, false, false,
                null, null, null, null, true, CLOCK.now(), CLOCK.now());
    }

    private static RegistroHabito registroDe(UserId participanteId, HabitoId habitoId, LocalDate fecha) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participanteId, habitoId, fecha, 5,
                TipoDia.DISCIPLINA, false, CLOCK.now());
    }

    private void mockParticipanteEnLima(UserId participanteId) {
        when(progresoPort.deParticipante(participanteId)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false)));
    }

    @Test
    @DisplayName("publicar en el Muro cierra el registro del habito de post diario, con el gesto generico")
    void publicarCierraElHabitoDelDia() {
        UserId autor = participante();
        Habito habito = habitoPostDiario();
        RegistroHabito registro = registroDe(autor, habito.id(), LocalDate.of(2026, 8, 24));

        mockParticipanteEnLima(autor);
        when(loadHabitoPort.porClaveSistema(PoliticaPostDiarioComunidad.CLAVE_SISTEMA))
                .thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(autor, habito.id(), LocalDate.of(2026, 8, 24)))
                .thenReturn(Optional.of(registro));

        service().alPublicarEnElMuro(autor, Instant.parse("2026-08-24T15:00:00Z"));

        // GENERICO y no PROPIO_DEL_HABITO: se quiere que PoliticaPostDiarioComunidad vuelva a
        // comprobar contra `publicaciones_muro` que la publicacion existe. Sin atajos por venir
        // de un evento.
        verify(completarRegistroUseCase).completar(
                new CompletarRegistroCommand(autor, registro.id(), null, null, GestoCompletar.GENERICO));
    }

    /**
     * La prueba que exige la regla 02-tiempo-zonas-y-schedulers: una hora UTC que cae en el dia
     * local ANTERIOR. Un {@code FixedClock} a las 10:00 UTC cae el mismo dia calendario en Lima y
     * habria escondido el bug (E-91).
     */
    @Test
    @DisplayName("el dia es el del PARTICIPANTE: publicar a las 02:00 UTC cierra el habito del dia anterior en Lima")
    void elDiaSeResuelveEnLaZonaDelParticipante() {
        UserId autor = participante();
        Habito habito = habitoPostDiario();
        // 02:00 UTC del 25 son las 21:00 del 24 en Lima (UTC-5): el habito que se cierra es el del 24.
        LocalDate diaEnLima = LocalDate.of(2026, 8, 24);
        RegistroHabito registro = registroDe(autor, habito.id(), diaEnLima);

        mockParticipanteEnLima(autor);
        when(loadHabitoPort.porClaveSistema(PoliticaPostDiarioComunidad.CLAVE_SISTEMA))
                .thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(autor, habito.id(), diaEnLima))
                .thenReturn(Optional.of(registro));

        service().alPublicarEnElMuro(autor, Instant.parse("2026-08-25T02:00:00Z"));

        verify(loadRegistroPort).porParticipanteHabitoYFecha(autor, habito.id(), diaEnLima);
        verify(completarRegistroUseCase).completar(any());
    }

    /**
     * La guarda que sostiene el outbox: {@code republish-outstanding-events-on-restart} esta en
     * {@code true}, asi que el mismo evento puede volver a llegar tras un reinicio. Y dos
     * publicaciones el mismo dia son el caso normal, no el borde.
     */
    @Test
    @DisplayName("un registro ya COMPLETADO no se vuelve a completar: ni segunda publicacion ni reentrega pagan dos veces")
    void noPagaDosVecesSiElRegistroYaEstaCompletado() {
        UserId autor = participante();
        Habito habito = habitoPostDiario();
        RegistroHabito yaCompletado = registroDe(autor, habito.id(), LocalDate.of(2026, 8, 24));
        yaCompletado.completar(10, null, null, null, CLOCK.now());

        mockParticipanteEnLima(autor);
        when(loadHabitoPort.porClaveSistema(PoliticaPostDiarioComunidad.CLAVE_SISTEMA))
                .thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(autor, habito.id(), LocalDate.of(2026, 8, 24)))
                .thenReturn(Optional.of(yaCompletado));

        service().alPublicarEnElMuro(autor, Instant.parse("2026-08-24T15:00:00Z"));
        service().alPublicarEnElMuro(autor, Instant.parse("2026-08-24T18:00:00Z"));

        assertThat(yaCompletado.puntosOtorgados()).isEqualTo(10);
        verify(completarRegistroUseCase, never()).completar(any());
    }

    @Test
    @DisplayName("quien publica sin ser participante del programa (mentor, admin) no dispara nada")
    void autorSinParticipacionNoDisparaNada() {
        UserId autor = participante();
        when(loadHabitoPort.porClaveSistema(PoliticaPostDiarioComunidad.CLAVE_SISTEMA))
                .thenReturn(Optional.of(habitoPostDiario()));
        when(progresoPort.deParticipante(autor)).thenReturn(Optional.empty());

        service().alPublicarEnElMuro(autor, Instant.parse("2026-08-24T15:00:00Z"));

        verify(loadRegistroPort, never()).porParticipanteHabitoYFecha(any(), any(), any());
        verify(completarRegistroUseCase, never()).completar(any());
    }

    @Test
    @DisplayName("una cuenta suspendida no cierra el habito publicando")
    void cuentaSuspendidaNoCierraElHabito() {
        UserId autor = participante();
        when(loadHabitoPort.porClaveSistema(PoliticaPostDiarioComunidad.CLAVE_SISTEMA))
                .thenReturn(Optional.of(habitoPostDiario()));
        when(progresoPort.deParticipante(autor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, true)));

        service().alPublicarEnElMuro(autor, Instant.parse("2026-08-24T15:00:00Z"));

        verify(completarRegistroUseCase, never()).completar(any());
    }

    @Test
    @DisplayName("sin registro para ese dia (habito pausado o fuera del plan) no se completa nada")
    void sinRegistroEseDiaNoCompletaNada() {
        UserId autor = participante();
        Habito habito = habitoPostDiario();
        mockParticipanteEnLima(autor);
        when(loadHabitoPort.porClaveSistema(PoliticaPostDiarioComunidad.CLAVE_SISTEMA))
                .thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(autor, habito.id(), LocalDate.of(2026, 8, 24)))
                .thenReturn(Optional.empty());

        service().alPublicarEnElMuro(autor, Instant.parse("2026-08-24T15:00:00Z"));

        verify(completarRegistroUseCase, never()).completar(any());
    }

    @Test
    @DisplayName("sin el habito en el catalogo de este entorno no se consulta nada mas")
    void sinHabitoEnCatalogoNoHaceNada() {
        UserId autor = participante();
        when(loadHabitoPort.porClaveSistema(PoliticaPostDiarioComunidad.CLAVE_SISTEMA)).thenReturn(Optional.empty());

        service().alPublicarEnElMuro(autor, Instant.parse("2026-08-24T15:00:00Z"));

        verify(progresoPort, never()).deParticipante(any());
        verify(completarRegistroUseCase, never()).completar(any());
    }
}
