package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.application.ports.out.push.MensajePush;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 007, FR-014: el push de un aviso de habito lleva {@code tag} y {@code threadId} con la etiqueta
 * {@code habito-{id}-inicio} / {@code -por-vencer}, para que el aviso del servidor y la alarma local del
 * telefono (que usa el mismo identificador) se reemplacen en vez de apilarse. La etiqueta de chat (D-221)
 * no cambia; eso lo prueba {@code AvisoDeChatEnElPushTest}.
 */
class ExpoPushTransporteTest {

    private static final Instant AHORA = Instant.parse("2026-09-06T01:50:00Z");
    private static final String HABITO = "66507383-7219-43ab-aa42-2fbc76152b82";
    private static final String RUTA = "/habitos/" + HABITO + "?dimension=BODY";

    private static TokenPush token(PlataformaPush plataforma) {
        return TokenPush.rehydrate(TokenPushId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                "ExponentPushToken[abc]", plataforma, AHORA, AHORA);
    }

    private static ExpoPushTransporte transporte() {
        return new ExpoPushTransporte("", true, false, false, false);
    }

    private static MensajePush aviso(String etiqueta) {
        return new MensajePush(TipoNotificacion.RECORDATORIO_HABITO, "Tu hábito está por empezar",
                "Meditar: te toca a las 21:00. En juego: 10 puntos.", RUTA, etiqueta);
    }

    @Test
    @DisplayName("Android: el aviso de inicio lleva tag y threadId iguales a la etiqueta del habito")
    void inicioLlevaLaEtiqueta() {
        assertThat(transporte().cuerpoJson(token(PlataformaPush.ANDROID), aviso("habito-" + HABITO + "-inicio")))
                .contains("\"tag\":\"habito-" + HABITO + "-inicio\"")
                .contains("\"threadId\":\"habito-" + HABITO + "-inicio\"")
                .contains("\"title\":\"Tu hábito está por empezar\"")
                .contains("\"data\":{\"route\":\"" + RUTA + "\"}");
    }

    @Test
    @DisplayName("el de vencimiento lleva otra etiqueta, para no reemplazar al de inicio")
    void vencimientoLlevaOtraEtiqueta() {
        assertThat(transporte().cuerpoJson(token(PlataformaPush.ANDROID), aviso("habito-" + HABITO + "-por-vencer")))
                .contains("\"tag\":\"habito-" + HABITO + "-por-vencer\"")
                .doesNotContain("-inicio");
    }

    @Test
    @DisplayName("iOS: lo agrupa el mismo threadId")
    void ios() {
        assertThat(transporte().cuerpoJson(token(PlataformaPush.IOS), aviso("habito-" + HABITO + "-inicio")))
                .contains("\"threadId\":\"habito-" + HABITO + "-inicio\"");
    }

    @Test
    @DisplayName("sin etiqueta (habito sin id, evento viejo) no hay tag ni threadId: cada aviso es uno aparte")
    void sinEtiquetaNoHayTag() {
        assertThat(transporte().cuerpoJson(token(PlataformaPush.ANDROID), aviso(null)))
                .doesNotContain("\"tag\"")
                .doesNotContain("threadId");
    }

    @Test
    @DisplayName("una etiqueta vacia se trata como ausente")
    void etiquetaVaciaEsAusente() {
        assertThat(transporte().cuerpoJson(token(PlataformaPush.ANDROID), aviso("  ")))
                .doesNotContain("\"tag\"");
    }
}
