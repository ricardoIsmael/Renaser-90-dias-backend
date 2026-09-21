package com.renaser.os.users.infrastructure.adapter.out.dns;

import com.renaser.os.users.application.ports.out.accountrequest.ResolverMxPort.ResultadoMx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.naming.CommunicationException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit puro: el contexto JNDI se sustituye por el seam {@code abrirContexto}, asi que esta clase
 * no abre ningun socket, no consulta ningun DNS y no depende de que haya red.
 *
 * <p><b>Que protege.</b> {@code getAttributes} no es un cliente de DNS: si el nombre empieza con
 * un esquema de URL, el JDK encamina la llamada al proveedor JNDI de ese esquema y el backend
 * termina conectandose al host y al puerto que eligio quien escribio el correo. La garantia que
 * se prueba aca es la del contrato: ante un nombre asi, {@code consultar} responde
 * {@code DOMINIO_INEXISTENTE} <b>sin construir ningun contexto</b>. Contar los contextos —y no
 * solo mirar el valor devuelto— es lo que hace que la prueba se ponga roja si alguien quita la
 * reja: sin ella el nombre llega al seam y el contador deja de estar en cero.
 */
class DnsResolverMxAdapterTest {

    private static final String DOMINIO_BUENO = "renaser.com";

    /** Cada entorno anotado aca es un contexto JNDI que se habria construido de verdad. */
    private final List<Hashtable<String, String>> contextosAbiertos = new ArrayList<>();

    private DirContext contexto;
    private DnsResolverMxAdapter adapter;

    @BeforeEach
    void setUp() {
        contextosAbiertos.clear();
        contexto = mock(DirContext.class);
        adapter = new DnsResolverMxAdapter("3000") {
            @Override
            DirContext abrirContexto(Hashtable<String, String> entorno) {
                contextosAbiertos.add(entorno);
                return contexto;
            }
        };
    }

    private void dnsResponde(Attributes atributos) throws NamingException {
        given(contexto.getAttributes(anyString(), any(String[].class))).willReturn(atributos);
    }

    private void dnsFalla(NamingException e) throws NamingException {
        given(contexto.getAttributes(anyString(), any(String[].class))).willThrow(e);
    }

    @Nested
    @DisplayName("un nombre con forma de URL no llega nunca al interprete JNDI")
    class NombresConEsquema {

        /**
         * Los cinco primeros son los payloads del hallazgo. El sexto no lleva barras a proposito:
         * {@code InitialContext#getURLScheme} toma como esquema todo lo que haya antes del primer
         * ':' cuando no hay '/' antes, asi que dos puntos sueltos ya bastan para desviar el nombre.
         */
        @ParameterizedTest
        @ValueSource(strings = {
                "ldap://baliza.ejemplo-del-atacante.tld:1389/x",
                "ldaps://baliza.ejemplo-del-atacante.tld:8443/x",
                "dns://baliza.ejemplo-del-atacante.tld:5353/x.y",
                "ldap://127.0.0.1:6379/aa",
                "ldap://169.254.169.254:80/aa",
                "baliza.ejemplo-del-atacante.tld:1389"
        })
        void respondeDominioInexistenteSinAbrirContexto(String nombre) {
            assertThat(adapter.consultar(nombre)).isEqualTo(ResultadoMx.DOMINIO_INEXISTENTE);
            assertThat(contextosAbiertos)
                    .as("un nombre con esquema no puede llegar a construir un contexto JNDI")
                    .isEmpty();
            verifyNoInteractions(contexto);
        }

        @Test
        void tampocoEnMayusculas() {
            assertThat(adapter.consultar("LDAP://EVIL.TLD:1389/X"))
                    .isEqualTo(ResultadoMx.DOMINIO_INEXISTENTE);
            assertThat(contextosAbiertos).isEmpty();
        }
    }

    @Nested
    @DisplayName("lo que no es un nombre de host se responde como dominio inexistente")
    class NombresQueNoSonHost {

        @ParameterizedTest
        @NullSource
        @EmptySource
        @ValueSource(strings = {
                "sin-punto",
                "-empieza-con-guion.com",
                "termina-con-guion-.com",
                "etiqueta..vacia.com",
                ".empieza-con-punto.com",
                "termina-con-punto.com.",
                "con espacio.com",
                "con\tsalto.com",
                "sub.dominio!.com",
                "a@b.com"
        })
        void respondeDominioInexistenteSinAbrirContexto(String nombre) {
            assertThat(adapter.consultar(nombre)).isEqualTo(ResultadoMx.DOMINIO_INEXISTENTE);
            assertThat(contextosAbiertos).isEmpty();
        }

