package com.renaser.os.academy.application.services;

import com.renaser.os.academy.application.ports.in.recomendacion.ConsultarRecomendacionDiariaUseCase.Disponible;
import com.renaser.os.academy.application.ports.in.recomendacion.ConsultarRecomendacionDiariaUseCase.NoDisponible;
import com.renaser.os.academy.application.ports.in.recomendacion.ConsultarRecomendacionDiariaUseCase.RecomendacionDiaria;
import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.ProgresoParticipanteAcademy;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.RolParticipante;
import com.renaser.os.academy.application.ports.out.recomendacion.LoadRecomendacionPort;
import com.renaser.os.academy.application.ports.out.recomendacion.RecomendarClasePort;
import com.renaser.os.academy.application.ports.out.recomendacion.SaveRecomendacionPort;
import com.renaser.os.academy.domain.model.curso.AccesoCurso;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.recomendacion.RecomendacionAcademia;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecomendacionServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T12:00:00Z"));
    private static final UserId ACTOR_ID = UserId.of(UUID.randomUUID());

    @Mock
    private LoadRecomendacionPort loadRecomendacionPort;
    @Mock
    private SaveRecomendacionPort saveRecomendacionPort;
    @Mock
    private RecomendarClasePort recomendarClasePort;
    @Mock
    private LoadLeccionPort loadLeccionPort;
    @Mock
    private LoadCursoPort loadCursoPort;
    @Mock
    private ConsultarProgresoParticipanteAcademyPort progresoPort;

    private RecomendacionService service() {
        return new RecomendacionService(loadRecomendacionPort, saveRecomendacionPort, recomendarClasePort,
                loadLeccionPort, loadCursoPort, progresoPort, CLOCK);
    }

    @Test
    @DisplayName("con cache del dia -> la devuelve sin llamar al puerto de IA")
    void conCacheDevuelveSinLlamarIa() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(
                Optional.of(new ProgresoParticipanteAcademy(10, ZoneId.of("America/Lima"), RolParticipante.TRAINEE, false)));
        LocalDate hoy = CLOCK.now().atZone(ZoneId.of("America/Lima")).toLocalDate();
        RecomendacionAcademia cache = new RecomendacionAcademia(ACTOR_ID, hoy, LeccionId.of("l1"), "porque si",
                CLOCK.now());
        when(loadRecomendacionPort.delDia(ACTOR_ID, hoy)).thenReturn(Optional.of(cache));
        Leccion leccion = new Leccion(LeccionId.of("l1"), CursoId.of("c1"), null, "Leccion 1", 0, null, null, null,
                null, null, null, CLOCK.now(), CLOCK.now());
        when(loadLeccionPort.byId(LeccionId.of("l1"))).thenReturn(Optional.of(leccion));
        Curso curso = new Curso(CursoId.of("c1"), "c1", "Curso 1", null, null, 0, true, AccesoCurso.ABIERTO, "skool",
                null, Set.of(), CLOCK.now(), CLOCK.now());
        when(loadCursoPort.byId(CursoId.of("c1"))).thenReturn(Optional.of(curso));

        RecomendacionDiaria resultado = service().recomendacion(ACTOR_ID);

        assertThat(resultado).isInstanceOf(Disponible.class);
        assertThat(((Disponible) resultado).leccionTitulo()).isEqualTo("Leccion 1");
        verify(recomendarClasePort, never()).recomendar(ACTOR_ID);
    }

    @Test
    @DisplayName("sin cache y el puerto de IA (NoOp) no recomienda nada -> NoDisponible")
    void sinCacheYSinRecomendacionIa() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(
                Optional.of(new ProgresoParticipanteAcademy(10, ZoneId.of("America/Lima"), RolParticipante.TRAINEE, false)));
        LocalDate hoy = CLOCK.now().atZone(ZoneId.of("America/Lima")).toLocalDate();
        when(loadRecomendacionPort.delDia(ACTOR_ID, hoy)).thenReturn(Optional.empty());
        when(recomendarClasePort.recomendar(ACTOR_ID)).thenReturn(Optional.empty());

        RecomendacionDiaria resultado = service().recomendacion(ACTOR_ID);

        assertThat(resultado).isInstanceOf(NoDisponible.class);
        verify(saveRecomendacionPort, never()).guardar(org.mockito.ArgumentMatchers.any());
    }
}
