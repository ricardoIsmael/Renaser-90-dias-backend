package com.renaser.os.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E-151: lo que Postgres acepta en una columna {@code inet} y lo que no. */
class DireccionIpDelClienteTest {

    @Test
    @DisplayName("un IPv6 entre corchetes pierde los corchetes: es la forma que rechazaba inet")
    void quitaLosCorchetesDeUnIpv6() {
        assertThat(DireccionIpDelCliente.normalizar("[2803:9810:6075:9310:c63b:3904:e158:3228]"))
                .isEqualTo("2803:9810:6075:9310:c63b:3904:e158:3228");
    }

    @Test
    @DisplayName("un IPv6 con puerto tambien queda limpio")
    void quitaCorchetesYPuerto() {
        assertThat(DireccionIpDelCliente.normalizar("[::1]:8080")).isEqualTo("::1");
    }

    @Test
    @DisplayName("el identificador de zona se descarta: inet tampoco lo acepta")
    void quitaLaZonaDeUnIpv6DeEnlaceLocal() {
        assertThat(DireccionIpDelCliente.normalizar("fe80::1%eth0")).isEqualTo("fe80::1");
    }

    @Test
    @DisplayName("un IPv4 pasa intacto: es el caso que nunca estuvo roto")
    void noTocaUnIpv4() {
        assertThat(DireccionIpDelCliente.normalizar("203.0.113.9")).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("un IPv6 sin corchetes pasa intacto")
    void noTocaUnIpv6QueYaVieneLimpio() {
        assertThat(DireccionIpDelCliente.normalizar("2803:9810::1")).isEqualTo("2803:9810::1");
    }

    @Test
    @DisplayName("nulo y vacio se devuelven tal cual: decidir que hacer con eso no es tarea de este metodo")
    void dejaPasarNuloYVacio() {
        assertThat(DireccionIpDelCliente.normalizar(null)).isNull();
        assertThat(DireccionIpDelCliente.normalizar("")).isEmpty();
    }
}
