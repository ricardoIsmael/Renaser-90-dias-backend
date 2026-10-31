package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** En qué grupo se mira a quien está en dos, en uno o en ninguno (spec 021). */
class GrupoDeLaPersonaTest {

    private static final UserId MENTORA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final GrupoDeLaPersona FENIX = new GrupoDeLaPersona(
            UUID.fromString("00000000-0000-0000-0000-00000000f001"), "Fénix", MENTORA, false);
    private static final GrupoDeLaPersona OCASO = new GrupoDeLaPersona(
            UUID.fromString("00000000-0000-0000-0000-00000000f002"), "Ocaso", MENTORA, false);
    private static final GrupoDeLaPersona SIN_MENTOR = new GrupoDeLaPersona(
            UUID.fromString("00000000-0000-0000-0000-00000000f003"), "Sin mentor", null, false);
    private static final GrupoDeLaPersona RECEPCION = new GrupoDeLaPersona(
            UUID.fromString("00000000-0000-0000-0000-00000000f004"), "Recepción", null, true);

    @Test
    @DisplayName("sin grupos no hay grupo")
    void ninguno() {
        assertThat(GrupoDeLaPersona.elegir(List.of(), null)).isEmpty();
    }

    @Test
    @DisplayName("el grupo regular con mentor gana al regular sin mentor y a la recepción, esté en el orden que esté")
    void conMentorPrimero() {
        assertThat(GrupoDeLaPersona.elegir(List.of(RECEPCION, SIN_MENTOR, FENIX), null)).contains(FENIX);
        assertThat(GrupoDeLaPersona.elegir(List.of(RECEPCION, SIN_MENTOR), null)).contains(SIN_MENTOR);
        assertThat(GrupoDeLaPersona.elegir(List.of(RECEPCION), null)).contains(RECEPCION);
    }

    @Test
    @DisplayName("entre dos con mentor gana su grupo principal; si no, el de nombre menor")
    void principalYNombre() {
        assertThat(GrupoDeLaPersona.elegir(List.of(FENIX, OCASO), OCASO.grupoId())).contains(OCASO);
        assertThat(GrupoDeLaPersona.elegir(List.of(OCASO, FENIX), null)).contains(FENIX);
    }

    @Test
    @DisplayName("el principal no le gana a la categoría: una recepción principal no pasa delante de un grupo con mentor")
    void elPrincipalNoGanaALaCategoria() {
        assertThat(GrupoDeLaPersona.elegir(List.of(RECEPCION, FENIX), RECEPCION.grupoId())).contains(FENIX);
    }

    @Test
    @DisplayName("solo un grupo regular con mentor tiene a quién avisarle; la recepción no, aunque trajera un mentor")
    void conMentor() {
        assertThat(FENIX.conMentor()).isTrue();
        assertThat(SIN_MENTOR.conMentor()).isFalse();
        assertThat(RECEPCION.conMentor()).isFalse();
        assertThat(new GrupoDeLaPersona(RECEPCION.grupoId(), "Recepción", MENTORA, true).conMentor()).isFalse();
    }

    @Test
    @DisplayName("un grupo sin id no existe")
    void sinId() {
        assertThatThrownBy(() -> new GrupoDeLaPersona(null, "x", null, false)).isInstanceOf(NullPointerException.class);
    }
}
