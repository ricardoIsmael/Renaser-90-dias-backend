package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.renaser.os.calendar.application.ports.out.meet.CifradoDeSecretosPort;
import com.renaser.os.calendar.application.ports.out.meet.ConfiguracionDeGooglePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Las credenciales de Google de Renaser para los eventos (spec 040, FR-010 y FR-011). Llegan por variables de entorno —en producción desde SSM,
 * como el resto de los secretos— y nunca están en el código ni en las pruebas. Son las de un proyecto de Google Cloud y un cliente OAuth PROPIOS de
 * Renaser: distintos de los de RenaserOs, que comparte el Gmail pero guarda su propio permiso.
 *
 * <p>Los permisos por defecto son los mínimos: el calendario propio de la app ({@code calendar.app.created}, FR-034) y {@code meetings.space.settings}
 * (quién puede entrar a la sala, FR-023), más {@code openid email} para saber de qué cuenta es el permiso. Si SC-001 mostrara que el permiso reducido
 * no alcanza, se cambia {@code EVENTOS_GOOGLE_PERMISOS} a {@code calendar.events} sin tocar el código.
 */
@Component
class ConfiguracionDeGoogle implements ConfiguracionDeGooglePort {

    static final String PERMISOS_POR_DEFECTO = "https://www.googleapis.com/auth/calendar.app.created "
            + "https://www.googleapis.com/auth/meetings.space.settings openid email";
    private static final Set<String> PERMISOS_DE_IDENTIDAD = Set.of("openid", "email", "profile");

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final Set<String> permisos;
    private final String correoEsperado;
    private final int ventanaEnDias;
    private final CifradoDeSecretosPort cifrado;

    ConfiguracionDeGoogle(@Value("${renaser.calendar.meet.oauth.client-id:}") String clientId,
                          @Value("${renaser.calendar.meet.oauth.client-secret:}") String clientSecret,
                          @Value("${renaser.calendar.meet.oauth.redirect-uri:}") String redirectUri,
                          @Value("${renaser.calendar.meet.oauth.permisos:" + PERMISOS_POR_DEFECTO + "}") String permisos,
                          @Value("${renaser.calendar.meet.correo-esperado:}") String correoEsperado,
                          @Value("${renaser.calendar.meet.ventana-dias:60}") int ventanaEnDias,
                          CifradoDeSecretosPort cifrado) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.redirectUri = redirectUri.trim();
        this.permisos = new LinkedHashSet<>(Arrays.asList(permisos.trim().split("\\s+")));
        this.correoEsperado = correoEsperado.trim();
        this.ventanaEnDias = ventanaEnDias;
        this.cifrado = cifrado;
    }

    @Override
    public boolean estaConfigurada() {
        return !clientId.isEmpty() && !clientSecret.isEmpty() && !redirectUri.isEmpty() && cifrado.estaConfigurado();
    }

    @Override
    public String correoEsperado() {
        return correoEsperado;
    }

    @Override
    public int ventanaEnDias() {
        return ventanaEnDias;
    }

    String clientId() {
        return clientId;
    }

    String clientSecret() {
        return clientSecret;
    }

    String redirectUri() {
        return redirectUri;
    }

    /** Todos los permisos que se piden, con los de identidad. */
    Set<String> permisosPedidos() {
        return permisos;
    }

    /** Los que Google tiene que dar todos: los pedidos sin los de identidad. */
    Set<String> permisosObligatorios() {
        Set<String> obligatorios = new LinkedHashSet<>(permisos);
        obligatorios.removeAll(PERMISOS_DE_IDENTIDAD);
        return obligatorios;
    }
}
