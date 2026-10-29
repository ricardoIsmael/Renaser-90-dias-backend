package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.application.ports.out.push.MensajePush;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import nl.martijndwars.webpush.Utils;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-647 (issue #26, 2026-10-28): el Web Push de un aviso «a una hora» lleva un TTL que termina a esa hora y todo
 * Web Push lleva {@code Urgency: high}. Con {@code new Notification(subscription, payload)} el TTL era el de la
 * librería (28 días) y no había {@code Urgency}: en iPhone (Safari) el servicio de push de Apple retenía el aviso y
 * lo entregaba horas tarde. Contra el código anterior estas pruebas no pasan: ni el TTL seguía a {@code venceEn} ni
 * la notificación traía urgencia.
 *
 * <p>No hay red: se arma la {@link Notification} con el método del adaptador y se mira lo que la librería pondría en
 * el pedido ({@link PushService#preparePost}, que solo arma el {@code POST}).
 *
 * <p>Los instantes cruzan la medianoche UTC a propósito (regla 03): las 19:10 de Lima son las 00:10 UTC del día
 * siguiente, y los segundos que faltan son una diferencia de instantes, no de fechas locales.
 */
class WebPushAdapterVencimientoTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T11:50:00Z");
    /** 07:00 del 28 de octubre en Lima. */
    private static final Instant A_LAS_07_EN_LIMA = Instant.parse("2026-10-28T12:00:00Z");
    private static final String ENDPOINT = "https://push.example.test/sub";

    private static String p256dh;
    private static String auth;

    @BeforeAll
    static void clavesDeUnNavegador() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator generador = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generador.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
        KeyPair claves = generador.generateKeyPair();
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        p256dh = base64.encodeToString(Utils.encode((ECPublicKey) claves.getPublic()));
        auth = base64.encodeToString(new byte[16]);
    }

    private static WebPushAdapter adaptador(Instant ahora) {
        return new WebPushAdapter("pub", "priv", "mailto:soporte@renaser.test", FixedClock.at(ahora));
    }

    private static MensajePush aviso(Instant venceEn) {
        return new MensajePush(TipoNotificacion.RECORDATORIO_HABITO, "Tu hábito está por empezar",
                "Pastilla Renacer: te toca a las 07:00. En juego: 10 puntos.", "/habitos/abc?dimension=SPIRIT",
                "habito-abc-inicio", venceEn);
    }

    private static Notification notificacion(WebPushAdapter adaptador, MensajePush mensaje) throws Exception {
        return adaptador.notificacionDe(ENDPOINT, p256dh, auth, "{}", mensaje);
    }

    @Test
    @DisplayName("con venceEn el TTL son los segundos que faltan: 10 minutos para las 07:00")
    void conVencimientoElTtlSonLosSegundosQueFaltan() throws Exception {
        Notification notificacion = notificacion(adaptador(AHORA), aviso(A_LAS_07_EN_LIMA));

        assertThat(notificacion.getTTL()).isEqualTo(600);
    }

    @Test
    @DisplayName("el TTL es una diferencia de instantes: cruzar la medianoche UTC no lo corre un dia")
    void cruzarLaMedianocheUtcNoLoCorre() throws Exception {
        Instant a_las_18_50_en_lima = Instant.parse("2026-10-28T23:50:00Z");
        Instant a_las_19_10_en_lima = Instant.parse("2026-10-29T00:10:00Z");

        assertThat(notificacion(adaptador(a_las_18_50_en_lima), aviso(a_las_19_10_en_lima)).getTTL())
                .isEqualTo(20 * 60);
    }

    @Test
    @DisplayName("una fraccion de segundo se descarta hacia abajo: el aviso no vive mas alla de su hora")
    void laFraccionDeSegundoSeDescarta() throws Exception {
        // Faltan 599,7 s: el TTL es 599 y no 600.
        Instant ahora = AHORA.plusMillis(400);
        Instant venceEn = A_LAS_07_EN_LIMA.plusMillis(100);

        assertThat(notificacion(adaptador(ahora), aviso(venceEn)).getTTL()).isEqualTo(599);
    }

    @Test
    @DisplayName("a menos de un segundo de vencer el TTL es 0, no 1")
    void aMenosDeUnSegundoEsCero() throws Exception {
        Instant venceEn = AHORA.plusMillis(900);

        assertThat(notificacion(adaptador(AHORA), aviso(venceEn)).getTTL()).isZero();
    }

    @Test
    @DisplayName("un aviso que ya vencio sale con TTL 0 (el servicio de push no lo retiene), nunca negativo")
    void unAvisoVencidoSaleConTtlCero() throws Exception {
        assertThat(notificacion(adaptador(AHORA), aviso(AHORA)).getTTL()).isZero();
        assertThat(notificacion(adaptador(AHORA), aviso(AHORA.minusSeconds(1))).getTTL()).isZero();
        assertThat(notificacion(adaptador(AHORA), aviso(AHORA.minus(3, ChronoUnit.DAYS))).getTTL()).isZero();
    }

    @Test
    @DisplayName("un instante lejanisimo no desborda el int del TTL")
    void unInstanteLejanisimoNoDesborda() throws Exception {
        Instant dentroDeDoscientosAnios = AHORA.plus(200L * 365, ChronoUnit.DAYS);

        assertThat(notificacion(adaptador(AHORA), aviso(dentroDeDoscientosAnios)).getTTL())
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("un reintento manda los segundos que faltan EN ESE MOMENTO, no los del primer intento")
    void unReintentoRecalculaElTtl() throws Exception {
        Instant[] ahora = {AHORA};
        Clock reloj = new Clock() {
            @Override
            public Instant now() {
                return ahora[0];
            }

            @Override
            public LocalDate today() {
                return LocalDate.ofInstant(ahora[0], java.time.ZoneOffset.UTC);
            }
        };
        WebPushAdapter adaptador = new WebPushAdapter("pub", "priv", "mailto:soporte@renaser.test", reloj);
        MensajePush mensaje = aviso(A_LAS_07_EN_LIMA);

        assertThat(notificacion(adaptador, mensaje).getTTL()).isEqualTo(600);
        ahora[0] = AHORA.plusSeconds(1);
        assertThat(notificacion(adaptador, mensaje).getTTL()).isEqualTo(599);
        ahora[0] = A_LAS_07_EN_LIMA.plusSeconds(1);
        assertThat(notificacion(adaptador, mensaje).getTTL()).isZero();
    }

    @Test
    @DisplayName("sin venceEn (anuncios, chat, avisos manuales) el TTL es el de siempre: el de la libreria")
    void sinVencimientoElTtlEsElDeSiempre() throws Exception {
        int ttlDeSiempre = new Notification(ENDPOINT, p256dh, auth, "{}").getTTL();

        assertThat(ttlDeSiempre).isPositive();
        assertThat(notificacion(adaptador(AHORA), aviso(null)).getTTL()).isEqualTo(ttlDeSiempre);
        assertThat(notificacion(adaptador(AHORA), new MensajePush(TipoNotificacion.MENSAJE_CHAT, "t", "c", "/chat/1"))
                .getTTL()).isEqualTo(ttlDeSiempre);
    }

    @ParameterizedTest
    @EnumSource(TipoNotificacion.class)
    @DisplayName("la urgencia es HIGH en todo aviso, caduque o no, sea cual sea el tipo")
    void laUrgenciaEsAltaSiempre(TipoNotificacion tipo) throws Exception {
        WebPushAdapter adaptador = adaptador(AHORA);

        assertThat(notificacion(adaptador, new MensajePush(tipo, "t", "c", "/ruta")).getUrgency())
                .isEqualTo(Urgency.HIGH);
        assertThat(notificacion(adaptador, new MensajePush(tipo, "t", "c", "/ruta", null, A_LAS_07_EN_LIMA))
                .getUrgency()).isEqualTo(Urgency.HIGH);
        assertThat(notificacion(adaptador, new MensajePush(tipo, "t", "c", "/ruta", null, AHORA.minusSeconds(60)))
                .getUrgency()).isEqualTo(Urgency.HIGH);
    }

    @Test
    @DisplayName("la notificacion conserva endpoint, claves y cuerpo: solo se suman TTL y urgencia")
    void conservaLoDemas() throws Exception {
        Notification notificacion = adaptador(AHORA).notificacionDe(ENDPOINT, p256dh, auth, "{\"title\":\"áé\"}",
                aviso(A_LAS_07_EN_LIMA));

        assertThat(notificacion.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(notificacion.getPayload()).isEqualTo("{\"title\":\"áé\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(notificacion.getUserAuth()).isEqualTo(Base64.getUrlDecoder().decode(auth));
        assertThat(notificacion.getUserPublicKey()).isEqualTo(
                new Notification(ENDPOINT, p256dh, auth, "{}").getUserPublicKey());
        assertThat(notificacion.getTopic()).isNull();
    }

    // ─── Lo que PushService pone de verdad en el pedido (sin red) ────────────────────────────

    @Test
    @DisplayName("PushService manda las cabeceras TTL y Urgency tal como las lleva la notificacion")
    void pushServiceMandaLasCabeceras() throws Exception {
        PushService servicio = new PushService();

        HttpPost conVencimiento = servicio.preparePost(notificacion(adaptador(AHORA), aviso(A_LAS_07_EN_LIMA)),
                Encoding.AESGCM);
        HttpPost sinVencimiento = servicio.preparePost(notificacion(adaptador(AHORA), aviso(null)),
                Encoding.AESGCM);

        assertThat(conVencimiento.getFirstHeader("TTL").getValue()).isEqualTo("600");
        assertThat(conVencimiento.getFirstHeader("Urgency").getValue()).isEqualTo("high");
        assertThat(sinVencimiento.getFirstHeader("TTL").getValue())
                .isEqualTo(String.valueOf(new Notification(ENDPOINT, p256dh, auth, "{}").getTTL()));
        assertThat(sinVencimiento.getFirstHeader("Urgency").getValue()).isEqualTo("high");
    }
}
