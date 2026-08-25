package com.renaser.os.academy.domain.model.asignacion;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsignacionCursoTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T12:00:00Z");
    private static final CursoId CURSO_ID = CursoId.of("curso-1");
    private static final UserId USUARIO_ID = UserId.of(UUID.randomUUID());
    private static final GrupoId GRUPO_ID = GrupoId.of(1L);

    @Test
    @DisplayName("arco exclusivo: usuario y grupo a la vez -> invalido")
    void usuarioYGrupoALaVezInvalido() {
        assertThatThrownBy(() -> new AsignacionCurso(null, CURSO_ID, USUARIO_ID, GRUPO_ID, null, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arco exclusivo");
    }

    @Test
    @DisplayName("arco exclusivo: ni usuario ni grupo -> invalido")
    void niUsuarioNiGrupoInvalido() {
        assertThatThrownBy(() -> new AsignacionCurso(null, CURSO_ID, null, null, null, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arco exclusivo");
    }

    @Test
    @DisplayName("sin desde/hasta y no revocada -> vigente")
    void sinFechasNoRevocadaVigente() {
        AsignacionCurso asignacion = new AsignacionCurso(AsignacionCursoId.of(1L), CURSO_ID, USUARIO_ID, null, null, null, null, null, AHORA);
        assertThat(asignacion.vigente(AHORA)).isTrue();
        assertThat(asignacion.esDirecta()).isTrue();
    }

    @Test
    @DisplayName("revocada -> nunca vigente")
    void revocadaNuncaVigente() {
        AsignacionCurso asignacion = new AsignacionCurso(AsignacionCursoId.of(1L), CURSO_ID, USUARIO_ID, null, null, null, AHORA, null, AHORA);
        assertThat(asignacion.vigente(AHORA.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("todavia no empieza (desde en el futuro) -> no vigente")
    void desdeEnElFuturoNoVigente() {
        AsignacionCurso asignacion = new AsignacionCurso(AsignacionCursoId.of(1L), CURSO_ID, USUARIO_ID, null, AHORA.plusSeconds(60), null,
                null, null, AHORA);
        assertThat(asignacion.vigente(AHORA)).isFalse();
    }

    @Test
    @DisplayName("ya vencio (hasta en el pasado) -> no vigente")
    void hastaEnElPasadoNoVigente() {
        AsignacionCurso asignacion = new AsignacionCurso(AsignacionCursoId.of(1L), CURSO_ID, USUARIO_ID, null, null, AHORA.minusSeconds(60),
                null, null, AHORA);
        assertThat(asignacion.vigente(AHORA)).isFalse();
    }

    @Test
    @DisplayName("dentro de la ventana desde/hasta -> vigente")
    void dentroDeLaVentanaVigente() {
        AsignacionCurso asignacion = new AsignacionCurso(AsignacionCursoId.of(1L), CURSO_ID, null, GRUPO_ID, AHORA.minusSeconds(60),
                AHORA.plusSeconds(60), null, null, AHORA);
        assertThat(asignacion.vigente(AHORA)).isTrue();
        assertThat(asignacion.esDirecta()).isFalse();
    }
}
