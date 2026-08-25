package com.renaser.os.chat.domain.model.conversacion;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Un miembro de una conversacion (tabla `participantes_conversacion`, PK compuesta
 * `conversacion_id, usuario_id`). Parte del agregado `conversacion/` (PLAN_DE_MODULOS.md
 * linea 132: "conversacion/ (con Participante)") — no es un agregado propio, no tiene
 * sentido sin su conversacion.
 *
 * <p><b>Asuncion (a confirmar, ver docs/MODULO_CHAT.md):</b> al unirse, {@code ultimoLeidoEn}
 * arranca en el momento de la union (no null / no vacio). Sin esto, un usuario nuevo que
 * se auto-agrega a GLOBAL veria como "no leido" todo el historial previo a su alta —
 * potencialmente miles de mensajes. Decision de producto no confirmada por negocio.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"conversacionId", "usuarioId"})
public final class Participante {

    private final ConversacionId conversacionId;
    private final UserId usuarioId;
    private Instant ultimoLeidoEn;
    private final Instant creadoEn;

    public static Participante unirse(ConversacionId conversacionId, UserId usuarioId, Instant ahora) {
        Objects.requireNonNull(conversacionId, "conversacionId es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        return new Participante(conversacionId, usuarioId, ahora, ahora);
    }

    public static Participante rehydrate(ConversacionId conversacionId, UserId usuarioId, Instant ultimoLeidoEn,
                                          Instant creadoEn) {
        return new Participante(conversacionId, usuarioId, ultimoLeidoEn, creadoEn);
    }

    public void marcarLeido(Instant ahora) {
        this.ultimoLeidoEn = Objects.requireNonNull(ahora, "ahora es obligatorio");
    }

    @Override
    public String toString() {
        return "Participante[" + conversacionId + ", " + usuarioId + "]";
    }
}
