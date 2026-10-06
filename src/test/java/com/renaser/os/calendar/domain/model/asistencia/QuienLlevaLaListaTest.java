package com.renaser.os.calendar.domain.model.asistencia;

import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Regla del dueño del 2026-10-06 (D-256): «quien creó el evento + Admin, Alquimista y Líder de mentores». */
class QuienLlevaLaListaTest {

    private final UserId yo = UserId.of(UUID.randomUUID());
    private final UserId otro = UserId.of(UUID.randomUUID());

    @ParameterizedTest
    @EnumSource(value = RolUsuario.class, names = {"ADMIN", "ALCHEMIST", "MENTOR_LEAD"})
    @DisplayName("Admin, Alquimista y Líder de mentores, en cualquier evento (también en uno que creó otro)")
    void losTresRolesEnCualquierEvento(RolUsuario rol) {
        assertThat(QuienLlevaLaLista.puede(rol, yo, otro)).isTrue();
        assertThat(QuienLlevaLaLista.puede(rol, yo, null)).as("evento cuyo autor se borró").isTrue();
    }

    @Test
    @DisplayName("el mentor que creó el evento, sí; el que no lo creó, no")
    void elMentorSoloEnLoQueCreo() {
        assertThat(QuienLlevaLaLista.puede(RolUsuario.MENTOR, yo, yo)).isTrue();
        assertThat(QuienLlevaLaLista.puede(RolUsuario.MENTOR, yo, otro)).isFalse();
        assertThat(QuienLlevaLaLista.puede(RolUsuario.MENTOR, yo, null)).isFalse();
    }

    @Test
    @DisplayName("el aprendiz no ve ni pasa lista (tampoco ve su propia asistencia)")
    void elAprendizNunca() {
        assertThat(QuienLlevaLaLista.puede(RolUsuario.TRAINEE, yo, otro)).isFalse();
    }
}
