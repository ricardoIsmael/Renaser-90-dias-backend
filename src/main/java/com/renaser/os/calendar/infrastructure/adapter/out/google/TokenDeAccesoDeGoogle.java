package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleNoConectadaException;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.SaveCuentaGooglePort;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.calendar.domain.model.meet.HuellaDeSecreto;
import com.renaser.os.calendar.infrastructure.adapter.out.google.LlamadasAGoogle.Respuesta;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.time.Instant;

/**
 * El token de acceso de la cuenta de Google (spec 040, FR-010 y FR-011): dura una hora y se pide con el permiso permanente SOLO cuando hace falta.
 * Vive en memoria, nunca en la base ni en un registro; si la cuenta cambia (se reconecta con otro permiso), el token guardado deja de valer porque
 * se reconoce por la huella del permiso permanente con que se pidió.
 */
@Component
class TokenDeAccesoDeGoogle {

    static final URI EXTREMO_DE_TOKENS = URI.create("https://oauth2.googleapis.com/token");
    private static final long MARGEN_SEGUNDOS = 60;

    /** Lo que necesita una llamada a la API: el token y el calendario donde caen las salas. */
    record Credencial(String tokenDeAcceso, String calendarioId) {
    }

    private record Guardado(String token, Instant venceEn, String huellaDelPermiso) {
    }

    private final LoadCuentaGooglePort loadCuentaPort;
    private final SaveCuentaGooglePort saveCuentaPort;
    private final LlamadasAGoogle llamadas;
    private final ConfiguracionDeGoogle configuracion;
    private final Clock clock;
    private volatile Guardado guardado;

    TokenDeAccesoDeGoogle(LoadCuentaGooglePort loadCuentaPort, SaveCuentaGooglePort saveCuentaPort, LlamadasAGoogle llamadas,
                          ConfiguracionDeGoogle configuracion, Clock clock) {
        this.loadCuentaPort = loadCuentaPort;
        this.saveCuentaPort = saveCuentaPort;
        this.llamadas = llamadas;
        this.configuracion = configuracion;
        this.clock = clock;
    }

    /** @throws CuentaGoogleNoConectadaException si no hay cuenta o está revocada */
    Credencial obtener() {
        CuentaDeGoogle cuenta = loadCuentaPort.cargar().filter(CuentaDeGoogle::activa).orElseThrow(CuentaGoogleNoConectadaException::new);
        String huella = HuellaDeSecreto.de(cuenta.refreshToken());
        Guardado actual = guardado;
        if (actual != null && actual.huellaDelPermiso().equals(huella) && actual.venceEn().isAfter(clock.now().plusSeconds(MARGEN_SEGUNDOS))) {
            return new Credencial(actual.token(), cuenta.calendarioParaLasSalas());
        }
        String token = renovar(cuenta.refreshToken(), huella);
        cuenta.registrarUso(clock.now());
        saveCuentaPort.guardar(cuenta);
        return new Credencial(token, cuenta.calendarioParaLasSalas());
    }

    /** Descarta el token en memoria: Google dijo 401 y hay que pedir otro. */
    void invalidar() {
        guardado = null;
    }

    /** Un token de acceso para un permiso permanente que todavía no está guardado (la conexión recién hecha). No se guarda en memoria. */
    String paraUnPermisoNuevo(String refreshToken) {
        return pedirToken(refreshToken).token();
    }

    private String renovar(String refreshToken, String huella) {
        Guardado nuevo = pedirToken(refreshToken);
        guardado = new Guardado(nuevo.token(), nuevo.venceEn(), huella);
        return nuevo.token();
    }

    private Guardado pedirToken(String refreshToken) {
        MultiValueMap<String, String> campos = new LinkedMultiValueMap<>();
        campos.add("grant_type", "refresh_token");
        campos.add("refresh_token", refreshToken);
        campos.add("client_id", configuracion.clientId());
        campos.add("client_secret", configuracion.clientSecret());
        Respuesta respuesta = llamadas.formulario(EXTREMO_DE_TOKENS, campos);
        if (!respuesta.ok()) {
            if ("invalid_grant".equals(respuesta.motivo())) {
                throw new CuentaGoogleRevocadaException("Google ya no acepta el permiso de la cuenta (invalid_grant).");
            }
            throw llamadas.falla(respuesta);
        }
        String token = respuesta.cuerpo().path("access_token").asText("");
        if (token.isEmpty()) {
            throw new CuentaGoogleRevocadaException("Google no devolvió un token de acceso.");
        }
        long segundos = respuesta.cuerpo().path("expires_in").asLong(3600);
        return new Guardado(token, clock.now().plusSeconds(segundos), null);
    }
}
