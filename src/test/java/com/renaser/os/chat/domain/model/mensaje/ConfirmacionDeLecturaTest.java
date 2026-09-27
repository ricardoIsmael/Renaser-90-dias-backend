package com.renaser.os.chat.domain.model.mensaje;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La doble marca de leído (D-208), sin Spring ni base: cuándo un mensaje propio pasa de ✓ a ✓✓ en un
 * 1 a 1, en un grupo y en un soporte, y por qué en la comunidad nunca.
 *
 * <p>Los instantes son de una tarde en Lima (UTC−5) a propósito, y uno cae a las 00:10 UTC, que en
 * Lima es el día anterior (regla 02 §3): la marca compara instantes, no fechas, y no puede cambiar
 * porque el día UTC ya haya dado la vuelta.
 */
class ConfirmacionDeLecturaTest {

    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId LUIS = UserId.of(UUID.randomUUID());
    private static final UserId MARTA = UserId.of(UUID.randomUUID());

    /** 19:00 en Lima. */
    private static final Instant ESCRIBE_ANA = Instant.parse("2026-09-27T00:00:00Z");

    private static final ConversacionId ID = ConversacionId.of(UUID.randomUUID());
    private static final Conversacion DIRECTA = Conversacion.crearDirecta(ID, Conversacion.claveDirectaDe(ANA, LUIS),
            ESCRIBE_ANA.minusSeconds(3600));
    private static final Conversacion GRUPO = Conversacion.crearCelula(ID, UUID.randomUUID(), ESCRIBE_ANA.minusSeconds(3600));
    private static final Conversacion COMUNIDAD = Conversacion.crearGlobal(ID, ESCRIBE_ANA.minusSeconds(3600));
    private static final Conversacion SOPORTE = Conversacion.crearSoporte(ID, ANA, "Ana – Formación Renaser",
            ESCRIBE_ANA.minusSeconds(3600));

    private static Participante marca(UserId quien, Instant leyoHasta) {
        return Participante.rehydrate(ID, quien, leyoHasta, ESCRIBE_ANA.minusSeconds(3600));
    }

    private static Mensaje deAna(Instant cuando) {
        return Mensaje.escribir(MensajeId.of(UUID.randomUUID()), ID, ANA, TipoMensaje.TEXTO, "hola", null, null, null,
                null, null, null, cuando);
    }

    @Nested
    @DisplayName("en un 1 a 1")
    class UnoAUno {

        @Test
        @DisplayName("el mensaje de Ana es ✓✓ cuando Luis leyó después de que ella escribiera, ✓ si leyó antes")
        void leidoCuandoElOtroLeyoDespues() {
            // Ana quedó marcada al escribir (MensajeService.enviar); Luis abrió el chat 10 min después.
            ConfirmacionDeLectura leyo = ConfirmacionDeLectura.de(DIRECTA,
                    List.of(marca(ANA, ESCRIBE_ANA), marca(LUIS, Instant.parse("2026-09-27T00:10:00Z"))));
            ConfirmacionDeLectura noLeyo = ConfirmacionDeLectura.de(DIRECTA,
                    List.of(marca(ANA, ESCRIBE_ANA), marca(LUIS, ESCRIBE_ANA.minusSeconds(60))));

            assertThat(leyo.estadoPara(deAna(ESCRIBE_ANA), ANA)).contains(EstadoDeEntrega.LEIDO);
            assertThat(noLeyo.estadoPara(deAna(ESCRIBE_ANA), ANA)).contains(EstadoDeEntrega.ENVIADO);
        }

        @Test
        @DisplayName("leer en el mismo instante en que se escribió cuenta como leído")
        void elMismoInstanteCuenta() {
            ConfirmacionDeLectura confirmacion = ConfirmacionDeLectura.de(DIRECTA,
                    List.of(marca(ANA, ESCRIBE_ANA), marca(LUIS, ESCRIBE_ANA)));

            assertThat(confirmacion.estadoPara(deAna(ESCRIBE_ANA), ANA)).contains(EstadoDeEntrega.LEIDO);
        }

        @Test
        @DisplayName("si Luis nunca abrió la conversación (marca nula), nada de Ana está leído")
        void sinMarcaNoLeyoNada() {
            ConfirmacionDeLectura confirmacion = ConfirmacionDeLectura.de(DIRECTA,
                    List.of(marca(ANA, ESCRIBE_ANA), marca(LUIS, null)));

            assertThat(confirmacion.leidoPorTodosHasta()).isEmpty();
            assertThat(confirmacion.estadoPara(deAna(ESCRIBE_ANA), ANA)).contains(EstadoDeEntrega.ENVIADO);
        }

        @Test
        @DisplayName("si del otro lado no queda nadie que pueda leer, nadie lo leyó")
        void sinNadieDelOtroLado() {
            ConfirmacionDeLectura confirmacion = ConfirmacionDeLectura.de(DIRECTA, List.of(marca(ANA, ESCRIBE_ANA)));

            assertThat(confirmacion.estadoPara(deAna(ESCRIBE_ANA.minusSeconds(3600)), ANA))
                    .contains(EstadoDeEntrega.ENVIADO);
        }
    }

    @Nested
    @DisplayName("en un grupo y en un soporte")
    class EnGrupo {

