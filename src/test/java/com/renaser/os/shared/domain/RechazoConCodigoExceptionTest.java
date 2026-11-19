package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Un rechazo con motivo: 400, 409 y, desde la spec 056, 404 (una persona que no sigue su programa no es una que no existe). */
class RechazoConCodigoExceptionTest {

    @Test
    @DisplayName("spec 056 FR-022: un 404 con motivo lleva su código y su mensaje")
    void noEncontradoConMotivo() {
        RechazoConCodigoException e = RechazoConCodigoException.noEncontrado("SIN_PROGRAMA", "Esta persona no sigue su programa.");

        assertThat(e.estado()).isEqualTo(404);
        assertThat(e.codigo()).isEqualTo("SIN_PROGRAMA");
        assertThat(e).hasMessage("Esta persona no sigue su programa.");
    }

    @Test
    @DisplayName("los 400 y 409 de la spec 055 siguen igual")
    void losDeLaSpec055() {
        assertThat(RechazoConCodigoException.invalido("RANGO_INVALIDO", "x").estado()).isEqualTo(400);
        assertThat(RechazoConCodigoException.conflicto("NOTA_DE_OTRO_DIA", "x").estado()).isEqualTo(409);
    }

    @Test
    @DisplayName("cualquier otro estado no se puede construir: el 403 con motivo es NotAuthorizedException")
    void otrosEstadosNo() {
        assertThatThrownBy(() -> new RechazoConCodigoException(403, "X", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RechazoConCodigoException(500, "X", "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
