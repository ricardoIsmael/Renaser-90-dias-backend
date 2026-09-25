package com.renaser.os.rag.domain.model.conversacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-270: ningun UUID de la base llega a la pantalla ni se guarda, aunque el modelo lo escriba y
 * aunque llegue partido entre dos pedazos del stream.
 */
class FiltroDeIdentificadoresTest {

    private static final String ID = "899a2151-e98c-4b61-a46c-b55134240d17";

    private static String porPedazos(List<String> pedazos) {
        FiltroDeIdentificadores filtro = new FiltroDeIdentificadores();
        StringBuilder salida = new StringBuilder();
        pedazos.forEach(pedazo -> salida.append(filtro.pasar(pedazo)));
        return salida.append(filtro.cerrar()).toString();
    }

    @Test
    @DisplayName("un UUID entero, con o sin comillas invertidas, se tapa")
    void uuidEntero() {
        assertThat(porPedazos(List.of("- Despertar: `" + ID + "`\n- Leer: " + ID + ".")))
                .isEqualTo("- Despertar: (dato interno)\n- Leer: (dato interno).")
                .doesNotContain("899a2151");
    }

    @Test
    @DisplayName("un UUID partido en varios pedazos del stream no se filtra a medias")
    void uuidPartido() {
        String salida = porPedazos(List.of("Tu habito: 899a", "2151-e98c-4b6", "1-a46c-b5513", "4240d17 listo"));

        assertThat(salida).isEqualTo("Tu habito: (dato interno) listo").doesNotContain("899a").doesNotContain("d17");
    }

    @Test
    @DisplayName("el texto normal sale igual, aunque tenga palabras que parecen hex como 'cada' o 'de'")
    void textoNormalIntacto() {
        String texto = "Vas en el dia 18 de 90. Cada paso cuenta, dale con fe.";

        assertThat(porPedazos(List.of("Vas en el dia 18 de 90. Cada ", "paso cuenta, dale con fe."))).isEqualTo(texto);
    }

    @Test
    @DisplayName("lo retenido se entrega al cerrar, y un texto completo se tapa de una vez")
    void cerrarYTextoCompleto() {
        FiltroDeIdentificadores filtro = new FiltroDeIdentificadores();

        assertThat(filtro.pasar("abc")).isEmpty();
        assertThat(filtro.cerrar()).isEqualTo("abc");
        assertThat(FiltroDeIdentificadores.taparEn("id " + ID)).isEqualTo("id (dato interno)");
        assertThat(FiltroDeIdentificadores.taparEn(null)).isNull();
    }
}
