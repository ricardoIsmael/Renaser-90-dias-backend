package com.renaser.os;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TestcontainersCloudGuardTest {
    @Test
    void admiteElRuntimeCloud() {
        assertThatCode(() -> TestcontainersConfiguration.requireCloudVersion("70+testcontainerscloud"))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaDockerLocalYRuntimeDesconocidoAntesDeCrearContenedores() {
        assertThatThrownBy(() -> TestcontainersConfiguration.requireCloudVersion("29.8.0"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TestcontainersConfiguration.requireCloudVersion(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
