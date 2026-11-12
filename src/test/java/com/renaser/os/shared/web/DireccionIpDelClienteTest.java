package com.renaser.os.shared.web;

import com.renaser.os.shared.domain.UnidadDeConteoPorIp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-151: lo que Postgres acepta en una columna {@code inet} y lo que no. E-678: de donde sale la IP del cliente (el
 * valor que agrega CloudFront, nunca el primero de {@code X-Forwarded-For}).
 */
class DireccionIpDelClienteTest {

    /** La conexion TCP que ve Spring detras del nginx: nunca es la del cliente. */
    private static final String CONEXION_DEL_PROXY = "172.18.0.5";

    private static MockHttpServletRequest peticion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CONEXION_DEL_PROXY);
        return request;
    }

    // E-678
    @Test
    @DisplayName("con un valor falso a la izquierda, la IP es la que agrego CloudFront (la de mas a la derecha)")
    void tomaElValorDeLaDerechaYNoElDeLaIzquierda() {
        MockHttpServletRequest request = peticion();
        request.addHeader("X-Forwarded-For", "198.51.100.7, 203.0.113.9");

        assertThat(DireccionIpDelCliente.de(request)).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("dos peticiones del mismo cliente con distinto valor falso a la izquierda dan la misma IP")
    void elValorFalsoNoCambiaLaIp() {
        MockHttpServletRequest primera = peticion();
        primera.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.9");
        MockHttpServletRequest segunda = peticion();
        segunda.addHeader("X-Forwarded-For", "198.51.100.2, 192.0.2.44, 203.0.113.9");

        assertThat(DireccionIpDelCliente.de(primera)).isEqualTo(DireccionIpDelCliente.de(segunda)).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("un solo valor: es el de CloudFront")
    void unSoloValor() {
        MockHttpServletRequest request = peticion();
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(DireccionIpDelCliente.de(request)).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("varias lineas de X-Forwarded-For: manda el ultimo valor de la ultima")
    void variasLineasDeLaCabecera() {
        MockHttpServletRequest request = peticion();
        request.addHeader("X-Forwarded-For", "198.51.100.1, 198.51.100.2");
        request.addHeader("X-Forwarded-For", "198.51.100.3, 203.0.113.9");

        assertThat(DireccionIpDelCliente.de(request)).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("un IPv6 a la derecha sale sin corchetes ni puerto: es lo unico que acepta una columna inet")
    void ipv6DeLaDerecha() {
        MockHttpServletRequest request = peticion();
        request.addHeader("X-Forwarded-For", "198.51.100.7, 2803:9810:6075:9310:c63b:3904:e158:3228");

        assertThat(DireccionIpDelCliente.de(request)).isEqualTo("2803:9810:6075:9310:c63b:3904:e158:3228");

        MockHttpServletRequest conCorchetesYPuerto = peticion();
        conCorchetesYPuerto.addHeader("X-Forwarded-For", "198.51.100.7, [2803:9810:6075:9310:c63b:3904:e158:3228]:46532");
        assertThat(DireccionIpDelCliente.de(conCorchetesYPuerto)).isEqualTo("2803:9810:6075:9310:c63b:3904:e158:3228");
    }

    @Test
    @DisplayName("sin cabeceras, la IP es la de la conexion")
    void sinCabecerasEsLaDeLaConexion() {
        assertThat(DireccionIpDelCliente.de(peticion())).isEqualTo(CONEXION_DEL_PROXY);
    }

    @Test
    @DisplayName("un ultimo valor que no es una direccion no se usa ni se resuelve: se cae a la conexion")
    void unUltimoValorQueNoEsUnaDireccion() {
        MockHttpServletRequest request = peticion();
        request.addHeader("X-Forwarded-For", "203.0.113.9, localhost");

        assertThat(DireccionIpDelCliente.de(request)).isEqualTo(CONEXION_DEL_PROXY);
    }

    @Test
    @DisplayName("Forwarded y X-Real-IP no se leen")
    void otrasCabecerasNoSeLeen() {
        MockHttpServletRequest request = peticion();
        request.addHeader("Forwarded", "for=198.51.100.7");
        request.addHeader("X-Real-IP", "198.51.100.8");

        assertThat(DireccionIpDelCliente.de(request)).isEqualTo(CONEXION_DEL_PROXY);
    }

    @Test
    @DisplayName("CloudFront-Viewer-Address no se lee por defecto: la escribe quien hace la peticion si la politica no la agrega")
    void laCabeceraDelVisitanteNoSeLeePorDefecto() {
        MockHttpServletRequest request = peticion();
        request.addHeader("CloudFront-Viewer-Address", "198.51.100.7:1234");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(DireccionIpDelCliente.de(request)).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("la propiedad esta apagada salvo que alguien la prenda")
    void porDefectoNoConfiaEnLaCabeceraDelVisitante() {
        MockHttpServletRequest request = peticion();
        request.addHeader("CloudFront-Viewer-Address", "198.51.100.7:1234");

        assertThat(DireccionIpDelCliente.de(request)).isEqualTo(CONEXION_DEL_PROXY);
    }

    // Con la propiedad prendida (D-342): CloudFront-Viewer-Address manda sobre X-Forwarded-For.
    @Test
    @DisplayName("prendida, CloudFront-Viewer-Address con IPv4 y puerto manda sobre X-Forwarded-For")
    void cabeceraDelVisitanteIpv4ConPuerto() {
        MockHttpServletRequest request = peticion();
        request.addHeader("CloudFront-Viewer-Address", "203.0.113.9:46532");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 192.0.2.1");

        assertThat(DireccionIpDelCliente.de(request, true)).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("prendida, CloudFront-Viewer-Address acepta IPv6 con corchetes y puerto, o sin corchetes y con puerto")
    void cabeceraDelVisitanteIpv6() {
        MockHttpServletRequest conCorchetes = peticion();
        conCorchetes.addHeader("CloudFront-Viewer-Address", "[2803:9810:6075:9310::1]:46532");
        MockHttpServletRequest sinCorchetes = peticion();
        sinCorchetes.addHeader("CloudFront-Viewer-Address", "2803:9810:6075:9310::1:46532");

        assertThat(DireccionIpDelCliente.de(conCorchetes, true)).isEqualTo("2803:9810:6075:9310::1");
        assertThat(DireccionIpDelCliente.de(sinCorchetes, true)).isEqualTo("2803:9810:6075:9310::1");
    }

    @Test
    @DisplayName("prendida, sin la cabecera o con un valor que no es una direccion se cae al ultimo valor de X-Forwarded-For")
    void cabeceraDelVisitanteAusenteOInvalida() {
        MockHttpServletRequest ausente = peticion();
        ausente.addHeader("X-Forwarded-For", "198.51.100.7, 203.0.113.9");
        MockHttpServletRequest invalida = peticion();
        invalida.addHeader("CloudFront-Viewer-Address", "no-es-una-ip:1234");
        invalida.addHeader("X-Forwarded-For", "198.51.100.7, 203.0.113.9");

        assertThat(DireccionIpDelCliente.de(ausente, true)).isEqualTo("203.0.113.9");
        assertThat(DireccionIpDelCliente.de(invalida, true)).isEqualTo("203.0.113.9");
    }

    // El conteo por /64 no depende de donde salga la direccion.
    @Test
    @DisplayName("dos direcciones del mismo /64 de IPv6, resueltas desde la cabecera, cuentan en el mismo cubo")
    void elConteoSigueSiendoPorSesentaYCuatroBits() {
        MockHttpServletRequest primera = peticion();
        primera.addHeader("X-Forwarded-For", "198.51.100.1, 2803:9810:6075:9310:aaaa:bbbb:cccc:dddd");
        MockHttpServletRequest segunda = peticion();
        segunda.addHeader("X-Forwarded-For", "198.51.100.2, 2803:9810:6075:9310:1111:2222:3333:4444");

        assertThat(DireccionIpDelCliente.de(primera)).isNotEqualTo(DireccionIpDelCliente.de(segunda));
        assertThat(UnidadDeConteoPorIp.de(DireccionIpDelCliente.de(primera)))
                .isEqualTo(UnidadDeConteoPorIp.de(DireccionIpDelCliente.de(segunda)))
                .isEqualTo("2803:9810:6075:9310::/64");
    }

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
