package com.renaser.os.shared.web.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La lista de rutas sin sesión del tope absoluto ({@link RutasSinSesion}) es una copia de lo que {@code SecurityConfig}
 * deja en {@code permitAll()}. Esta prueba lee ese archivo y rompe el build si se separan: una ruta pública nueva que el
 * tope no conoce dejaría a quien trae un token viejo sin poder usarla; una que ya no es pública y sigue aquí le daría una
 * puerta que no debería tener.
 */
class RutasSinSesionTest {

    private static final Path SECURITY_CONFIG = Path.of("src/main/java/com/renaser/os/shared/web/SecurityConfig.java");
    private static final Pattern PERMIT_ALL = Pattern.compile("\\.requestMatchers\\(([^()]*)\\)\\s*\\.permitAll\\(\\)");
    private static final Pattern METODO = Pattern.compile("HttpMethod\\.(\\w+)");
    private static final Pattern RUTA = Pattern.compile("\"([^\"]+)\"");

    /** Cada {@code requestMatchers(...).permitAll()} de la configuración, como «MÉTODO ruta» ({@code *} = cualquiera). */
    private static Set<String> lasPermitidasEnLaConfiguracion() throws Exception {
        String fuente = Files.readString(SECURITY_CONFIG);
        Set<String> rutas = new LinkedHashSet<>();
        Matcher bloque = PERMIT_ALL.matcher(fuente);
        while (bloque.find()) {
            String argumentos = bloque.group(1);
            Matcher metodo = METODO.matcher(argumentos);
            String verbo = metodo.find() ? metodo.group(1) : "*";
            Matcher ruta = RUTA.matcher(argumentos);
            while (ruta.find()) {
                rutas.add(verbo + " " + ruta.group(1));
            }
        }
        return rutas;
    }

    private static Set<String> lasDelFiltro() {
        Set<String> rutas = new LinkedHashSet<>();
        for (RutasSinSesion.Ruta ruta : RutasSinSesion.LAS_PUBLICAS) {
            rutas.add(ruta.metodo() + " " + ruta.patron());
        }
        return rutas;
    }

    // spec 050 FR-028
    @Test
    @DisplayName("las rutas sin sesión del tope son exactamente las permitAll() de SecurityConfig")
    void lasMismasQueLaConfiguracion() throws Exception {
        Set<String> enLaConfiguracion = lasPermitidasEnLaConfiguracion();

        assertThat(enLaConfiguracion).as("la prueba encontró las rutas públicas de SecurityConfig").hasSizeGreaterThan(10);
        assertThat(lasDelFiltro())
                .as("RutasSinSesion.LAS_PUBLICAS debe ser igual a lo que SecurityConfig deja en permitAll(). Si agregaste "
                        + "o quitaste una ruta pública, haz lo mismo en RutasSinSesion (src/main/java/com/renaser/os/"
                        + "shared/web/security/RutasSinSesion.java)")
                .containsExactlyInAnyOrderElementsOf(enLaConfiguracion);
    }

    @Test
    @DisplayName("el método y el patrón se respetan: login con cualquier verbo, el estado de una solicitud solo con GET")
    void metodoYPatron() {
        assertThat(RutasSinSesion.esDeLasPublicas("POST", "/api/v1/auth/login")).isTrue();
        assertThat(RutasSinSesion.esDeLasPublicas("POST", "/api/v1/auth/password/reset")).isTrue();
        assertThat(RutasSinSesion.esDeLasPublicas("GET", "/api/v1/account-requests/3f2a/status")).isTrue();
        assertThat(RutasSinSesion.esDeLasPublicas("POST", "/api/v1/account-requests/3f2a/status")).isFalse();
        assertThat(RutasSinSesion.esDeLasPublicas("GET", "/api/v1/account-requests")).isFalse();
        assertThat(RutasSinSesion.esDeLasPublicas("POST", "/api/v1/account-requests")).isTrue();
        assertThat(RutasSinSesion.esDeLasPublicas("GET", "/api/v1/home")).isFalse();
        assertThat(RutasSinSesion.esDeLasPublicas("POST", "/api/v1/auth/logout")).as("salir pide sesión").isFalse();
        assertThat(RutasSinSesion.esDeLasPublicas("POST", "/api/v1/auth/loginx")).isFalse();
    }
}
