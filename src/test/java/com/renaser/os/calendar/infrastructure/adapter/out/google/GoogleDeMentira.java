package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.renaser.os.calendar.application.ports.out.meet.CifradoDeSecretosPort;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.SaveCuentaGooglePort;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * El cableado de los adaptadores de Google para las pruebas (spec 040): un servidor HTTP de mentira ({@link MockRestServiceServer}) en lugar de
 * Google, una cuenta ya conectada y las credenciales de mentira. Ninguna llamada sale a la red y ningún secreto es real.
 */
final class GoogleDeMentira {

    static final String CLIENT_ID = "cliente-de-prueba.apps.googleusercontent.com";
    static final String CLIENT_SECRET = "secreto-de-cliente-de-prueba";
    static final String REDIRECT_URI = "https://api.renaser.test/api/v1/integraciones/google/callback";
    static final String REFRESH_TOKEN = "1//permiso-permanente-de-prueba";
    static final String TOKEN_DE_ACCESO = "ya29.token-de-acceso-de-prueba";
    static final String CALENDARIO = "calendario-renaser@group.calendar.google.com";

    /** Un reloj que se mueve. */
    static final class Reloj implements Clock {
        Instant ahora = Instant.parse("2026-11-05T15:00:00Z");

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    /** La cuenta, en memoria. */
    static final class Cuenta implements LoadCuentaGooglePort, SaveCuentaGooglePort {
        CuentaDeGoogle actual;
        int guardados;

        @Override
        public Optional<CuentaDeGoogle> cargar() {
            return Optional.ofNullable(actual);
        }

        @Override
        public CuentaDeGoogle guardar(CuentaDeGoogle cuenta) {
            actual = cuenta;
            guardados++;
            return cuenta;
        }

        @Override
        public void borrar() {
            actual = null;
        }
    }

    final MockRestServiceServer servidor;
    final Reloj reloj = new Reloj();
    final Cuenta cuenta = new Cuenta();
    final LimitadorDePedidosAGoogle limitador;
    final LlamadasAGoogle llamadas;
    final ConfiguracionDeGoogle configuracion;
    final TokenDeAccesoDeGoogle tokens;

    GoogleDeMentira() {
        this(30);
    }

    GoogleDeMentira(int pedidosPorMinuto) {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        limitador = new LimitadorDePedidosAGoogle(reloj, pedidosPorMinuto);
        llamadas = new LlamadasAGoogle(builder.build(), limitador);
        configuracion = new ConfiguracionDeGoogle(CLIENT_ID, CLIENT_SECRET, REDIRECT_URI, ConfiguracionDeGoogle.PERMISOS_POR_DEFECTO, "", 60,
                new CifradoQueNoCifra());
        tokens = new TokenDeAccesoDeGoogle(cuenta, cuenta, llamadas, configuracion, reloj);
        CuentaDeGoogle conectada = CuentaDeGoogle.conectar("renasercorporation@gmail.com", REFRESH_TOKEN,
                Set.of(CuentaDeGoogle.PERMISO_CALENDARIO_PROPIO, CuentaDeGoogle.PERMISO_AJUSTES_DE_LA_SALA), UserId.of(UUID.randomUUID()), reloj.now());
        conectada.fijarCalendario(CALENDARIO);
        cuenta.actual = conectada;
    }

    /** Para la configuración: la clave de cifrado está («estaConfigurado»), pero estas pruebas no cifran nada. */
    private static final class CifradoQueNoCifra implements CifradoDeSecretosPort {
        @Override
        public boolean estaConfigurado() {
            return true;
        }

        @Override
        public String cifrar(String textoPlano) {
            return textoPlano;
        }

        @Override
        public String descifrar(String textoCifrado) {
            return textoCifrado;
        }

        @Override
        public int versionDeLaClave() {
            return 1;
        }
    }
}
