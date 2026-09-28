package com.renaser.os.notifications.domain.model.notificacion;

import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-217: el respaldo del push para los telefonos que no confirmaron sus alarmas locales.
 *
 * <p>El reloj esta a las 02:00 UTC a proposito (regla 02): en Lima son las 21:00 del dia ANTERIOR. La
 * vigencia se cuenta en horas sobre instantes, no en dias calendario de nadie, asi que no puede
 * depender de en que fecha local caiga el envio.
 */
class EntregaPushTest {

    private static final Instant AHORA = Instant.parse("2026-09-29T02:00:00Z");
    private static final UserId USUARIO = UserId.of(UUID.randomUUID());

    private static TokenPush token(PlataformaPush plataforma, Duration confirmadoHace) {
        TokenPush t = TokenPush.registrar(TokenPushId.of(UUID.randomUUID()), USUARIO, "tok-" + UUID.randomUUID(),
                plataforma, FixedClock.at(AHORA.minus(Duration.ofDays(10))));
        if (confirmadoHace != null) {
            t.confirmarAlarmasLocales(FixedClock.at(AHORA.minus(confirmadoHace)));
        }
        return t;
    }

    @Test
    @DisplayName("nunca confirmo (el APK anterior a D-217) -> navegador y telefono")
    void nuncaConfirmoRecibeElPush() {
        TokenPush web = token(PlataformaPush.WEB, null);
        TokenPush android = token(PlataformaPush.ANDROID, null);

        assertThat(EntregaPush.RESPALDO_DE_ALARMA_LOCAL.filtrar(List.of(web, android), AHORA))
                .containsExactly(web, android);
    }

    @Test
    @DisplayName("confirmo hace 1 h -> solo el navegador: su alarma ya suena")
    void confirmoHaceUnaHora() {
        TokenPush web = token(PlataformaPush.WEB, null);
        TokenPush android = token(PlataformaPush.ANDROID, Duration.ofHours(1));
        TokenPush iphone = token(PlataformaPush.IOS, Duration.ofHours(25));

        assertThat(EntregaPush.RESPALDO_DE_ALARMA_LOCAL.filtrar(List.of(web, android, iphone), AHORA))
                .containsExactly(web);
    }

    @Test
    @DisplayName("confirmo hace 27 h -> vuelve a recibir el push (no abrio la app en mas de un dia)")
    void confirmoHace27Horas() {
        TokenPush web = token(PlataformaPush.WEB, null);
        TokenPush android = token(PlataformaPush.ANDROID, Duration.ofHours(27));

        assertThat(EntregaPush.RESPALDO_DE_ALARMA_LOCAL.filtrar(List.of(web, android), AHORA))
                .containsExactly(web, android);
    }

    @Test
    @DisplayName("un token sin plataforma (registros viejos) se trata como telefono")
    void sinPlataformaEsTelefono() {
        TokenPush viejo = token(null, null);
        TokenPush viejoConfirmado = token(null, Duration.ofMinutes(5));

        assertThat(EntregaPush.RESPALDO_DE_ALARMA_LOCAL.filtrar(List.of(viejo, viejoConfirmado), AHORA))
                .containsExactly(viejo);
    }

    @Test
    @DisplayName("un navegador confirmado igual recibe: no tiene alarma local")
    void navegadorSiempre() {
        TokenPush web = token(PlataformaPush.WEB, Duration.ofMinutes(1));

        assertThat(EntregaPush.RESPALDO_DE_ALARMA_LOCAL.filtrar(List.of(web), AHORA)).containsExactly(web);
    }

    @Test
    @DisplayName("los otros modos no cambian: TODOS, SOLO_NAVEGADOR, NINGUNO")
    void losOtrosModos() {
        TokenPush web = token(PlataformaPush.WEB, null);
        TokenPush android = token(PlataformaPush.ANDROID, null);
        List<TokenPush> todos = List.of(web, android);

        assertThat(EntregaPush.TODOS.filtrar(todos, AHORA)).containsExactly(web, android);
        assertThat(EntregaPush.SOLO_NAVEGADOR.filtrar(todos, AHORA)).containsExactly(web);
        assertThat(EntregaPush.NINGUNO.filtrar(todos, AHORA)).isEmpty();
    }
}
