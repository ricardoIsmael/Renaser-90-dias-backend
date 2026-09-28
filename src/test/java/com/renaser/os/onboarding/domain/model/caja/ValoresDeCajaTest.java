package com.renaser.os.onboarding.domain.model.caja;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los datos de la caja que escribe una persona: envío, destino, país, contenido, fotos (D-219). */
class ValoresDeCajaTest {

    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-00000000a0a0"));

    @Test
    @DisplayName("el medio y el código son obligatorios; Olva y Shalom abren su página de rastreo, el resto no")
    void datosDelEnvio() {
        assertThatThrownBy(() -> DatosDelEnvio.de(" ", null, "A1", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("medio");
        assertThatThrownBy(() -> DatosDelEnvio.de("inDrive", null, "", null)).hasMessageContaining("código");
        assertThatThrownBy(() -> DatosDelEnvio.de("Olva", null, "A1", BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(DatosDelEnvio.de("Agencia", "OLVA Courier", "A1", null).rastreoUrl()).hasValue(DatosDelEnvio.RASTREO_OLVA);
        assertThat(DatosDelEnvio.de("Shalóm", null, "A1", null).rastreoUrl()).hasValue(DatosDelEnvio.RASTREO_SHALOM);
        assertThat(DatosDelEnvio.de("inDrive", null, "ABC-123", null).rastreoUrl()).isEmpty();
    }

    @Test
    @DisplayName("los datos del envío van y vuelven del detalle del paso sin perder el costo")
    void idaYVueltaDelDetalle() {
        DatosDelEnvio datos = DatosDelEnvio.de("Olva", "Olva", "A1", new BigDecimal("15.50"));
        PasoDeCaja enviada = new PasoDeCaja(ANA, 1, TipoPasoCaja.ENVIADA, java.time.Instant.EPOCH, null,
                datos.comoDetalle());

        assertThat(DatosDelEnvio.desde(enviada)).isEqualTo(datos);
    }

    @Test
    @DisplayName("Perú se escribe como sea; sin país se asume Perú; otro país queda fuera de la app")
    void pais() {
        for (String peru : List.of("Perú", "PERU", " perú ", "Peru.", "PE", "Lima, Perú")) {
            assertThat(ficha(peru).esDelPeru()).as(peru).isTrue();
        }
        assertThat(ficha(null).esDelPeru()).isTrue();
        assertThat(ficha("Chile").esDelPeru()).isFalse();
        assertThat(ficha("España").esDelPeru()).isFalse();
    }

    @Test
    @DisplayName("el destino alternativo limpia espacios, vacío es sin respuesta, y tiene tope de largo")
    void destino() {
        DestinoAlternativo destino = DestinoAlternativo.de("  Av. Siempre Viva 123 ", "", null, "Frente al parque", " ");

        assertThat(destino.otraDireccion()).isEqualTo("Av. Siempre Viva 123");
        assertThat(destino.otroCelular()).isNull();
        assertThat(destino.provincia()).isNull();
        assertThatThrownBy(() -> DestinoAlternativo.de(null, "9".repeat(31), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el contenido: sin elementos repetidos, la clave sale de la etiqueta y solo se marca lo que está")
    void contenido() {
        ElementoDeCaja vela = ElementoDeCaja.de(null, "Vela aromática");
        assertThat(vela.valor()).isEqualTo("vela_aromatica");
        ContenidoDeCaja lista = ContenidoDeCaja.nueva(List.of(vela, ElementoDeCaja.de("totem", "Tótem")));

        assertThat(lista.completo(Set.of("vela_aromatica"))).isFalse();
        assertThat(lista.completo(lista.exigirMarcables(List.of("vela_aromatica", "totem")))).isTrue();
        assertThatThrownBy(() -> lista.exigirMarcables(List.of("otra"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContenidoDeCaja.nueva(List.of(vela, ElementoDeCaja.de("vela_aromatica", "Otra"))))
                .hasMessageContaining("repetido");
        assertThatThrownBy(() -> ContenidoDeCaja.nueva(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ElementoDeCaja.de("Con Mayúsculas", "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la foto va a la caja del aprendiz: otra ruta, otro aprendiz o un «..» no se aceptan")
    void rutasDeFotos() {
        String ruta = FotoDeCaja.rutaNueva(ANA, UUID.randomUUID());

        assertThat(ruta).startsWith("onboarding/" + ANA + "/caja/");
        assertThat(FotoDeCaja.exigirRutaDe(ANA, ruta)).isEqualTo(ruta);
        UserId otro = UserId.of(UUID.randomUUID());
        assertThatThrownBy(() -> FotoDeCaja.exigirRutaDe(otro, ruta)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FotoDeCaja.exigirRutaDe(ANA, "onboarding/" + ANA + "/caja/../audio/x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FotoDeCaja.exigirRutaDe(ANA, "onboarding/" + ANA + "/audio/x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(FotoDeCaja.exigirTipoDeContenido(null)).isEqualTo("image/jpeg");
        assertThatThrownBy(() -> FotoDeCaja.exigirTipoDeContenido("application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un paso se reconstruye desde su fila; un flujo que no es de la caja o un paso desconocido se ignora")
    void pasoDesdeLaFila() {
        assertThat(PasoDeCaja.desdeFila(ANA, "caja:2:ENVIADA", java.time.Instant.EPOCH, null, Map.of("codigo", "A1")))
                .hasValueSatisfying(p -> {
                    assertThat(p.envio()).isEqualTo(2);
                    assertThat(p.tipo()).isEqualTo(TipoPasoCaja.ENVIADA);
                    assertThat(p.dato(PasoDeCaja.CODIGO)).hasValue("A1");
                });
        assertThat(PasoDeCaja.desdeFila(ANA, "mapa_dia7", java.time.Instant.EPOCH, null, Map.of())).isEmpty();
        assertThat(PasoDeCaja.desdeFila(ANA, "caja:1:ALGO_NUEVO", java.time.Instant.EPOCH, null, Map.of())).isEmpty();
        assertThat(PasoDeCaja.desdeFila(ANA, "caja:x:ENVIADA", java.time.Instant.EPOCH, null, Map.of())).isEmpty();
    }

    private static FichaDeEnvio ficha(String pais) {
        return new FichaDeEnvio(null, null, pais, null, null, null, null, null);
    }

    @Test
    @DisplayName("E-416: el 409 de una acción fuera de su estado se lee en palabras, nunca con el nombre del estado")
    void mensajeDelEstadoEnPalabras() {
        for (EstadoCaja estado : EstadoCaja.values()) {
            for (AccionDeCaja accion : AccionDeCaja.values()) {
                if (accion.sePuedeDesde(estado)) {
                    continue;
                }
                assertThatThrownBy(() -> accion.exigirDesde(estado)).isInstanceOf(IllegalStateException.class)
                        .hasMessageNotContaining(estado.name());
            }
        }
        assertThatThrownBy(() -> AccionDeCaja.CONFIRMAR_RECIBIDA.exigirDesde(EstadoCaja.ENTREGADA))
                .hasMessage("La caja ya fue entregada: no se puede confirmar que llegó.");
    }

    @Test
    @DisplayName("E-417: la foto de la caja tiene que ser un JPEG o un PNG por dentro, no solo por el encabezado")
    void fotoPorDentro() {
        FotoDeCaja.exigirImagen(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0});
        FotoDeCaja.exigirImagen(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0});
        assertThatThrownBy(() -> FotoDeCaja.exigirImagen("no soy una imagen".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La foto tiene que ser JPG o PNG.");
        assertThatThrownBy(() -> FotoDeCaja.exigirImagen(new byte[] {(byte) 0xFF}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FotoDeCaja.exigirImagen(new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }
}
