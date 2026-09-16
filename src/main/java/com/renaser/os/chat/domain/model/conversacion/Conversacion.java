package com.renaser.os.chat.domain.model.conversacion;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Una conversacion de chat (tabla `conversaciones`). Replica en dominio el CHECK
 * `tipo_coherente` de la base (V1__baseline_renaser.sql:1286-1290, ampliado por
 * V54 con la rama SOPORTE) ANTES de llegar a Postgres, para que un dato invalido
 * explote como 400 (dominio), no como 500 (violacion de CHECK) — CLAUDE.MD sec. 5.4.4.
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

    /** Ver {@link #claveSoporteDe}. Constante y no un literal suelto: es formato de datos
     * persistidos, y cambiarlo dejaria huerfanas las conversaciones ya creadas. */
    private static final String PREFIJO_SOPORTE = "soporte:";

    private final ConversacionId id;
    private final TipoConversacion tipo;
    private final UUID celulaId;
    private final String claveDirecta;
    private final String nombre;
    private final Instant creadoEn;

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code ConversacionService}). Asi la
     * factoria es referencialmente transparente y un test puede fijar el id que espera
     * (CLAUDE.MD §5.4.7). Vale igual para {@link #crearDirecta} y {@link #crearGlobal}.
     */
    public static Conversacion crearCelula(ConversacionId id, UUID celulaId, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        if (celulaId == null) {
            throw new IllegalArgumentException("celulaId es obligatorio para una conversacion de celula");
        }
        return new Conversacion(id, TipoConversacion.CELULA, celulaId, null, null, ahora);
    }

    public static Conversacion crearDirecta(ConversacionId id, String claveDirecta, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        if (claveDirecta == null || claveDirecta.isBlank()) {
            throw new IllegalArgumentException("claveDirecta es obligatoria para una conversacion directa");
        }
        return new Conversacion(id, TipoConversacion.DIRECTA, null, claveDirecta, null, ahora);
    }

    public static Conversacion crearGlobal(ConversacionId id, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        return new Conversacion(id, TipoConversacion.GLOBAL, null, null, "Global", ahora);
    }

    /**
     * El chat de soporte de un aprendiz (D-136). La identidad de negocio es el aprendiz, y por
     * eso se guarda en {@code claveDirecta}: esa columna ya trae el UNIQUE
     * ({@code conversaciones_clave_directa_key}) que impide una segunda conversacion de soporte
     * para la misma persona. La unicidad la garantiza la base, no un chequeo del servicio que
     * dos peticiones simultaneas podrian pasar las dos.
     *
     * <p>El {@code nombre} entra por parametro y no se arma aca porque el dominio de `chat` no
     * conoce el nombre de nadie: quien lo resuelve es el caso de uso, contra `users.api`.
     */
    public static Conversacion crearSoporte(ConversacionId id, UserId aprendizId, String nombre, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(aprendizId, "aprendizId es obligatorio para una conversacion de soporte");
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio para una conversacion de soporte");
        }
        return new Conversacion(id, TipoConversacion.SOPORTE, null, claveSoporteDe(aprendizId), nombre.strip(), ahora);
    }

    /**
     * Devuelve una instancia nueva con {@code nombre} cambiado (CLAUDE.MD sec. 5.4.7:
     * "cambiar" = instancia nueva, nunca mutar). Solo GLOBAL tiene sentido renombrar —
     * es la unica de las tres que llega con un {@code nombre} propio (ver
     * {@link #crearCelula} / {@link #crearDirecta}, que lo dejan en {@code null}: su
     * titulo en pantalla sale de otro lado — la celula de `community`, el otro
     * participante del DM).
     */
    public Conversacion renombrada(String nuevoNombre) {
        if (tipo != TipoConversacion.GLOBAL) {
            throw new IllegalStateException("Solo la conversacion GLOBAL se puede renombrar");
        }
        if (nuevoNombre == null || nuevoNombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacio");
        }
        return new Conversacion(id, tipo, celulaId, claveDirecta, nuevoNombre.strip(), creadoEn);
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
            case SOPORTE -> claveDirecta != null && celulaId == null;
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

    /**
     * Clave canonica del chat de soporte de un aprendiz: {@code soporte:<uuid del aprendiz>}.
     *
     * <p>Comparte columna con {@link #claveDirectaDe} y no colisiona con ella: una clave de DM es
     * {@code <uuid>_<uuid>} y nunca empieza con este prefijo. Compartirla es lo que hace que el
     * UNIQUE ya existente sirva de candado, sin columna nueva.
     */
    public static String claveSoporteDe(UserId aprendizId) {
        return PREFIJO_SOPORTE + aprendizId.value();
    }

    /**
     * Si {@code usuarioId} es el aprendiz dueño de este soporte — el unico que NO se puede ir
     * (regla del dueño del proyecto: el staff sale si quiere, el aprendiz no).
     *
     * <p>Compara hacia adelante (arma la clave y la contrasta) en vez de descomponer la que hay
     * guardada: asi no existe el caso "clave con formato raro" ni un {@code UUID.fromString} que
     * pueda lanzar.
     */
    public boolean esAprendizDeSoporte(UserId usuarioId) {
        return tipo == TipoConversacion.SOPORTE && usuarioId != null
                && claveSoporteDe(usuarioId).equals(claveDirecta);
    }

    @Override
    public String toString() {
        return "Conversacion[" + id + ", " + tipo + "]";
    }
}
