package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A qué grupo cae cada familia de rutas (spec 050, FR-013): el orden de decisión del plan §5 y la lista corta de lo exento.
 */
class ClasificadorDeGruposTest {

    private static final String ID = "3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f";

    // spec 050 FR-013
    @ParameterizedTest(name = "{0} {1} → {2}")
    @DisplayName("cada familia de rutas cae en su grupo")
    @CsvSource({
            "GET,/api/v1/habits,LECTURA",
            "GET,/api/v1/home,LECTURA",
            "HEAD,/api/v1/habits,LECTURA",
            "POST,/api/v1/habit-tracks,ESCRITURA",
            "PUT,/api/v1/mapa-renacimiento/acciones,ESCRITURA",
            "PATCH,/api/v1/users/me,ESCRITURA",
            "DELETE,/api/v1/habits/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f,ESCRITURA",
            "POST,/api/v1/wall,MURO",
            "POST,/api/v1/wall/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/comments,MURO",
            "GET,/api/v1/wall,LECTURA",
            "GET,/api/v1/wall/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/comments,LECTURA",
            "POST,/api/v1/wall/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/react,ESCRITURA",
            "PATCH,/api/v1/wall/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f,ESCRITURA",
            "POST,/api/v1/chat/conversations/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/messages,CHAT",
            "POST,/api/v1/chat/conversations/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/messages/share-wall-post,CHAT",
            "GET,/api/v1/chat/conversations/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/messages,LECTURA",
            "POST,/api/v1/chat/conversations,ESCRITURA",
            "POST,/api/v1/chat/conversations/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/media/upload-url,SUBIDAS",
            "POST,/api/v1/wall/media/upload-url,SUBIDAS",
            "POST,/api/v1/habit-tracks/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/evidence/upload-url,SUBIDAS",
            "POST,/api/v1/admin/eventos/portada/upload-url,SUBIDAS",
            "POST,/api/v1/onboarding/media/presign,SUBIDAS",
            "GET,/api/v1/admin/caja/export.csv,EXPORTACIONES",
            "GET,/api/v1/admin/diagnostico/exportar.csv,EXPORTACIONES",
            "POST,/api/v1/analisis-80-20/generar,IA_FUERA_DE_SER",
            "POST,/api/v1/espejo-sombra/generar,IA_FUERA_DE_SER",
            "POST,/api/v1/mapa-renacimiento/completar,IA_FUERA_DE_SER",
            "GET,/api/v1/analisis-80-20,LECTURA",
            "GET,/api/v1/espejo-sombra,LECTURA",
            "POST,/api/v1/renasia/mensajes,SER",
            "POST,/api/v1/renasia/voz,SER",
            "POST,/api/v1/renasia/propuestas/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/confirmar,SER",
            "GET,/api/v1/renasia/mensajes,LECTURA",
            "DELETE,/api/v1/renasia/memoria,ESCRITURA",
            "GET,/api/v1/admin/trainees,ADMIN",
            "GET,/api/v1/admin/pagos/cuotas,ADMIN",
            "DELETE,/api/v1/admin/pagos/cuotas/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f,ADMIN",
            "PUT,/api/v1/admin/trainees/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/program-day,ADMIN",
            "POST,/api/v1/webhooks/whatsapp,WEBHOOK_WHATSAPP",
    })
    void cadaFamiliaDeRutasCaeEnSuGrupo(String metodo, String ruta, GrupoDeLimite esperado) {
        assertThat(ClasificadorDeGrupos.grupoDe(metodo, ruta)).contains(esperado);
    }

    // spec 050 FR-013
    @Test
    @DisplayName("el orden de decisión: una subida o una exportación de administración no cuenta como administración")
    void elOrdenDeDecisionPonePrimeroLoEspecifico() {
        assertThat(ClasificadorDeGrupos.grupoDe("POST", "/api/v1/admin/x/upload-url")).contains(GrupoDeLimite.SUBIDAS);
        assertThat(ClasificadorDeGrupos.grupoDe("GET", "/api/v1/admin/x/export.csv")).contains(GrupoDeLimite.EXPORTACIONES);
        assertThat(ClasificadorDeGrupos.grupoDe("POST", "/api/v1/wall/media/upload-url"))
                .as("la URL de subida del Muro es una subida, no una publicación").contains(GrupoDeLimite.SUBIDAS);
    }

    // spec 050 FR-013
    @Test
    @DisplayName("las lecturas con POST: el perfil propio se lee con POST y es lectura, no escritura")
    void laLecturaDelPerfilPropioConPostEsLectura() {
        assertThat(ClasificadorDeGrupos.grupoDe("POST", "/api/v1/users/me")).contains(GrupoDeLimite.LECTURA);
        assertThat(ClasificadorDeGrupos.grupoDe("POST", "/api/v1/users/me/avatar/upload-url")).contains(GrupoDeLimite.SUBIDAS);
        assertThat(ClasificadorDeGrupos.grupoDe("POST", "/api/v1/users/invite")).contains(GrupoDeLimite.ESCRITURA);
    }