        @Test
        @DisplayName("✓✓ solo cuando leyeron TODOS los demás; con uno que falta, ✓")
        void leidoCuandoLeyeronTodos() {
            Participante anaAlEscribir = marca(ANA, ESCRIBE_ANA);
            Participante luisLeyo = marca(LUIS, Instant.parse("2026-09-27T00:05:00Z"));

            ConfirmacionDeLectura faltaMarta = ConfirmacionDeLectura.de(GRUPO,
                    List.of(anaAlEscribir, luisLeyo, marca(MARTA, ESCRIBE_ANA.minusSeconds(600))));
            ConfirmacionDeLectura todos = ConfirmacionDeLectura.de(GRUPO,
                    List.of(anaAlEscribir, luisLeyo, marca(MARTA, Instant.parse("2026-09-27T00:10:00Z"))));

            assertThat(faltaMarta.estadoPara(deAna(ESCRIBE_ANA), ANA)).contains(EstadoDeEntrega.ENVIADO);
            assertThat(todos.estadoPara(deAna(ESCRIBE_ANA), ANA)).contains(EstadoDeEntrega.LEIDO);
        }

        @Test
        @DisplayName("en el soporte, igual: el aprendiz ve ✓✓ cuando leyó todo el staff")
        void enElSoporteTambienTodos() {
            ConfirmacionDeLectura unoDelStaffNoLeyo = ConfirmacionDeLectura.de(SOPORTE,
                    List.of(marca(ANA, ESCRIBE_ANA), marca(LUIS, Instant.parse("2026-09-27T00:05:00Z")),
                            marca(MARTA, null)));

            assertThat(unoDelStaffNoLeyo.estadoPara(deAna(ESCRIBE_ANA), ANA)).contains(EstadoDeEntrega.ENVIADO);
        }

        /**
         * Por qué la misma marca sirve para los mensajes de cualquiera (ver la clase): quien escribe
         * queda marcado en ese instante, así que el mínimo entre todos coincide con el de los demás.
         */
        @Test
        @DisplayName("la misma marca sirve para los mensajes de cada uno: el de Luis también está leído por todos")
        void laMismaMarcaValeParaTodos() {
            Instant escribeLuis = Instant.parse("2026-09-27T00:02:00Z");
            Mensaje deLuis = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), ID, LUIS, TipoMensaje.TEXTO, "¡hola!",
                    null, null, null, null, null, null, escribeLuis);
            ConfirmacionDeLectura confirmacion = ConfirmacionDeLectura.de(GRUPO,
                    List.of(marca(ANA, Instant.parse("2026-09-27T00:03:00Z")), marca(LUIS, escribeLuis),
                            marca(MARTA, Instant.parse("2026-09-27T00:04:00Z"))));

            assertThat(confirmacion.leidoPorTodosHasta()).contains(escribeLuis);
            assertThat(confirmacion.estadoPara(deLuis, LUIS)).contains(EstadoDeEntrega.LEIDO);
            assertThat(confirmacion.estadoPara(deAna(Instant.parse("2026-09-27T00:03:00Z")), ANA))
                    .as("Ana escribió después de lo que Luis leyó").contains(EstadoDeEntrega.ENVIADO);
        }
    }

    @Test
    @DisplayName("en la comunidad no hay ✓✓: aunque lo hayan leído todos, queda ✓")
    void enLaComunidadNuncaHayDobleMarca() {
        ConfirmacionDeLectura confirmacion = ConfirmacionDeLectura.de(COMUNIDAD,
                List.of(marca(ANA, ESCRIBE_ANA), marca(LUIS, Instant.parse("2026-09-28T00:00:00Z")),
                        marca(MARTA, Instant.parse("2026-09-28T00:00:00Z"))));

        assertThat(confirmacion.leidoPorTodosHasta()).isEmpty();
        assertThat(confirmacion.estadoPara(deAna(ESCRIBE_ANA), ANA)).contains(EstadoDeEntrega.ENVIADO);
        assertThat(TipoConversacion.GLOBAL.confirmaLectura()).isFalse();
        assertThat(List.of(TipoConversacion.DIRECTA, TipoConversacion.CELULA, TipoConversacion.SOPORTE))
                .allMatch(TipoConversacion::confirmaLectura);
    }

    @Test
    @DisplayName("solo los mensajes propios llevan marca: ni los de otra persona ni los del programa guardados a su nombre")
    void soloLosPropiosLlevanMarca() {
        ConfirmacionDeLectura confirmacion = ConfirmacionDeLectura.de(SOPORTE,
                List.of(marca(ANA, ESCRIBE_ANA), marca(LUIS, Instant.parse("2026-09-27T00:10:00Z"))));
        Mensaje bienvenida = Mensaje.delPrograma(MensajeId.of(UUID.randomUUID()), ID, ANA,
                ContenidoDelPrograma.texto("Te damos la bienvenida"), ESCRIBE_ANA);

        assertThat(confirmacion.estadoPara(deAna(ESCRIBE_ANA), LUIS)).as("el de Ana, mirado por Luis").isEmpty();
        assertThat(confirmacion.estadoPara(bienvenida, ANA)).as("el del programa guardado a nombre de Ana").isEmpty();
    }
}
