package com.renaser.os.academy.domain.model.curso;

import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Raiz del agregado `curso`: un curso importado de Skool, con su gate de
 * catalogo (rol + dia de programa + publicacion). Contiene tambien
 * {@link SeccionCurso}, {@link Leccion} y {@link RecursoLeccion} — no tienen
 * vida propia sin el curso al que pertenecen (CLAUDE.MD sec. 5.1.2, regla de
 * agregado).
 *
 * <p>La regla de visibilidad es una copia fiel de
 * {@code puedeVerCurso}/RenaserBack {@code src/features/cursos/repository.ts:731-747}, verificada
 * contra sus tests unitarios ({@code __tests__/puedeVerCurso.test.ts}). Ver
 * `docs/MODULO_ACADEMY.md` §1 para el detalle de la migracion, incluida la
 * decision AC-01 (asignaciones NO alteran esta regla) y AC-02 (RESTRINGIDO
 * nunca es visible por catalogo hoy, con su pregunta abierta).
 */
public final class Curso {

    /** Espejo de `PROGRAM_DAY_INICIAL` (repository.ts:717): sin fila de participante todavia = dia 0. */
    public static final int DIA_PROGRAMA_INICIAL = 0;

    private final CursoId id;
    private final String slug;
    private final String titulo;
    private final String descripcion;
    private final String portadaRuta;
    private final int orden;
    private final boolean publicado;
    private final AccesoCurso acceso;
    private final String origen;
    private final Integer diaDesbloqueo;
    private final Set<UserRole> rolesPermitidos;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    public Curso(CursoId id, String slug, String titulo, String descripcion, String portadaRuta, int orden,
                 boolean publicado, AccesoCurso acceso, String origen, Integer diaDesbloqueo,
                 Set<UserRole> rolesPermitidos, Instant creadoEn, Instant actualizadoEn) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.slug = Objects.requireNonNull(slug, "slug es obligatorio");
        this.titulo = Objects.requireNonNull(titulo, "titulo es obligatorio");
        this.descripcion = descripcion;
        this.portadaRuta = portadaRuta;
        this.orden = orden;
        this.publicado = publicado;
        this.acceso = Objects.requireNonNull(acceso, "acceso es obligatorio");
        this.origen = Objects.requireNonNull(origen, "origen es obligatorio");
        this.diaDesbloqueo = diaDesbloqueo;
        this.rolesPermitidos = rolesPermitidos == null ? Set.of() : Set.copyOf(rolesPermitidos);
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn es obligatorio");
    }

    /**
     * Gate del catalogo publicado. Las asignaciones (`asignaciones_curso`) NO
     * intervienen aca — decision AC-01, fiel al comportamiento real de
     * RenaserBack (el parametro `_asignadoHistoricamente` del repo viejo ya
     * no altera el resultado, ver comentario en repository.ts:735-741).
     */
    public boolean visibleEnCatalogoPara(UserRole rol, Integer diaProgramaParticipante) {
        Objects.requireNonNull(rol, "rol es obligatorio");
        if (!rolesPermitidos.isEmpty() && !rolesPermitidos.contains(rol)) {
            return false;
        }
        if (diaDesbloqueo != null && rol == UserRole.TRAINEE) {
            int diaActual = diaProgramaParticipante == null ? DIA_PROGRAMA_INICIAL : diaProgramaParticipante;
            if (diaActual < diaDesbloqueo) {
                return false;
            }
        }
        return publicado && acceso == AccesoCurso.ABIERTO;
    }

    /**
     * True si este curso es exactamente el caso que `catalogo_cursos_bloqueados`
     * (RPC del repo viejo, 0018 — nunca existio como REST, ver
     * `docs/MODULO_ACADEMY.md` §1/§5, decision AC-15) pintaba con candado: un
     * curso que el actor va a ver mas adelante, SOLO porque todavia no llega
     * al dia de desbloqueo. Es la inversa exacta de {@link #visibleEnCatalogoPara}
     * pero nunca revela ningun otro motivo (rol, borrador, restringido) — mismo
     * criterio de "no revelar de mas" que {@code motivo()}/{@code MotivoBloqueoCurso}.
     */
    public boolean bloqueadoPorDiaPara(UserRole rol, Integer diaProgramaParticipante) {
        Objects.requireNonNull(rol, "rol es obligatorio");
        if (diaDesbloqueo == null || rol != UserRole.TRAINEE) {
            return false;
        }
        if (!rolesPermitidos.isEmpty() && !rolesPermitidos.contains(rol)) {
            return false;
        }
        if (!publicado || acceso != AccesoCurso.ABIERTO) {
            return false;
        }
        int diaActual = diaProgramaParticipante == null ? DIA_PROGRAMA_INICIAL : diaProgramaParticipante;
        return diaActual < diaDesbloqueo;
    }

    public CursoId id() {
        return id;
    }

    public String slug() {
        return slug;
    }

    public String titulo() {
        return titulo;
    }

    public String descripcion() {
        return descripcion;
    }

    public String portadaRuta() {
        return portadaRuta;
    }

    public int orden() {
        return orden;
    }

    public boolean publicado() {
        return publicado;
    }

    public AccesoCurso acceso() {
        return acceso;
    }

    public String origen() {
        return origen;
    }

    public Integer diaDesbloqueo() {
        return diaDesbloqueo;
    }

    public Set<UserRole> rolesPermitidos() {
        return rolesPermitidos;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    public Instant actualizadoEn() {
        return actualizadoEn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Curso curso)) {
            return false;
        }
        return id.equals(curso.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Curso[" + id + ", " + titulo + "]";
    }
}
