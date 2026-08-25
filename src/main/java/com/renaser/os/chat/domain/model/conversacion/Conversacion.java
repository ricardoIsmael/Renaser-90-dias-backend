package com.renaser.os.chat.domain.model.conversacion;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

/**
 * Una conversacion de chat (tabla `conversaciones`). Replica en dominio el CHECK
 * `tipo_coherente` de la base (V1__baseline_renaser.sql:1286-1290) ANTES de llegar a
 * Postgres, para que un dato invalido explote como 400 (dominio), no como 500
 * (violacion de CHECK) — CLAUDE.MD sec. 5.4.4.
 *
 * <p>`celulaId` viaja como UUID plano: es el id de una `Celula` de `community`, modulo que
 * `chat` no puede importar directamente (CLAUDE.MD sec. 5.1 — solo la API publica de otro
 * modulo es visible, y `community.api` no expone `CelulaId`).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Conversacion {

    private final ConversacionId id;
    private final TipoConversacion tipo;
    private final UUID celulaId;
    private final String claveDirecta;
    private final String nombre;
    private final Instant creadoEn;

    public static Conversacion crearCelula(UUID celulaId, Instant ahora) {
        if (celulaId == null) {
            throw new IllegalArgumentException("celulaId es obligatorio para una conversacion de celula");
        }
        return new Conversacion(ConversacionId.newId(), TipoConversacion.CELULA, celulaId, null, null, ahora);
    }

    public static Conversacion crearDirecta(String claveDirecta, Instant ahora) {
        if (claveDirecta == null || claveDirecta.isBlank()) {
            throw new IllegalArgumentException("claveDirecta es obligatoria para una conversacion directa");
        }
        return new Conversacion(ConversacionId.newId(), TipoConversacion.DIRECTA, null, claveDirecta, null, ahora);
    }

    public static Conversacion crearGlobal(Instant ahora) {
        return new Conversacion(ConversacionId.newId(), TipoConversacion.GLOBAL, null, null, "Global", ahora);
    }

    /** Solo para el adaptador de persistencia — valida la coherencia igual que las fabricas
     * de arriba, para que un dato corrupto en la base falle rapido, no en silencio. */
    public static Conversacion rehydrate(ConversacionId id, TipoConversacion tipo, UUID celulaId,
                                          String claveDirecta, String nombre, Instant creadoEn) {
        requireTipoCoherente(tipo, celulaId, claveDirecta);
        return new Conversacion(id, tipo, celulaId, claveDirecta, nombre, creadoEn);
    }

    private static void requireTipoCoherente(TipoConversacion tipo, UUID celulaId, String claveDirecta) {
        boolean coherente = switch (tipo) {
            case CELULA -> celulaId != null && claveDirecta == null;
            case DIRECTA -> claveDirecta != null && celulaId == null;
            case GLOBAL -> celulaId == null && claveDirecta == null;
        };
        if (!coherente) {
            throw new IllegalArgumentException("Conversacion inconsistente: tipo=" + tipo + " celulaId=" + celulaId
                    + " claveDirecta=" + claveDirecta);
        }
    }

    /**
     * Clave canonica de una conversacion directa entre dos usuarios: orden lexicografico
     * `menor_mayor` de sus UUID, para que da igual quien la busque primero (a-b y b-a
     * resuelven la misma fila, `conversaciones.clave_directa UNIQUE`).
     */
    public static String claveDirectaDe(UserId a, UserId b) {
        String sa = a.value().toString();
        String sb = b.value().toString();
        return sa.compareTo(sb) <= 0 ? sa + "_" + sb : sb + "_" + sa;
    }

    @Override
    public String toString() {
        return "Conversacion[" + id + ", " + tipo + "]";
    }
}
