package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.application.ports.out.push.MensajePush;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-188 (E-9 del 26/09): el push de Expo nombra el canal de Android según el tipo del aviso, solo
 * con su propiedad encendida, y con todo apagado (el default) manda el mismo cuerpo de antes.
 *
 * <p>Antes de D-188 el cuerpo no llevaba {@code channelId} para ningún tipo, así que las pruebas
 * que piden un canal fallan contra ese código.
 */
class ExpoPushTransporteCanalTest {

    private static final Instant AHORA = Instant.parse("2026-09-26T15:00:00Z");

    private static TokenPush token(PlataformaPush plataforma) {
        return TokenPush.rehydrate(TokenPushId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                "ExponentPushToken[abc]", plataforma, AHORA, AHORA);
    }

    private static ExpoPushTransporte transporte(boolean acompanamiento, boolean recordatorios) {
        return new ExpoPushTransporte("", true, acompanamiento, recordatorios);
    }

    private static ExpoPushTransporte todoEncendido() {
        return transporte(true, true);
    }

    private static ExpoPushTransporte todoApagado() {
        return transporte(false, false);
    }

    private static String cuerpo(ExpoPushTransporte transporte, PlataformaPush plataforma, TipoNotificacion tipo) {
        return transporte.cuerpoJson(token(plataforma), new MensajePush(tipo, "Titulo", "Cuerpo", "/ruta"));
    }

    @ParameterizedTest
    @EnumSource(TipoNotificacion.class)
    @DisplayName("con todo apagado (el default) el cuerpo es el de antes de D-188, más la prioridad alta de D-217")
    void apagadoEsIdenticoAlDeAntes(TipoNotificacion tipo) {
        assertThat(cuerpo(todoApagado(), PlataformaPush.ANDROID, tipo))
                .isEqualTo("{\"to\":\"ExponentPushToken[abc]\",\"title\":\"Titulo\",\"body\":\"Cuerpo\","
                        + "\"sound\":\"default\",\"priority\":\"high\",\"data\":{\"route\":\"/ruta\"}}");
    }

    @Test
    @DisplayName("encendido, el aviso de acompañamiento sale por el canal que la app crea al registrar el token")
    void acompanamientoPorSuCanal() {
        assertThat(cuerpo(transporte(true, false), PlataformaPush.ANDROID, TipoNotificacion.ACOMPANAMIENTO_ALUMNO))
                .contains("\"channelId\":\"avisos-acompanamiento\"");
    }

    @Test
    @DisplayName("el de acompañamiento no se enciende con la propiedad de los recordatorios")
    void acompanamientoTieneSuPropiaPropiedad() {
        assertThat(cuerpo(transporte(false, true), PlataformaPush.ANDROID, TipoNotificacion.ACOMPANAMIENTO_ALUMNO))
                .doesNotContain("channelId");
    }

    @Test
    @DisplayName("con los canales de recordatorios encendidos, evento y hábito van a su canal BASE")
    void recordatoriosASuCanalBase() {
        ExpoPushTransporte conCanales = transporte(false, true);

        assertThat(cuerpo(conCanales, PlataformaPush.ANDROID, TipoNotificacion.RECORDATORIO_EVENTO))
                .contains("\"channelId\":\"recordatorios-eventos\"");
        assertThat(cuerpo(conCanales, PlataformaPush.ANDROID, TipoNotificacion.RECORDATORIO_HABITO))
                .contains("\"channelId\":\"recordatorios-habitos\"");
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacion.class, names = {"RECORDATORIO_EVENTO", "RECORDATORIO_HABITO"})
    @DisplayName("apagado: un recordatorio NO nombra un canal que el APK viejo puede no tener")
    void recordatoriosSinCanalPorDefecto(TipoNotificacion tipo) {
        assertThat(cuerpo(transporte(true, false), PlataformaPush.ANDROID, tipo)).doesNotContain("channelId");
    }

    @ParameterizedTest
    @EnumSource(value = TipoNotificacion.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"RECORDATORIO_EVENTO", "RECORDATORIO_HABITO", "ACOMPANAMIENTO_ALUMNO"})
    @DisplayName("el resto de los tipos sigue sin channelId: canal por defecto, como antes")
    void elRestoSinCanal(TipoNotificacion tipo) {
        assertThat(cuerpo(todoEncendido(), PlataformaPush.ANDROID, tipo)).doesNotContain("channelId");
    }

    @Test
    @DisplayName("sin tipo no se elige canal")
    void sinTipoSinCanal() {
        assertThat(cuerpo(todoEncendido(), PlataformaPush.ANDROID, null)).doesNotContain("channelId");
    }

    @Test
    @DisplayName("en iOS nunca va channelId: el campo es solo de Android")
    void iosSinCanal() {
        assertThat(cuerpo(todoEncendido(), PlataformaPush.IOS, TipoNotificacion.ACOMPANAMIENTO_ALUMNO))
                .doesNotContain("channelId");
    }

    @Test
    @DisplayName("el resto del cuerpo no cambia: título, cuerpo, sonido y ruta siguen ahí")
    void elCuerpoSeConserva() {
        assertThat(cuerpo(todoEncendido(), PlataformaPush.ANDROID, TipoNotificacion.RECORDATORIO_EVENTO))
                .isEqualTo("{\"to\":\"ExponentPushToken[abc]\",\"title\":\"Titulo\",\"body\":\"Cuerpo\","
                        + "\"sound\":\"default\",\"priority\":\"high\",\"channelId\":\"recordatorios-eventos\","
                        + "\"data\":{\"route\":\"/ruta\"}}");
    }

    @Test
    @DisplayName("D-218: la ruta del habito, con su ?dimension=, viaja tal cual en data.route y el JSON sigue valido")
    void rutaDelHabitoConConsulta() throws Exception {
        String ruta = "/habitos/0f000000-0000-4000-8000-000000000001?dimension=BODY";
        String json = todoApagado().cuerpoJson(token(PlataformaPush.ANDROID),
                new MensajePush(TipoNotificacion.RECORDATORIO_HABITO, "Titulo", "Cuerpo", ruta));

        var leido = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        assertThat(leido.path("data").path("route").asText()).isEqualTo(ruta);
    }
}