    // spec 050 FR-013
    @ParameterizedTest(name = "{0} {1} está exenta")
    @DisplayName("lo exento por escrito: rutas públicas de autenticación y alta, lo que no es de la API y el OPTIONS del CORS")
    @CsvSource({
            "POST,/api/v1/auth/login",
            "POST,/api/v1/auth/social",
            "POST,/api/v1/auth/social/complete",
            "POST,/api/v1/auth/password/reset",
            "POST,/api/v1/auth/password/forgot",
            "POST,/api/v1/auth/email-verification/confirm",
            "GET,/api/v1/auth/whatsapp-code",
            "POST,/api/v1/auth/whatsapp-code/password",
            "POST,/api/v1/auth/whatsapp-code/registration",
            "GET,/api/v1/integraciones/google/callback",
            "POST,/api/v1/account-requests",
            "POST,/api/v1/account-requests/check-email",
            "POST,/api/v1/account-requests/exists",
            "POST,/api/v1/account-requests/verify-email",
            "GET,/api/v1/account-requests/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/status",
            "POST,/api/v1/account-deletion/request-code",
            "POST,/api/v1/account-deletion/confirm",
            "GET,/ws",
            "GET,/salud",
            "GET,/actuator/health",
            "OPTIONS,/api/v1/wall",
            "OPTIONS,/api/v1/habits",
    })
    void loExentoNoTieneGrupo(String metodo, String ruta) {
        assertThat(ClasificadorDeGrupos.estaExento(metodo, ruta)).isTrue();
        assertThat(ClasificadorDeGrupos.grupoDe(metodo, ruta)).isEqualTo(Optional.empty());
    }

    // spec 050 FR-013
    @ParameterizedTest(name = "{0} {1} NO está exenta ({2})")
    @DisplayName("lo que se parece a lo público pero tiene sesión sí se cuenta")
    @CsvSource({
            "GET,/api/v1/account-requests,LECTURA",
            "POST,/api/v1/account-requests/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f/approve,ESCRITURA",
            "DELETE,/api/v1/account-requests/3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f,ESCRITURA",
            "POST,/api/v1/auth/logout,ESCRITURA",
            "GET,/api/v1/auth/me,LECTURA",
            "POST,/api/v1/auth/social/link,ESCRITURA",
            "POST,/api/v1/integraciones/google/callback,ESCRITURA",
    })
    void loQueTieneSesionSiSeCuenta(String metodo, String ruta, GrupoDeLimite esperado) {
        assertThat(ClasificadorDeGrupos.grupoDe(metodo, ruta)).contains(esperado);
    }

    // spec 050 FR-013
    @Test
    @DisplayName("el método sin importar mayúsculas y una barra final no cambian el grupo")
    void metodoEnMinusculasYBarraFinal() {
        assertThat(ClasificadorDeGrupos.grupoDe("post", "/api/v1/wall")).contains(GrupoDeLimite.MURO);
        assertThat(ClasificadorDeGrupos.grupoDe("POST", "/api/v1/wall/")).contains(GrupoDeLimite.MURO);
        assertThat(ClasificadorDeGrupos.grupoDe("POST", "/api/v1/auth/login/")).isEmpty();
    }

    // spec 050 FR-013
    @Test
    @DisplayName("el patrón: * es un segmento, ** es lo que siga (incluso nada) y no hay coincidencias a medias")
    void elPatronDeRutas() {
        assertThat(ClasificadorDeGrupos.coincide("/api/v1/wall/*/comments", "/api/v1/wall/" + ID + "/comments")).isTrue();
        assertThat(ClasificadorDeGrupos.coincide("/api/v1/wall/*/comments", "/api/v1/wall/comments")).isFalse();
        assertThat(ClasificadorDeGrupos.coincide("/api/v1/wall/*/comments", "/api/v1/wall/" + ID + "/comments/x")).isFalse();
        assertThat(ClasificadorDeGrupos.coincide("/api/v1/renasia/**", "/api/v1/renasia")).isTrue();
        assertThat(ClasificadorDeGrupos.coincide("/api/v1/renasia/**", "/api/v1/renasia/voz/en-vivo")).isTrue();
        assertThat(ClasificadorDeGrupos.coincide("/api/v1/renasia/**", "/api/v1/renasiax")).isFalse();
        assertThat(ClasificadorDeGrupos.coincide("/api/v1/wall", "/api/v1/wallpaper")).isFalse();
    }
}
