package com.renaser.os.community.domain.model.sobrenombre;

import com.renaser.os.community.domain.model.sobrenombre.SobrenombreRechazadoException.Motivo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** spec 046 FR-004: la forma del sobrenombre y las palabras prohibidas. */
class SobrenombreTest {

    private static Motivo motivo(String crudo) {
        try {
            Sobrenombre.de(crudo, PalabrasProhibidasBase.PALABRAS);
            return null;
        } catch (SobrenombreRechazadoException e) {
            return e.motivo();
        }
    }

    // spec 046 FR-004
    @Test
    void aceptaLetrasConTildesNumerosEspacioPuntoYGuion() {
        Sobrenombre s = Sobrenombre.de("  Fénix de Arequipa ", List.of());
        assertThat(s.texto()).isEqualTo("Fénix de Arequipa");
        assertThat(s.normalizado()).isEqualTo("fenix de arequipa");
        assertThat(Sobrenombre.de("León-23.Ñ", List.of()).normalizado()).isEqualTo("leon-23.n");
    }

    // spec 046 FR-004
    @Test
    void rechazaLargoEspaciosDoblesYSimbolos() {
        assertThat(motivo("Yo")).isEqualTo(Motivo.LARGO);
        assertThat(motivo("Un sobrenombre muy muy largo")).isEqualTo(Motivo.LARGO);
        assertThat(motivo("Fénix  doble")).isEqualTo(Motivo.LARGO);
        assertThat(motivo("Fénix 🔥")).isEqualTo(Motivo.CARACTERES);
        assertThat(motivo("Ana@mail")).isEqualTo(Motivo.CARACTERES);
    }

    // spec 046 FR-004
    @Test
    void rechazaPalabrasProhibidasAunqueVenganConTildesODentroDeOtra() {
        assertThat(motivo("El Admin")).isEqualTo(Motivo.PROHIBIDO);
        assertThat(motivo("Renasér Uno")).isEqualTo(Motivo.PROHIBIDO);
        assertThat(motivo("superidiota")).isEqualTo(Motivo.PROHIBIDO);
        assertThat(motivo("Conchita")).isNull();
        assertThatThrownBy(() -> Sobrenombre.de("Tigre", List.of("tigre"))).isInstanceOf(SobrenombreRechazadoException.class)
                .hasMessage("Elige otro: ese no se puede usar.");
    }

    // spec 046 FR-004 — issue #100
    @Test
    void laTildeCombinableNFDSeAceptaYSeGuardaComoNFC() {
        Sobrenombre s = Sobrenombre.de("Jose\u0301 Luis", List.of());
        assertThat(s.texto()).isEqualTo("José Luis");
        assertThat(s.normalizado()).isEqualTo("jose luis");
    }

    // spec 046 FR-004 — issue #100
    @Test
    void dosEspaciosSeguidosDicenQueUseUnSoloEspacio() {
        assertThatThrownBy(() -> Sobrenombre.de("Fénix  doble", List.of())).isInstanceOf(SobrenombreRechazadoException.class)
                .hasMessage("Usa un solo espacio entre palabras.");
        assertThatThrownBy(() -> Sobrenombre.de("Yo", List.of())).hasMessage("Usa de 3 a 20 letras.");
    }
}
