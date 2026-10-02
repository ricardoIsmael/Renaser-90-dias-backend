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

    /** La clave de soporte NO sale al wire: `clave_directa` no esta en la respuesta.
     *
     * <p><b>Corregido 2026-10-02 (D-244).</b> Decia ademas que «el id del aprendiz que lleva adentro tampoco
     * tiene por que salir por ahi». Ahora sale, con nombre ({@code supportTraineeId}): quien atiende soporte lo
     * necesita para ver el pedido de emergencia de esa persona desde el chat. No filtra nada: un soporte solo
     * lo ven el propio aprendiz y ADMIN/ALCHEMIST. Ver {@link #elSoporteDiceDeQuienEs}. */
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

    @Test
    @DisplayName("supportTraineeId: el aprendiz del soporte; null en grupo, comunidad y 1 a 1")
    void elSoporteDiceDeQuienEs() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());

        assertThat(ConversacionResponse.from(Conversacion.crearSoporte(ID, aprendiz, "Ana - Formación Renaser", AHORA))
                .supportTraineeId()).isEqualTo(aprendiz.value().toString());
        assertThat(ConversacionResponse.from(Conversacion.crearGlobal(ID, AHORA)).supportTraineeId()).isNull();
        assertThat(ConversacionResponse.from(Conversacion.crearCelula(ID, UUID.randomUUID(), AHORA)).supportTraineeId())
                .isNull();
        assertThat(ConversacionResponse.from(Conversacion.crearDirecta(ID, Conversacion.claveDirectaDe(aprendiz, otro),
                AHORA)).supportTraineeId()).isNull();
    }

    /** D-205: solo un soporte trae la ruta de su foto; lo demás usa la tarjeta sin nombre de la app. */
    @Test
    @DisplayName("photoPath: la ruta de la foto solo en un soporte; null en grupo, comunidad y 1 a 1")
    void laRutaDeLaFotoSoloEnUnSoporte() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());

        assertThat(ConversacionResponse.from(Conversacion.crearSoporte(ID, aprendiz, "Ana - Formación Renaser", AHORA))
                .photoPath()).isEqualTo("/api/v1/chat/conversations/" + ID + "/foto");
        assertThat(ConversacionResponse.from(Conversacion.crearGlobal(ID, AHORA)).photoPath()).isNull();
        assertThat(ConversacionResponse.from(Conversacion.crearCelula(ID, UUID.randomUUID(), AHORA)).photoPath()).isNull();
        assertThat(ConversacionResponse.from(Conversacion.crearDirecta(ID, Conversacion.claveDirectaDe(aprendiz, otro), AHORA))
                .photoPath()).isNull();
    }

    /**
     * D-212: un grupo con foto propia trae la ruta con {@code ?v=} y los milisegundos de cuándo cambió; con
     * otra foto, otra ruta, y el teléfono la baja sin esperar el día del caché. Sin foto propia, nada.
     */
    @Test
    @DisplayName("photoPath de un grupo: solo con foto propia, y con ?v= de cuándo cambió")
    void laRutaDeLaFotoDeUnGrupoConFotoPropia() {
        Conversacion grupo = Conversacion.crearCelula(ID, UUID.randomUUID(), AHORA);
        Instant cambiada = Instant.parse("2026-09-27T15:00:00.123Z");

        assertThat(ConversacionResponse.from(grupo, cambiada, null).photoPath())
                .isEqualTo("/api/v1/chat/conversations/" + ID + "/foto?v=" + cambiada.toEpochMilli());
        assertThat(ConversacionResponse.from(grupo, null, null).photoPath()).as("usa la foto de Renaser").isNull();
        assertThat(ConversacionResponse.from(Conversacion.crearGlobal(ID, AHORA), cambiada, null).photoPath())
                .as("la comunidad nunca").isNull();
    }
}
