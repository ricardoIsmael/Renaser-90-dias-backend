package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La invariante que el hallazgo del prefijo IPv6 rompia y que nadie cubria: dos peticiones que
 * vienen del MISMO principal tienen que caer en el MISMO cubo, y dos que vienen de principales
 * distintos en cubos distintos. Antes de esto la clave era la cadena cruda, asi que cada
 * direccion del propio /64 —y hasta cada forma de escribir la misma direccion— estrenaba
 * contador.
 */
class UnidadDeConteoPorIpTest {

    @Nested
    @DisplayName("IPv6: se cuenta por /64")
    class PorPrefijo {

        @Test
        @DisplayName("dos direcciones distintas del mismo /64 comparten cubo: es la evasion que se cierra")
        void dosDireccionesDelMismoPrefijoCompartenCubo() {
            // La primera es la direccion real que la bitacora registro en produccion (E-151).
            assertThat(UnidadDeConteoPorIp.de("2803:9810:6075:9310:c63b:3904:e158:3228"))
                    .isEqualTo(UnidadDeConteoPorIp.de("2803:9810:6075:9310:1:2:3:4"))
                    .isEqualTo(UnidadDeConteoPorIp.de("2803:9810:6075:9310::"))
                    .isEqualTo("2803:9810:6075:9310::/64");
        }

        @Test
        @DisplayName("dos /64 distintos NO comparten cubo: agregar no puede volverse un contador global")
        void dosPrefijosDistintosNoCompartenCubo() {
            assertThat(UnidadDeConteoPorIp.de("2001:db8:1:1::1"))
                    .isNotEqualTo(UnidadDeConteoPorIp.de("2001:db8:1:2::1"));
        }

        @Test
        @DisplayName("el corte cae exactamente en el bit 64: el bit 63 separa cubos y el 64 no")
        void elCorteEsExactamenteEnElBitSesentaYCuatro() {
            // Bit 64 en adelante: identificador de interfaz, lo elige el host. No separa cubos.
            assertThat(UnidadDeConteoPorIp.de("2001:db8:1:1::"))
                    .isEqualTo(UnidadDeConteoPorIp.de("2001:db8:1:1:8000::"))
                    .isEqualTo(UnidadDeConteoPorIp.de("2001:db8:1:1:ffff:ffff:ffff:ffff"))
                    .isEqualTo("2001:db8:1:1::/64");
            // Bit 63: ultimo bit del prefijo. Ahi si cambia el cubo.
            assertThat(UnidadDeConteoPorIp.de("2001:db8:1:1::"))
                    .isNotEqualTo(UnidadDeConteoPorIp.de("2001:db8:1:0::"));
            // El texto sale comprimido (RFC 5952), asi que un grupo en cero dentro del prefijo
            // se lo come el "::" — es la misma cadena siempre, que es lo unico que importa aca.
            assertThat(UnidadDeConteoPorIp.de("2001:db8:1:0:ffff:ffff:ffff:ffff")).isEqualTo("2001:db8:1::/64");
        }
    }

    @Nested
    @DisplayName("formas equivalentes de la misma direccion")
    class MismaDireccionDistintoTexto {

        @Test
        @DisplayName("abreviatura con ::, ceros a la izquierda, mayusculas y corchetes dan el mismo cubo")
        void todasLasFormasDelMismoIpv6CaenJuntas() {
            String cubo = "2803:9810::/64";
            assertThat(UnidadDeConteoPorIp.de("2803:9810::1")).isEqualTo(cubo);
            assertThat(UnidadDeConteoPorIp.de("2803:9810:0:0::1")).isEqualTo(cubo);
            assertThat(UnidadDeConteoPorIp.de("2803:9810:0000:0000:0000:0000:0000:0001")).isEqualTo(cubo);
            assertThat(UnidadDeConteoPorIp.de("2803:9810::0001")).isEqualTo(cubo);
            assertThat(UnidadDeConteoPorIp.de("2803:9810::A")).isEqualTo(UnidadDeConteoPorIp.de("2803:9810::a"));
            assertThat(UnidadDeConteoPorIp.de("[2803:9810::1]")).isEqualTo(cubo);
            assertThat(UnidadDeConteoPorIp.de("2803:9810::1%eth0")).isEqualTo(cubo);
            assertThat(UnidadDeConteoPorIp.de("  2803:9810::1  ")).isEqualTo(cubo);
        }

