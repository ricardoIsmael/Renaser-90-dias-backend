package com.renaser.os.academy.application.services;

import com.renaser.os.academy.application.ports.out.asignacion.LoadAsignacionCursoPort;
import com.renaser.os.academy.application.ports.out.asignacion.LoadMiembroGrupoPort;
import com.renaser.os.academy.domain.model.asignacion.AsignacionCurso;
import com.renaser.os.academy.domain.model.asignacion.AsignacionCursoId;
import com.renaser.os.academy.domain.model.asignacion.GrupoId;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccesoCursoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T12:00:00Z"));
    private static final CursoId CURSO_ID = CursoId.of("curso-1");

    @Mock
    private LoadAsignacionCursoPort loadAsignacionCursoPort;
    @Mock
    private LoadMiembroGrupoPort loadMiembroGrupoPort;

    private AccesoCursoService service;

    @BeforeEach
    void setUp() {
        service = new AccesoCursoService(loadAsignacionCursoPort, loadMiembroGrupoPort, CLOCK);
    }

    @Test
    @DisplayName("usuariosConAcceso: une asignaciones directas vigentes con miembros de grupos vigentes")
    void uneDirectasYPorGrupo() {
        UserId usuarioDirecto = UserId.of(UUID.randomUUID());
        UserId usuarioDeGrupo = UserId.of(UUID.randomUUID());
        UserId usuarioRevocado = UserId.of(UUID.randomUUID());
        GrupoId grupoId = GrupoId.of(1L);

        AsignacionCurso directa = new AsignacionCurso(AsignacionCursoId.of(1L), CURSO_ID, usuarioDirecto, null, null, null, null, null,
                CLOCK.now());
        AsignacionCurso porGrupo = new AsignacionCurso(AsignacionCursoId.of(2L), CURSO_ID, null, grupoId, null, null, null, null,
                CLOCK.now());
        AsignacionCurso revocada = new AsignacionCurso(AsignacionCursoId.of(3L), CURSO_ID, usuarioRevocado, null, null, null, CLOCK.now(),
                null, CLOCK.now());

        when(loadAsignacionCursoPort.porCurso(CURSO_ID)).thenReturn(List.of(directa, porGrupo, revocada));
        when(loadMiembroGrupoPort.usuariosDeGrupos(Set.of(grupoId))).thenReturn(Set.of(usuarioDeGrupo));

        Set<UserId> resultado = service.usuariosConAcceso(CURSO_ID.value());

        assertThat(resultado).containsExactlyInAnyOrder(usuarioDirecto, usuarioDeGrupo);
        assertThat(service.tieneAcceso(usuarioRevocado, CURSO_ID.value())).isFalse();
        assertThat(service.tieneAcceso(usuarioDirecto, CURSO_ID.value())).isTrue();
    }

    @Test
    @DisplayName("sin asignaciones -> conjunto vacio, nunca consulta grupos")
    void sinAsignacionesConjuntoVacio() {
        when(loadAsignacionCursoPort.porCurso(CURSO_ID)).thenReturn(List.of());

        assertThat(service.usuariosConAcceso(CURSO_ID.value())).isEmpty();
    }
}
