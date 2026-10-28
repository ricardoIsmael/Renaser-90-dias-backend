package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * E-645 (issue #22, 2026-10-28): el push de un aviso «a una hora» lleva {@code expiration} para que FCM no lo
 * entregue tarde. Sin el campo, un «GIMNASIO: te toca a las 07:00» que el servidor mandó antes de las 07:00
 * llegó pasadas las 8, cuando el teléfono volvió a tener red. Contra el código anterior estas pruebas no
 * pasan: {@code MensajePush} no tenía {@code venceEn} y el cuerpo nunca llevaba {@code expiration}.
 *
 * <p>Los instantes están en la madrugada UTC a propósito (regla 03): las 07:00 de Lima son las 12:00 UTC, pero
 * las 21:00 de Lima son las 02:00 UTC del día siguiente, y el timestamp Unix tiene que ser el del instante
 * absoluto, no el de una fecha local.
 */
class ExpoPushTransporteVencimientoTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T11:50:00Z");
    /** 07:00 del 28 de octubre en Lima. */
    private static final Instant A_LAS_07_EN_LIMA = Instant.parse("2026-10-28T12:00:00Z");
    /** 21:00 del 28 de octubre en Lima, que son las 02:00 UTC del 29. */
    private static final Instant A_LAS_21_EN_LIMA = Instant.parse("2026-10-29T02:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static TokenPush token(PlataformaPush plataforma) {
        return TokenPush.rehydrate(TokenPushId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                "ExponentPushToken[abc]", plataforma, AHORA, AHORA);
    }

    private static ExpoPushTransporte transporte() {
        return new ExpoPushTransporte("", true, false, false, false);
    }

    private static MensajePush habito(Instant venceEn) {
        return new MensajePush(TipoNotificacion.RECORDATORIO_HABITO, "Tu hábito está por empezar",
                "GIMNASIO: te toca a las 07:00. En juego: 10 puntos.", "/habitos/abc?dimension=BODY",
                "habito-abc-inicio", venceEn);
    }

    @ParameterizedTest
    @EnumSource(value = PlataformaPush.class, names = {"ANDROID", "IOS"})
    @DisplayName("con venceEn el cuerpo lleva expiration: el timestamp Unix en segundos (Android e iOS)")
    void conVencimientoLlevaExpiration(PlataformaPush plataforma) throws Exception {
        JsonNode json = JSON.readTree(transporte().cuerpoJson(token(plataforma), habito(A_LAS_07_EN_LIMA)));

        assertThat(json.path("expiration").isIntegralNumber()).isTrue();
        assertThat(json.path("expiration").asLong()).isEqualTo(A_LAS_07_EN_LIMA.getEpochSecond());
        assertThat(json.path("expiration").asLong()).isEqualTo(1_793_188_800L);
    }

    @Test
    @DisplayName("la expiracion es del instante absoluto: las 21:00 de Lima (02:00 UTC del dia siguiente) no se corren un dia")
    void laExpiracionEsElInstanteAbsoluto() throws Exception {
        JsonNode json = JSON.readTree(transporte().cuerpoJson(token(PlataformaPush.ANDROID),
                habito(A_LAS_21_EN_LIMA)));

        assertThat(Instant.ofEpochSecond(json.path("expiration").asLong())).isEqualTo(A_LAS_21_EN_LIMA);
    }

    @Test
    @DisplayName("son segundos y no milisegundos: Expo rechazaria (o tomaria por lejanisimo) un valor 1000 veces mayor")
    void sonSegundos() throws Exception {
        long expiracion = JSON.readTree(transporte().cuerpoJson(token(PlataformaPush.ANDROID),
                habito(A_LAS_07_EN_LIMA))).path("expiration").asLong();

        // En segundos son 10 digitos (hasta el año 5138 cabe en 11); en milisegundos serian 13.
        assertThat(expiracion).isLessThan(100_000_000_000L);
    }

    @Test
    @DisplayName("una fraccion de segundo se descarta: el aviso no vive mas alla de su hora")
    void laFraccionDeSegundoSeDescarta() throws Exception {
        Instant conFraccion = A_LAS_07_EN_LIMA.plusMillis(999);

        long expiracion = JSON.readTree(transporte().cuerpoJson(token(PlataformaPush.ANDROID), habito(conFraccion)))
                .path("expiration").asLong();

        assertThat(expiracion).isEqualTo(A_LAS_07_EN_LIMA.getEpochSecond());
    }

    @Test
    @DisplayName("no manda ttl: mandaria sobre expiration y habria que recalcularlo en cada reintento")
    void noMandaTtl() {
        assertThat(transporte().cuerpoJson(token(PlataformaPush.ANDROID), habito(A_LAS_07_EN_LIMA)))
                .doesNotContain("\"ttl\"");
    }

    @ParameterizedTest
    @EnumSource(TipoNotificacion.class)
    @DisplayName("sin venceEn (chat, logros, avisos manuales) el cuerpo no lleva expiration, sea cual sea el tipo")
    void sinVencimientoNoLlevaExpiration(TipoNotificacion tipo) {
        MensajePush mensaje = new MensajePush(tipo, "Titulo", "Cuerpo", "/ruta");

        assertThat(transporte().cuerpoJson(token(PlataformaPush.ANDROID), mensaje)).doesNotContain("expiration");
        assertThat(transporte().cuerpoJson(token(PlataformaPush.IOS), mensaje)).doesNotContain("expiration");
    }

    @Test
    @DisplayName("el cuerpo de un aviso de habito, con y sin venceEn: ejemplo exacto, y solo difieren en expiration")
    void ejemploExactoConYSinVencimiento() {
        TokenPush android = token(PlataformaPush.ANDROID);
        String sin = transporte().cuerpoJson(android, habito(null));
        String con = transporte().cuerpoJson(android, habito(A_LAS_07_EN_LIMA));

        assertThat(sin).isEqualTo("{\"to\":\"ExponentPushToken[abc]\",\"title\":\"Tu hábito está por empezar\","
                + "\"body\":\"GIMNASIO: te toca a las 07:00. En juego: 10 puntos.\",\"sound\":\"default\","
                + "\"priority\":\"high\",\"tag\":\"habito-abc-inicio\",\"threadId\":\"habito-abc-inicio\","
                + "\"data\":{\"route\":\"/habitos/abc?dimension=BODY\"}}");
        assertThat(con).isEqualTo(sin.replace("\"priority\":\"high\",",
                "\"priority\":\"high\",\"expiration\":1793188800,"));
    }

    @Test
    @DisplayName("con los canales de Android encendidos, expiration convive con channelId, tag y data")
    void convivenConElCanal() throws Exception {
        JsonNode json = JSON.readTree(new ExpoPushTransporte("", true, true, true, true)
                .cuerpoJson(token(PlataformaPush.ANDROID), habito(A_LAS_07_EN_LIMA)));

        assertThat(json.path("expiration").asLong()).isEqualTo(A_LAS_07_EN_LIMA.getEpochSecond());
        assertThat(json.path("channelId").asText()).isNotBlank();
        assertThat(json.path("tag").asText()).isEqualTo("habito-abc-inicio");
        assertThat(json.path("data").path("route").asText()).isEqualTo("/habitos/abc?dimension=BODY");
    }
}