        @Test
        @DisplayName("un IPv4 con ceros a la izquierda no estrena cubo propio")
        void cerosALaIzquierdaEnIpv4() {
            assertThat(UnidadDeConteoPorIp.de("010.000.113.009")).isEqualTo("10.0.113.9");
        }
    }

    @Nested
    @DisplayName("IPv4 sigue contando igual que antes")
    class Ipv4NoCambia {

        @Test
        @DisplayName("una IPv4 canonica sale identica: las claves de Redis en vuelo no se reinician")
        void unaIpv4SaleIntacta() {
            assertThat(UnidadDeConteoPorIp.de("203.0.113.9")).isEqualTo("203.0.113.9");
            assertThat(UnidadDeConteoPorIp.de("127.0.0.1")).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("dos IPv4 distintas siguen en cubos distintos: no se agrega nada en IPv4")
        void dosIpv4DistintasSiguenSeparadas() {
            assertThat(UnidadDeConteoPorIp.de("203.0.113.9"))
                    .isNotEqualTo(UnidadDeConteoPorIp.de("203.0.113.10"));
        }

        @Test
        @DisplayName("ni siquiera dos IPv4 vecinas del mismo /24 se juntan: el /64 es solo de IPv6")
        void vecinasDelMismoVeinticuatroTampocoSeJuntan() {
            assertThat(UnidadDeConteoPorIp.de("203.0.113.1"))
                    .isNotEqualTo(UnidadDeConteoPorIp.de("203.0.113.254"));
        }

        @Test
        @DisplayName("una IPv4 mapeada cuenta como la IPv4 que es, no como un IPv6 de prefijo cero")
        void laFormaMapeadaEsLaMismaIpv4() {
            assertThat(UnidadDeConteoPorIp.de("::ffff:203.0.113.9")).isEqualTo("203.0.113.9");
            // Si se agregara por /64, TODAS las mapeadas caerian en ::/64 — un unico contador
            // global para todo IPv4, que bloquearia a cualquiera en cuanto uno solo abusara.
            assertThat(UnidadDeConteoPorIp.de("::ffff:203.0.113.9"))
                    .isNotEqualTo(UnidadDeConteoPorIp.de("::ffff:198.51.100.7"));
        }
    }

    @Nested
    @DisplayName("lo que no es una direccion")
    class NoEsUnaDireccion {

        @Test
        @DisplayName("un nombre NO se resuelve por DNS: sale tal cual y se cuenta opaco")
        void unNombreNoSeResuelve() {
            // Si esto se resolviera, el contador seria una consulta DNS gobernada por el cliente
            // (el texto llega por X-Forwarded-For) y "localhost" terminaria contando como 127.0.0.1.
            assertThat(UnidadDeConteoPorIp.de("localhost")).isEqualTo("localhost");
            assertThat(UnidadDeConteoPorIp.de("ejemplo.test")).isEqualTo("ejemplo.test");
        }

        @Test
        @DisplayName("basura, nulo y vacio se devuelven tal cual: el comportamiento de antes")
        void basuraNuloYVacio() {
            assertThat(UnidadDeConteoPorIp.de("no-soy-una-ip")).isEqualTo("no-soy-una-ip");
            assertThat(UnidadDeConteoPorIp.de(null)).isNull();
            assertThat(UnidadDeConteoPorIp.de("")).isEmpty();
            assertThat(UnidadDeConteoPorIp.de("   ")).isEqualTo("   ");
        }
    }
}
