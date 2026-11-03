package com.renaser.os.chat.domain.model.conversacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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
    /** Separa la formación del resto de una clave que no es la de la Escuela (spec 024). */
    private static final String SEPARADOR_DE_FORMACION = ":";

    private final ConversacionId id;
    private final TipoConversacion tipo;
    private final UUID celulaId;
    private final String claveDirecta;
    private final String nombre;
    private final Instant creadoEn;
    /**
     * La formación a la que pertenece la conversación (V138, spec 024): su comunidad general, el chat de su grupo, el soporte de su aprendiz o el directo
     * que se abrió en ella. {@code final}: una conversación no cambia de formación; quien cambia de formación deja de verla y la de la nueva es otra.
     */
    private final Formacion formacion;

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code ConversacionService}). Asi la
     * factoria es referencialmente transparente y un test puede fijar el id que espera
     * (CLAUDE.MD §5.4.7). Vale igual para {@link #crearDirecta} y {@link #crearGlobal}.
     */
    public static Conversacion crearCelula(ConversacionId id, UUID celulaId, Instant ahora) {
        return crearCelula(id, celulaId, ahora, Formacion.POR_DEFECTO);
    }

    /** El chat de un grupo es de la formación del grupo (spec 024). */
    public static Conversacion crearCelula(ConversacionId id, UUID celulaId, Instant ahora, Formacion formacion) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(formacion, "formacion es obligatoria");
        if (celulaId == null) {
            throw new IllegalArgumentException("celulaId es obligatorio para una conversacion de celula");
        }
        return new Conversacion(id, TipoConversacion.CELULA, celulaId, null, null, ahora, formacion);
    }

    public static Conversacion crearDirecta(ConversacionId id, String claveDirecta, Instant ahora) {
        return crearDirecta(id, claveDirecta, ahora, Formacion.POR_DEFECTO);
    }

    /** Un directo es de la formación en que se abrió (spec 024, FR-029). */
    public static Conversacion crearDirecta(ConversacionId id, String claveDirecta, Instant ahora, Formacion formacion) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(formacion, "formacion es obligatoria");
        if (claveDirecta == null || claveDirecta.isBlank()) {
            throw new IllegalArgumentException("claveDirecta es obligatoria para una conversacion directa");
        }
        return new Conversacion(id, TipoConversacion.DIRECTA, null, claveDirecta, null, ahora, formacion);
    }

    public static Conversacion crearGlobal(ConversacionId id, Instant ahora) {
        return crearGlobal(id, NombreDelChat.DE_LA_COMUNIDAD, ahora, Formacion.POR_DEFECTO);
    }

    /**
     * La comunidad general de {@code formacion} (spec 024, FR-026): una por formación. {@code nombre} lo arma quien llama
     * ({@link NombreDelChat#deLaComunidad}): la Escuela conserva el de siempre y las otras llevan el de su formación.
     */
    public static Conversacion crearGlobal(ConversacionId id, String nombre, Instant ahora, Formacion formacion) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(formacion, "formacion es obligatoria");
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la comunidad es obligatorio");
        }
        return new Conversacion(id, TipoConversacion.GLOBAL, null, null, nombre.strip(), ahora, formacion);
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
        return crearSoporte(id, aprendizId, nombre, ahora, Formacion.POR_DEFECTO);
    }

    /** El soporte de un aprendiz con el equipo de SU formación (spec 024, FR-028). */
    public static Conversacion crearSoporte(ConversacionId id, UserId aprendizId, String nombre, Instant ahora,
                                            Formacion formacion) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(formacion, "formacion es obligatoria");
        Objects.requireNonNull(aprendizId, "aprendizId es obligatorio para una conversacion de soporte");
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio para una conversacion de soporte");
        }
        return new Conversacion(id, TipoConversacion.SOPORTE, null, claveSoporteDe(aprendizId, formacion), nombre.strip(), ahora,
                formacion);
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
        return new Conversacion(id, tipo, celulaId, claveDirecta, nuevoNombre.strip(), creadoEn, formacion);
    }

    /** Solo para el adaptador de persistencia — valida la coherencia igual que las fabricas
     * de arriba, para que un dato corrupto en la base falle rapido, no en silencio. */
    public static Conversacion rehydrate(ConversacionId id, TipoConversacion tipo, UUID celulaId,
                                          String claveDirecta, String nombre, Instant creadoEn) {
        return rehydrate(id, tipo, celulaId, claveDirecta, nombre, creadoEn, Formacion.POR_DEFECTO);
    }

    /** Solo para el adaptador de persistencia, con la formación guardada en la fila. */
    public static Conversacion rehydrate(ConversacionId id, TipoConversacion tipo, UUID celulaId,
                                          String claveDirecta, String nombre, Instant creadoEn, Formacion formacion) {
        requireTipoCoherente(tipo, celulaId, claveDirecta);
        return new Conversacion(id, tipo, celulaId, claveDirecta, nombre, creadoEn, formacion);
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
     * La clave del directo de dos personas dentro de {@code formacion} (spec 024, FR-029). La de la Escuela es la de siempre —los directos que ya
     * existen la conservan—; la de las otras lleva el código al final, así que dos personas que comparten dos formaciones tienen un directo en cada una.
     */
    public static String claveDirectaDe(UserId a, UserId b, Formacion formacion) {
        String base = claveDirectaDe(a, b);
        return formacion == Formacion.POR_DEFECTO ? base : base + SEPARADOR_DE_FORMACION + formacion.codigo();
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
     * La clave del soporte de un aprendiz en {@code formacion} (spec 024, FR-028): la de la Escuela no cambia; la de las otras lleva el código
     * al final. Así quien pasa de formación tiene un soporte nuevo en la nueva y conserva (sin verlo) el de la anterior.
     */
    public static String claveSoporteDe(UserId aprendizId, Formacion formacion) {
        String base = claveSoporteDe(aprendizId);
        return formacion == Formacion.POR_DEFECTO ? base : base + SEPARADOR_DE_FORMACION + formacion.codigo();
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
                && claveSoporteDe(usuarioId, formacion).equals(claveDirecta);
    }

    /**
     * Si para {@code usuarioId} esta conversacion se gana por <b>ROL</b> y no por la fila de
     * {@code participantes_conversacion}: un SOPORTE del que no es el aprendiz dueño.
     *
     * <p>Existe para que la pregunta se escriba UNA vez y no cuatro. La regla de acceso del modulo
     * esta copiada en {@code MensajeService}, {@code ConversacionService}, {@code PresenciaService}
     * y {@code AutorizacionDeConversacionService}, y fue esa duplicacion la que dejo al SOPORTE en
     * la misma bolsa que un mensaje directo — donde la proyeccion alcanza, porque nadie pierde un
     * DM por cambiar de rol. Un soporte si: adentro van el aprendiz y los ADMIN/ALCHEMIST activos,
     * nadie mas (regla 1 de D-136). Quien da {@code true} aca tiene que seguir siendo staff AHORA,
     * no haberlo sido alguna vez.
     *
     * <p>Un DM, la GLOBAL y una CELULA dan {@code false}: a ninguno le aplica esta pregunta — la
     * CELULA tiene la suya propia, que es la pertenencia vigente al grupo.
     */
    public boolean seGanaPorRolDeStaff(UserId usuarioId) {
        return tipo == TipoConversacion.SOPORTE && !esAprendizDeSoporte(usuarioId);
    }

    /**
     * El aprendiz dueño de este soporte (D-205: la foto del chat es su tarjeta con su nombre), leído
     * de la clave {@code soporte:<uuid>}. Vacío si no es un soporte.
     *
     * <p>Acá sí hay que descomponer la clave —{@link #esAprendizDeSoporte} la arma hacia adelante
     * porque ya sabe a quién pregunta; esta no—, así que una clave con otra forma no lanza: da vacío.
     * Nadie la escribe distinto ({@link #crearSoporte} es el único camino), pero un dato raro en la
     * base no puede tumbar una lectura.
     */
    public Optional<UserId> aprendizDelSoporte() {
        if (tipo != TipoConversacion.SOPORTE || claveDirecta == null || !claveDirecta.startsWith(PREFIJO_SOPORTE)) {
            return Optional.empty();
        }
        try {
            String sinPrefijo = claveDirecta.substring(PREFIJO_SOPORTE.length());
            int separador = sinPrefijo.indexOf(SEPARADOR_DE_FORMACION);
            String uuid = separador < 0 ? sinPrefijo : sinPrefijo.substring(0, separador);
            return Optional.of(UserId.of(UUID.fromString(uuid)));
        } catch (IllegalArgumentException claveConOtraForma) {
            return Optional.empty();
        }
    }

    /** Si hay doble marca de leído en esta conversación (D-208): en todas menos la comunidad. */
    public boolean confirmaLectura() {
        return tipo.confirmaLectura();
    }

    @Override
    public String toString() {
        return "Conversacion[" + id + ", " + tipo + "]";
    }
}
