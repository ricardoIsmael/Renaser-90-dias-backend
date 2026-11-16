package com.renaser.os.shared.web;

import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las tres cabeceras de lo hecho sin red (spec 048, fase 3): sin las dos primeras es un pedido normal; mal hechas, 400. */
class HechoSinRedArgumentResolverTest {

    private static final String ID = "5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10";

    private static MockHttpServletRequest pedido(String id, String hechoEn, String origen) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (id != null) {
            request.addHeader(HechoSinRedArgumentResolver.CABECERA_ID_CLIENTE, id);
        }
        if (hechoEn != null) {
            request.addHeader(HechoSinRedArgumentResolver.CABECERA_HECHO_EN, hechoEn);
        }
        if (origen != null) {
            request.addHeader(HechoSinRedArgumentResolver.CABECERA_ORIGEN_DE_LA_HORA, origen);
        }
        return request;
    }

    @Test
    @DisplayName("con las tres cabeceras arma el hecho sin red con su id, su hora real y su origen")
    void armaElHecho() { // spec 048 FR-019 FR-013
        HechoSinRed hecho = HechoSinRedArgumentResolver.leer(pedido(ID, "2026-11-10T02:40:13.250Z", "RELOJ_DEL_SISTEMA"));

        assertThat(hecho.idCliente()).isEqualTo(UUID.fromString(ID));
        assertThat(hecho.hechoEn()).isEqualTo(Instant.parse("2026-11-10T02:40:13.250Z"));
        assertThat(hecho.origenDeLaHora()).isEqualTo(OrigenDeLaHora.RELOJ_DEL_SISTEMA);
    }

    @Test
    @DisplayName("sin origen, o con uno desconocido, se lee como TELEFONO_CORREGIDO")
    void origenPorDefecto() { // spec 048 FR-013
        assertThat(HechoSinRedArgumentResolver.leer(pedido(ID, "2026-11-10T02:40:13Z", null)).origenDeLaHora())
                .isEqualTo(OrigenDeLaHora.TELEFONO_CORREGIDO);
        assertThat(HechoSinRedArgumentResolver.leer(pedido(ID, "2026-11-10T02:40:13Z", "OTRO")).origenDeLaHora())
                .isEqualTo(OrigenDeLaHora.TELEFONO_CORREGIDO);
    }

    @Test
    @DisplayName("si falta una de las dos primeras (o viene en blanco) el pedido es uno normal: null, y todo responde como hoy")
    void sinCabecerasEsUnPedidoNormal() { // spec 048 FR-019
        assertThat(HechoSinRedArgumentResolver.leer(pedido(null, null, null))).isNull();
        assertThat(HechoSinRedArgumentResolver.leer(pedido(ID, null, "RELOJ_DEL_SISTEMA"))).isNull();
        assertThat(HechoSinRedArgumentResolver.leer(pedido(null, "2026-11-10T02:40:13Z", null))).isNull();
        assertThat(HechoSinRedArgumentResolver.leer(pedido(" ", "2026-11-10T02:40:13Z", null))).isNull();
    }

    @Test
    @DisplayName("un id que no es UUID o una hora que no es un instante es un 400 de siempre (IllegalArgumentException)")
    void cabecerasMalHechas() { // spec 048 FR-019
        assertThatThrownBy(() -> HechoSinRedArgumentResolver.leer(pedido("no-es-uuid", "2026-11-10T02:40:13Z", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("X-Id-Cliente");
        assertThatThrownBy(() -> HechoSinRedArgumentResolver.leer(pedido(ID, "ayer", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("X-Hecho-En");
    }
}
