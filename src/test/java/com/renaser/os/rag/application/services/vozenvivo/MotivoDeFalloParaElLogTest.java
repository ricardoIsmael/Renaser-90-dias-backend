package com.renaser.os.rag.application.services.vozenvivo;

import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-475: el log de la voz en vivo decia "-> fallo (55 ms)" sin la causa. Ahora lleva la primera
 * oracion del motivo, sin los nombres entre comillas.
 */
class MotivoDeFalloParaElLogTest {

    @Test
    @DisplayName("un fallo deja su primera oracion, con los nombres entre comillas tapados")
    void primeraOracionSinNombres() {
        String motivo = MotivoDeFalloParaElLog.de(ResultadoHerramienta.fallo("'Rezar por mi hija' ya esta a las "
                + "06:00 ese dia: no hay nada que cambiar. Dile eso."));

        assertThat(motivo).isEqualTo("'…' ya esta a las 06:00 ese dia: no hay nada que cambiar.");
    }

    @Test
    @DisplayName("un exito no deja nada, y un motivo largo se corta")
    void exitoYLargo() {
        assertThat(MotivoDeFalloParaElLog.de(ResultadoHerramienta.exito("Propuesta creada"))).isEmpty();
        assertThat(MotivoDeFalloParaElLog.de(ResultadoHerramienta.fallo("x".repeat(500))))
                .hasSize(MotivoDeFalloParaElLog.LARGO_MAXIMO + 1);
    }
}
