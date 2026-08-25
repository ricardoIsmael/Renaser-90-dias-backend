package com.renaser.os.notifications.domain.model.notificacion;

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
 * Una fila de la bandeja de la campana (tabla {@code notificaciones}, log de alto
 * volumen con PK bigint identity). Regla de negocio unica y completa: se emite
 * (creado) y se puede marcar leida una vez, de forma idempotente — no hay mas
 * estados ni flujo de borrado (mismo patron simple que {@code TicketMentor} de
 * `support`, ver docs/MODULO_SUPPORT.md).
 *
 * <p>{@code RETENCION_DIAS}/{@code LIMITE_BANDEJA} son las dos reglas de la
 * ventana de lectura del repo viejo (`notifications/schema.ts`): la bandeja
 * NUNCA es un archivo historico completo, es tope, no pagina.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Notificacion {

    public static final int RETENCION_DIAS = 90;
    public static final int LIMITE_BANDEJA = 100;

    private final Long id;
    private final UserId usuarioId;
    private final TipoNotificacion tipo;
    private final String titulo;
    private final String cuerpo;
    private final String rutaApp;
    private Instant leidaEn;
    private final Instant creadoEn;

    /** Emision de una notificacion nueva. {@code id} lo asigna Postgres (IDENTITY) al guardar. */
    public static Notificacion emitir(UserId usuarioId, TipoNotificacion tipo, String titulo, String cuerpo,
                                       String rutaApp, Clock clock) {
        return new Notificacion(null, Objects.requireNonNull(usuarioId, "usuarioId es obligatorio"),
                Objects.requireNonNull(tipo, "tipo es obligatorio"), requireNotBlank(titulo), requireNotBlank(cuerpo),
                rutaApp, null, clock.now());
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static Notificacion rehydrate(Long id, UserId usuarioId, TipoNotificacion tipo, String titulo,
                                          String cuerpo, String rutaApp, Instant leidaEn, Instant creadoEn) {
        return new Notificacion(id, usuarioId, tipo, titulo, cuerpo, rutaApp, leidaEn, creadoEn);
    }

    /** Idempotente: repetirlo sobre una ya leida no mueve {@code leidaEn} (mismo criterio que el repo viejo). */
    public void marcarLeida(Clock clock) {
        if (leidaEn == null) {
            leidaEn = clock.now();
        }
    }

    public boolean estaLeida() {
        return leidaEn != null;
    }

    public boolean perteneceA(UserId actorId) {
        return usuarioId.equals(actorId);
    }

    private static String requireNotBlank(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("titulo y cuerpo de la notificacion son obligatorios");
        }
        return value;
    }

    @Override
    public String toString() {
        return "Notificacion[id=" + id + ", usuario=" + usuarioId + ", tipo=" + tipo + "]";
    }
}
