package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contrato del wire (D-36): los enums viven en espanol en dominio y base, y la app publicada
 * los ve en ingles. La traduccion es la frontera con una aplicacion que ya esta escrita contra
 * estas cuatro palabras — cambiarlas por descuido no lo nota ningun compilador, lo nota el
 * telefono de alguien.
 */
class ConversacionResponseTest {

    private static final Instant AHORA = Instant.parse("2026-09-16T10:00:00Z");
    private static final ConversacionId ID = ConversacionId.of(UUID.randomUUID());

    @Test
    @DisplayName("SOPORTE viaja como SUPPORT, y los otros tres no se mueven")
    void losCuatroTiposViajanConSuNombreEnIngles() {
        assertThat(ConversacionResponse.toWireTipo(TipoConversacion.CELULA)).isEqualTo("CELL");
        assertThat(ConversacionResponse.toWireTipo(TipoConversacion.DIRECTA)).isEqualTo("DIRECT");
        assertThat(ConversacionResponse.toWireTipo(TipoConversacion.GLOBAL)).isEqualTo("GLOBAL");
        assertThat(ConversacionResponse.toWireTipo(TipoConversacion.SOPORTE)).isEqualTo("SUPPORT");
    }

    /** La clave de soporte NO sale al wire: `clave_directa` no esta en la respuesta, y el id del
     * aprendiz que lleva adentro tampoco tiene por que salir por ahi. */
    @Test
    @DisplayName("la respuesta de un soporte lleva su nombre y ninguna celula")
    void laRespuestaDeUnSoporteLlevaNombreYNingunaCelula() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        ConversacionResponse respuesta = ConversacionResponse.from(
                Conversacion.crearSoporte(ID, aprendiz, "Soporte - Ana Perez", AHORA));

        assertThat(respuesta.type()).isEqualTo("SUPPORT");
        assertThat(respuesta.nombre()).isEqualTo("Soporte - Ana Perez");
        assertThat(respuesta.celulaId()).isNull();
    }
}
