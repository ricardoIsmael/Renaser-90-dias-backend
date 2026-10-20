package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Sin la marca de la fase 2 (R-1, spec 005) ninguna cuenta es de prueba; al integrarla este test cambia con ella. */
class CuentasDePruebaFinderServiceTest {

    private final CuentasDePruebaFinderService finder = new CuentasDePruebaFinderService();

    @Test
    @DisplayName("hoy ninguna cuenta es de prueba, ni preguntando en lote ni de a una")
    void ningunaEsDePrueba() {
        UserId ana = UserId.of(UUID.randomUUID());
        UserId luis = UserId.of(UUID.randomUUID());

        assertThat(finder.dePruebaEntre(List.of(ana, luis))).isEmpty();
        assertThat(finder.dePruebaEntre(List.of())).isEmpty();
        assertThat(finder.esDePrueba(ana)).isFalse();
    }
}
