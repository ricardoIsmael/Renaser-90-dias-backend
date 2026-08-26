package com.renaser.os.rag.domain.model.conversacion;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * La conversacion de Renasia de un aprendiz (tabla `conversaciones_renasia`). 1:1 REAL con
 * el usuario: {@code usuario_id} es a la vez PK y FK — un aprendiz tiene UNA sola
 * conversacion con Renasia, nunca una lista (docs/MODULO_RAG.md §2). Por eso no existe una
 * {@code ConversacionRenasiaId} propia: la identidad de este agregado ES el {@link UserId}
 * del aprendiz.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "usuarioId")
public final class ConversacionRenasia {

    private final UserId usuarioId;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    public static ConversacionRenasia iniciar(UserId usuarioId, Instant ahora) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("usuarioId es obligatorio para iniciar una conversacion de Renasia");
        }
        return new ConversacionRenasia(usuarioId, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static ConversacionRenasia rehydrate(UserId usuarioId, Instant creadoEn, Instant actualizadoEn) {
        return new ConversacionRenasia(usuarioId, creadoEn, actualizadoEn);
    }

    /** Nueva instancia con {@code actualizadoEn} refrescado — la conversacion es inmutable,
     * "tocarla" devuelve un valor nuevo (CLAUDE.MD sec. 5.4.7). */
    public ConversacionRenasia tocar(Instant ahora) {
        return new ConversacionRenasia(usuarioId, creadoEn, ahora);
    }

    @Override
    public String toString() {
        return "ConversacionRenasia[" + usuarioId + "]";
    }
}
