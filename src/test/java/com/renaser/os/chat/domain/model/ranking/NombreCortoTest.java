package com.renaser.os.chat.domain.model.ranking;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Primer nombre + inicial del apellido (D-262). */
class NombreCortoTest {

    @ParameterizedTest(name = "«{0}» → «{1}»")
    @CsvSource(delimiter = '|', value = {
            "Liz Mendoza | Liz M.",
            "Liz Mendoza Ruiz | Liz M.",
            "Liz Mariela Mendoza Ruiz | Liz M.",
            "Miguel Ángel Alva Soto | Miguel A.",
            "  liz   mendoza  | Liz M.",
            "José de la Cruz | José C.",
            "María de los Ángeles Pérez Ñahui | María P.",
            "Rosa | Rosa",
            "álvaro ñahui | Álvaro Ñ.",
    })
    void nombreCorto(String completo, String esperado) {
        assertThat(NombreCorto.de(completo)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "'   '"})
    void sinNombre(String completo) {
        assertThat(NombreCorto.de(completo)).isEqualTo("Aprendiz");
        assertThat(NombreCorto.de(null)).isEqualTo("Aprendiz");
    }
}