        @Test
        void tampocoUnNombreMasLargoQueElMaximoDeDns() {
            String demasiadoLargo = ("a".repeat(63) + ".").repeat(4) + "com"; // 259 caracteres
            assertThat(demasiadoLargo.length()).isGreaterThan(253);
            assertThat(adapter.consultar(demasiadoLargo)).isEqualTo(ResultadoMx.DOMINIO_INEXISTENTE);
            assertThat(contextosAbiertos).isEmpty();
        }
    }

    @Nested
    @DisplayName("un nombre de host de verdad si se consulta")
    class NombresDeHost {

        @ParameterizedTest
        @ValueSource(strings = {
                DOMINIO_BUENO,
                "gmail.com",
                "sub.dominio.renaser.com",
                "con-guion-interior.com",
                "169.254.169.254",
                "RENASER.COM"
        })
        void abreElContexto(String nombre) throws NamingException {
            dnsResponde(new BasicAttributes("MX", "10 mx.renaser.com"));
            assertThat(adapter.consultar(nombre)).isEqualTo(ResultadoMx.TIENE_MX);
            assertThat(contextosAbiertos).hasSize(1);
        }

        @Test
        void sinMxCuandoElAtributoNoEsta() throws NamingException {
            dnsResponde(new BasicAttributes());
            assertThat(adapter.consultar(DOMINIO_BUENO)).isEqualTo(ResultadoMx.SIN_MX);
        }

        @Test
        void sinMxCuandoElAtributoEstaVacio() throws NamingException {
            dnsResponde(new BasicAttributes() {{ put(new BasicAttribute("MX")); }});
            assertThat(adapter.consultar(DOMINIO_BUENO)).isEqualTo(ResultadoMx.SIN_MX);
        }

        @Test
        void dominioInexistenteCuandoElDnsDiceNxdomain() throws NamingException {
            dnsFalla(new NameNotFoundException("NXDOMAIN"));
            assertThat(adapter.consultar(DOMINIO_BUENO)).isEqualTo(ResultadoMx.DOMINIO_INEXISTENTE);
        }

        @Test
        void indeterminadoCuandoElDnsNoContesta() throws NamingException {
            dnsFalla(new CommunicationException("timeout"));
            assertThat(adapter.consultar(DOMINIO_BUENO)).isEqualTo(ResultadoMx.INDETERMINADO);
        }
    }

    @Nested
    @DisplayName("el entorno del contexto")
    class Entorno {

        /**
         * Los dos timeouts de LDAP no evitan la conexion saliente —de eso se ocupa la reja de
         * NOMBRE_DE_HOST—, solo le ponen plazo si algun dia un nombre volviera a caer en ese
         * proveedor. Sin ellos {@code LdapCtx} deja connectTimeout y readTimeout en -1 y la
         * espera de la respuesta no termina nunca.
         */
        @Test
        void acotaEnElTiempoTambienAlProveedorLdap() throws NamingException {
            dnsResponde(new BasicAttributes("MX", "10 mx.renaser.com"));
            adapter.consultar(DOMINIO_BUENO);

            assertThat(contextosAbiertos).hasSize(1);
            assertThat(contextosAbiertos.getFirst())
                    .containsEntry("com.sun.jndi.dns.timeout.initial", "3000")
                    .containsEntry("com.sun.jndi.dns.timeout.retries", "1")
                    .containsEntry("com.sun.jndi.ldap.connect.timeout", "3000")
                    .containsEntry("com.sun.jndi.ldap.read.timeout", "3000");
        }

        /** El proveedor los lee con Integer.parseInt: si no es un entero, revienta al usarlos. */
        @Test
        void losTimeoutsSonEnteros() throws NamingException {
            dnsResponde(new BasicAttributes("MX", "10 mx.renaser.com"));
            adapter.consultar(DOMINIO_BUENO);

            Hashtable<String, String> entorno = contextosAbiertos.getFirst();
            assertThat(Integer.parseInt(entorno.get("com.sun.jndi.ldap.connect.timeout"))).isPositive();
            assertThat(Integer.parseInt(entorno.get("com.sun.jndi.ldap.read.timeout"))).isPositive();
            assertThat(Integer.parseInt(entorno.get("com.sun.jndi.dns.timeout.initial"))).isPositive();
        }
    }
}
