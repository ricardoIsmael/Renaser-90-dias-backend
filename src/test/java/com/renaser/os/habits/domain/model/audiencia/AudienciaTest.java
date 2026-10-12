package com.renaser.os.habits.domain.model.audiencia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudienciaTest {

    private static final CodigoDeRol MENTOR = CodigoDeRol.of("MENTOR");
    private static final CodigoDeRol APRENDIZ = CodigoDeRol.of("TRAINEE");

    @Test
    @DisplayName("sin roles es para todos, incluido un rol que no existe todavía")
    void vaciaEsParaTodos() {
        assertThat(Audiencia.PARA_TODOS.paraTodos()).isTrue();
        assertThat(Audiencia.PARA_TODOS.aplicaA(APRENDIZ)).isTrue();
        assertThat(Audiencia.PARA_TODOS.aplicaA(CodigoDeRol.of("COACH"))).isTrue();
    }

    @Test
    @DisplayName("con roles, aplica solo a esos")
    void conRolesAplicaSoloAEsos() {
        Audiencia soloMentores = Audiencia.de(Set.of(MENTOR));

        assertThat(soloMentores.paraTodos()).isFalse();
        assertThat(soloMentores.aplicaA(MENTOR)).isTrue();
        assertThat(soloMentores.aplicaA(APRENDIZ)).isFalse();
    }

    @Test
    @DisplayName("un rol nuevo funciona sin tocar el dominio (US5)")
    void rolNuevoFunciona() {
        CodigoDeRol coach = CodigoDeRol.of("COACH");

        assertThat(Audiencia.de(Set.of(coach)).aplicaA(coach)).isTrue();
    }

    @Test
    @DisplayName("el código de rol rechaza vacíos y minúsculas")
    void codigoInvalido() {
        assertThatThrownBy(() -> CodigoDeRol.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CodigoDeRol.of("mentor")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CodigoDeRol.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dos audiencias con los mismos roles son iguales, sin importar el orden")
    void igualdad() {
        assertThat(Audiencia.de(Set.of(MENTOR, APRENDIZ))).isEqualTo(Audiencia.de(Set.of(APRENDIZ, MENTOR)));
    }
}
