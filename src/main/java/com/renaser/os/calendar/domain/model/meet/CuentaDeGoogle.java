package com.renaser.os.calendar.domain.model.meet;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * La cuenta de Google que organiza todas las salas de Meet de Renaser (spec 040, FR-010): UNA sola para toda la plataforma, conectada por
 * OAuth. Guarda su permiso permanente (refresh token), que vive cifrado en la base y en claro solo en memoria; el correo y la contraseña del
 * Gmail nunca pasan por Renaser.
 *
 * <p>{@code calendarioId} es el calendario donde se crean las salas (FR-034): el propio «Renaser 90 días» que crea nuestra app, o {@code primary}
 * si el permiso concedido no deja crear uno.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class CuentaDeGoogle {

    public static final String PERMISO_CALENDARIO_PROPIO = "https://www.googleapis.com/auth/calendar.app.created";
    public static final String PERMISO_EVENTOS = "https://www.googleapis.com/auth/calendar.events";
    public static final String PERMISO_AJUSTES_DE_LA_SALA = "https://www.googleapis.com/auth/meetings.space.settings";
    public static final String CALENDARIO_PRINCIPAL = "primary";

    private final String correo;
    private final String refreshToken;
    private final Set<String> permisos;
    private String calendarioId;
    private EstadoDeLaCuenta estado;
    private final UserId conectadaPor;
    private final Instant conectadaEn;
    private Instant ultimoUsoEn;
    private String ultimoError;
    private Instant ultimoAvisoEn;
    private long version;

    public static CuentaDeGoogle conectar(String correo, String refreshToken, Set<String> permisos, UserId conectadaPor,
                                          Instant ahora) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("Google no dijo de qué cuenta es el permiso.");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Google no dio el permiso permanente. Vuelve a conectar y acepta todo lo que pide.");
        }
        return new CuentaDeGoogle(correo.trim(), refreshToken, Set.copyOf(permisos), null, EstadoDeLaCuenta.ACTIVA,
                Objects.requireNonNull(conectadaPor, "conectadaPor es obligatorio"), ahora, null, null, null, 0L);
    }

    /** Solo para el adaptador de persistencia. */
    public static CuentaDeGoogle rehydrate(String correo, String refreshToken, Set<String> permisos, String calendarioId,
                                           EstadoDeLaCuenta estado, UserId conectadaPor, Instant conectadaEn,
                                           Instant ultimoUsoEn, String ultimoError, Instant ultimoAvisoEn, long version) {
        return new CuentaDeGoogle(correo, refreshToken, Set.copyOf(permisos), calendarioId, estado, conectadaPor, conectadaEn,
                ultimoUsoEn, ultimoError, ultimoAvisoEn, version);
    }

    public boolean activa() {
        return estado == EstadoDeLaCuenta.ACTIVA;
    }

    public boolean tienePermiso(String permiso) {
        return permisos.contains(permiso);
    }

    /** Dónde caen las salas: el calendario propio si se creó, el principal si no. */
    public String calendarioParaLasSalas() {
        return calendarioId == null ? CALENDARIO_PRINCIPAL : calendarioId;
    }

    public void fijarCalendario(String id) {
        this.calendarioId = Objects.requireNonNull(id, "id es obligatorio");
    }

    /** Google dijo que el permiso ya no vale: no se reintenta hasta que alguien vuelva a conectar. */
    public void revocar(String motivo, Instant ahora) {
        this.estado = EstadoDeLaCuenta.REVOCADA;
        this.ultimoError = motivo;
        this.ultimoUsoEn = ahora;
    }

    public void registrarUso(Instant ahora) {
        this.ultimoUsoEn = ahora;
        if (estado == EstadoDeLaCuenta.ACTIVA) {
            this.ultimoError = null;
        }
    }

    public void registrarError(String motivo, Instant ahora) {
        this.ultimoError = motivo;
        this.ultimoUsoEn = ahora;
    }

    public void avisadaEl(Instant ahora) {
        this.ultimoAvisoEn = ahora;
    }

    @Override
    public String toString() {
        return "CuentaDeGoogle[" + correo + ", " + estado + "]";
    }
}
