package com.renaser.os.notifications.domain.model.tokenpush;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Token de push (Expo) de un dispositivo (tabla {@code tokens_push}). {@code token} es
 * globalmente UNICO en el esquema (V1__baseline_renaser.sql:1345) — un mismo token
 * registrado de nuevo (reinstalacion, cambio de usuario en el mismo dispositivo)
 * REEMPLAZA el dueno anterior, nunca duplica fila. Espejo 1:1 de
 * {@code chat/repository.ts:upsertPushToken} del repo viejo.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class TokenPush {

    private final TokenPushId id;
    private UserId usuarioId;
    private final String token;
    private PlataformaPush plataforma;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static TokenPush registrar(TokenPushId id, UserId usuarioId, String token, PlataformaPush plataforma,
                                       Clock clock) {
        Instant ahora = clock.now();
        return new TokenPush(Objects.requireNonNull(id, "id es obligatorio"),
                Objects.requireNonNull(usuarioId, "usuarioId es obligatorio"),
                requireNotBlank(token), plataforma, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static TokenPush rehydrate(TokenPushId id, UserId usuarioId, String token, PlataformaPush plataforma,
                                       Instant creadoEn, Instant actualizadoEn) {
        return new TokenPush(id, usuarioId, token, plataforma, creadoEn, actualizadoEn);
    }

    /** Re-vincula este token (ya existente) a otro dueno/plataforma — mismo UPDATE del UPSERT viejo. */
    public void reasignar(UserId usuarioId, PlataformaPush plataforma, Clock clock) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        this.plataforma = plataforma;
        this.actualizadoEn = clock.now();
    }

    private static String requireNotBlank(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("token no puede ser vacio");
        }
        return value;
    }

    @Override
    public String toString() {
        return "TokenPush[id=" + id + ", usuario=" + usuarioId + "]";
    }
}
