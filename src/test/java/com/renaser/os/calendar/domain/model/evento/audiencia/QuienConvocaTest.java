package com.renaser.os.calendar.domain.model.evento.audiencia;

import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 044: quién crea eventos, qué partes elige cada rol y qué tipos de evento. */
class QuienConvocaTest {

    // spec 044 FR-001
    @Test
    @DisplayName("FR-001: crean Administración, Alquimista, el líder, el mentor y la guía (en calendar, MENTOR); el aprendiz no")
    void quienCrea() {
        assertThat(QuienConvoca.puedeCrear(RolUsuario.ADMIN)).isTrue();
        assertThat(QuienConvoca.puedeCrear(RolUsuario.ALCHEMIST)).isTrue();
        assertThat(QuienConvoca.puedeCrear(RolUsuario.MENTOR_LEAD)).isTrue();
        assertThat(QuienConvoca.puedeCrear(RolUsuario.MENTOR)).isTrue();
        assertThat(QuienConvoca.puedeCrear(RolUsuario.TRAINEE)).isFalse();
    }

    // spec 044 FR-002
    @Test
    @DisplayName("FR-002: el mentor elige sus alumnos, personas y colores; el líder, además, grupos, mentores y personal; nunca «mis alumnos»")
    void partesPorRol() {
        assertThat(QuienConvoca.partesDe(RolUsuario.MENTOR))
                .containsExactlyInAnyOrder(TipoDeParte.PERSONA, TipoDeParte.MIS_ALUMNOS, TipoDeParte.COLOR);
        assertThat(QuienConvoca.partesDe(RolUsuario.MENTOR_LEAD))
                .containsExactlyInAnyOrder(TipoDeParte.PERSONA, TipoDeParte.GRUPO, TipoDeParte.MENTORES, TipoDeParte.COLOR, TipoDeParte.PERSONAL);
        assertThat(QuienConvoca.partesDe(RolUsuario.ADMIN)).doesNotContain(TipoDeParte.MIS_ALUMNOS).contains(TipoDeParte.PERSONAL);
        assertThat(QuienConvoca.partesDe(RolUsuario.TRAINEE)).isEmpty();
    }

    // spec 044 FR-007
    @Test
    @DisplayName("FR-007 (P9): el mentor y el líder solo crean «Sesión especial» y «Espontáneo», y solo con audiencia elegida")
    void tiposPorRol() {
        assertThat(QuienConvoca.tiposDe(RolUsuario.MENTOR)).containsExactlyInAnyOrder(TipoEvento.SESION_ESPECIAL, TipoEvento.ESPONTANEO);
        assertThat(QuienConvoca.tiposDe(RolUsuario.MENTOR_LEAD)).doesNotContain(TipoEvento.MENTORIA_ALQUIMISTA, TipoEvento.SEMANA_MANIFESTACION);
        assertThat(QuienConvoca.tiposDe(RolUsuario.ADMIN)).contains(TipoEvento.MENTORIA_ALQUIMISTA);
        assertThat(QuienConvoca.soloConAudienciaElegida(RolUsuario.MENTOR_LEAD)).isTrue();
        assertThat(QuienConvoca.soloConAudienciaElegida(RolUsuario.ADMIN)).isFalse();
    }
}
