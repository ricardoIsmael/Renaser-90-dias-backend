package com.renaser.os.notifications.domain.model.tokenpush;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Suscripción de push de un dispositivo (Expo para móvil o Web Push para navegador; tabla
 * {@code tokens_push}). {@code token} es
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

    /**
     * Cuanto vale una confirmacion de alarmas locales (D-217): un dia mas 2 h de margen. La app
     * confirma cada vez que abre y rearma sus alarmas; quien la abre todos los dias, pero hoy a las
     * 09:00 y ayer a las 08:00, no puede quedar "sin confirmar" por una hora de diferencia, y quien
     * no la abrio en todo un dia ya no puede asegurar que el sistema no le borro la alarma
     * (detencion forzada, ahorro de bateria).
     */
    public static final Duration VIGENCIA_CONFIRMACION_ALARMAS = Duration.ofHours(26);

    private final TokenPushId id;
    private UserId usuarioId;
    private final String token;
    private PlataformaPush plataforma;
    private final Instant creadoEn;
    private Instant actualizadoEn;
    /**
     * Ultima vez que la app de ESTE dispositivo avisó que tiene vivas sus alarmas locales de habitos
     * ({@code POST /api/v1/push-tokens/alarmas-locales}, D-217). {@code null} = nunca confirmó: el APK
     * anterior a D-217, el navegador, o un token recien registrado.
     */
    private Instant alarmasConfirmadasEn;

    public static TokenPush registrar(TokenPushId id, UserId usuarioId, String token, PlataformaPush plataforma,
                                       Clock clock) {
        Instant ahora = clock.now();
        return new TokenPush(Objects.requireNonNull(id, "id es obligatorio"),
                Objects.requireNonNull(usuarioId, "usuarioId es obligatorio"),
                requireNotBlank(token), plataforma, ahora, ahora, null);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static TokenPush rehydrate(TokenPushId id, UserId usuarioId, String token, PlataformaPush plataforma,
                                       Instant creadoEn, Instant actualizadoEn) {
        return rehydrate(id, usuarioId, token, plataforma, creadoEn, actualizadoEn, null);
    }

    /** Igual que el de arriba, con la ultima confirmacion de alarmas locales (D-217). */
    public static TokenPush rehydrate(TokenPushId id, UserId usuarioId, String token, PlataformaPush plataforma,
                                       Instant creadoEn, Instant actualizadoEn, Instant alarmasConfirmadasEn) {
        return new TokenPush(id, usuarioId, token, plataforma, creadoEn, actualizadoEn, alarmasConfirmadasEn);
    }

    /** La app de este dispositivo acaba de rearmar sus alarmas locales y lo avisa (D-217). */
    public void confirmarAlarmasLocales(Clock clock) {
        this.alarmasConfirmadasEn = clock.now();
    }

    /**
     * {@code true} si este dispositivo confirmó sus alarmas locales hace menos de
     * {@link #VIGENCIA_CONFIRMACION_ALARMAS}. Es lo que decide si el aviso de inicio de un habito con
     * recordatorio le llega tambien por push (D-217): sin confirmacion vigente, el servidor no sabe si la
     * alarma sigue viva y empuja igual.
     */
    public boolean tieneAlarmasLocalesVigentes(Instant ahora) {
        return alarmasConfirmadasEn != null
                && alarmasConfirmadasEn.plus(VIGENCIA_CONFIRMACION_ALARMAS).isAfter(ahora);
    }

    /** Re-vincula este token (ya existente) a otro dueno/plataforma — mismo UPDATE del UPSERT viejo. */
    public void reasignar(UserId usuarioId, PlataformaPush plataforma, Clock clock) {
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        if (!this.usuarioId.equals(usuarioId)) {
            // Las alarmas que confirmo el dueno anterior no son las de este (D-217).
            this.alarmasConfirmadasEn = null;
        }
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
