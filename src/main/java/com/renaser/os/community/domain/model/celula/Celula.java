package com.renaser.os.community.domain.model.celula;

import com.renaser.os.community.domain.model.acompanamiento.CupoCelula;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Grupo de aprendices con un mentor (tabla `celulas`). `mentorId` es el `usuario_id` de un
 * `perfiles_mentor` — tabla que NO pertenece a este modulo (es un perfil de `users`,
 * CLAUDE.MD sec. 5.3.2), asi que aca viaja como {@link UserId} plano: `community` guarda el
 * UUID, nunca importa el agregado de otro modulo (CLAUDE.MD sec. 5.1: "un modulo solo puede
 * llamar a la API publica de otro").
 *
 * <p>Un mentor lidera a lo sumo una celula (`celulas.mentor_id UNIQUE`,
 * V1__baseline_renaser.sql:245) — la unicidad la sostiene la base; el caso de uso rechaza
 * antes de llegar ahi (community/service.ts:389-393).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Celula {

    private final CelulaId id;
    private String nombre;
    private UserId mentorId;
    private final CohorteId cohorteId;
    private String urlVideollamada;
    private Instant proximaSesionEn;
    private final Instant creadoEn;
    private Instant actualizadoEn;
    /** RECEPCION o REGULAR (V45). Recepcion no tiene tope comercial y puede no tener mentor. */
    private TipoCelula tipo;
    /** Override de capacidad de esta celula. {@code null} = usar la de la politica de su cohorte. */
    private Integer capacidadMaxima;
    /**
     * V48. Desde cuando y hasta cuando vive el grupo que armo el administrador.
     *
     * <p>{@code null} = grupo SIN periodo, que no caduca. No es un caso raro ni un estado a
     * medias: es lo que son todas las celulas anteriores a V48, y la migracion las dejo asi a
     * proposito para no ponerle fecha de muerte a grupos que nadie programo. Por eso los metodos
     * de consulta de abajo tienen que responder para ese caso, y no pueden delegar a ciegas.
     */
    private PeriodoGrupo periodo;

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code CelulaService.crear}). Asi la
     * factoria es referencialmente transparente y un test puede fijar el id que espera, en
     * vez de tener que caer a {@link #rehydrate} para lograrlo.
     */
    public static Celula crear(CelulaId id, String nombre, CohorteId cohorteId, String urlVideollamada,
                                Instant ahora) {
        return crear(id, nombre, cohorteId, urlVideollamada, null, ahora);
    }

    /** V48: la misma alta, con el periodo que el administrador escribio. {@code periodo} nulo = sin periodo. */
    public static Celula crear(CelulaId id, String nombre, CohorteId cohorteId, String urlVideollamada,
                                PeriodoGrupo periodo, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        requireNombreValido(nombre);
        Objects.requireNonNull(cohorteId, "cohorteId es obligatorio");
        return new Celula(id, nombre, null, cohorteId, urlVideollamada, null, ahora, ahora,
                TipoCelula.REGULAR, null, periodo);
    }

    /**
     * Solo para el adaptador de persistencia. Sobrecarga previa a V45: asume REGULAR sin
     * override. Se conserva para no obligar a tocar los llamadores que no saben de tipos.
     */
    public static Celula rehydrate(CelulaId id, String nombre, UserId mentorId, CohorteId cohorteId,
                                    String urlVideollamada, Instant proximaSesionEn, Instant creadoEn,
                                    Instant actualizadoEn) {
        return rehydrate(id, nombre, mentorId, cohorteId, urlVideollamada, proximaSesionEn, creadoEn,
                actualizadoEn, TipoCelula.REGULAR, null);
    }

    /** Solo para el adaptador de persistencia. Sobrecarga previa a V48: grupo sin periodo. */
    public static Celula rehydrate(CelulaId id, String nombre, UserId mentorId, CohorteId cohorteId,
                                    String urlVideollamada, Instant proximaSesionEn, Instant creadoEn,
                                    Instant actualizadoEn, TipoCelula tipo, Integer capacidadMaxima) {
        return rehydrate(id, nombre, mentorId, cohorteId, urlVideollamada, proximaSesionEn, creadoEn,
                actualizadoEn, tipo, capacidadMaxima, null);
    }

    /** Solo para el adaptador de persistencia. */
    public static Celula rehydrate(CelulaId id, String nombre, UserId mentorId, CohorteId cohorteId,
                                    String urlVideollamada, Instant proximaSesionEn, Instant creadoEn,
                                    Instant actualizadoEn, TipoCelula tipo, Integer capacidadMaxima,
                                    PeriodoGrupo periodo) {
        return new Celula(id, nombre, mentorId, cohorteId, urlVideollamada, proximaSesionEn, creadoEn,
                actualizadoEn, tipo != null ? tipo : TipoCelula.REGULAR, capacidadMaxima, periodo);
    }

    /**
     * El periodo que sale de las dos fechas sueltas de un comando o de una fila: <b>las dos o
     * ninguna</b>.
     *
     * <p>Una sola fecha no es medio periodo, es un error de quien lo mando: sin inicio no se sabe
     * desde cuando cuenta y sin fin no se sabe cuando cierra, y guardarlo asi deja al grupo en un
     * limbo que ninguna consulta resuelve. Es la misma regla que el CHECK
     * {@code celulas_periodo_completo_o_ausente} (V48) sostiene desde la base; aca se rechaza
     * antes, para que el error salga con nombre y no como violacion de constraint.
     */
    public static PeriodoGrupo periodoDe(LocalDate inicio, LocalDate fin) {
        if (inicio == null && fin == null) {
            return null;
        }
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException(
                    "El periodo del grupo va con las dos fechas o con ninguna: inicio=" + inicio + ", fin=" + fin);
        }
        return new PeriodoGrupo(inicio, fin);
    }

    public boolean esRecepcion() {
        return tipo == TipoCelula.RECEPCION;
    }

    public boolean tienePeriodo() {
        return periodo != null;
    }

    /**
     * Si el grupo ya cerro ese dia. <b>Un grupo sin periodo NUNCA esta vencido</b> — de otro modo
     * esta migracion apagaria de golpe todas las celulas anteriores a V48, que no tienen fechas.
     *
     * <p>El {@code dia} viene por parametro y no de {@code LocalDate.now()}: el dia tiene que ser
     * el de quien mira, en su zona. A las 02:00 UTC del dia 1, en Lima todavia es el ultimo dia
     * del mes anterior y el grupo sigue vivo (E-91, regla 02).
     */
    public boolean vencidoEn(LocalDate dia) {
        return periodo != null && periodo.vencidoEn(dia);
    }

    /** Si todavia no arranco. Un grupo sin periodo tampoco es futuro: ya esta corriendo. */
    public boolean futuroEn(LocalDate dia) {
        return periodo != null && periodo.futuroEn(dia);
    }

    /** Si ese dia el grupo se ve desde la app del alumno. Sin periodo, siempre. */
    public boolean vigenteEn(LocalDate dia) {
        return periodo == null || periodo.contiene(dia);
    }

    /**
     * Los cuatro estados en una sola respuesta, para que la pantalla no tenga que combinar tres
     * booleanos y equivocarse en la unica combinacion que importa: sin periodo NO es vigente por
     * casualidad, lo es porque no caduca.
     */
    public EstadoGrupo estadoEn(LocalDate dia) {
        if (periodo == null) {
            return EstadoGrupo.SIN_PERIODO;
        }
        if (periodo.futuroEn(dia)) {
            return EstadoGrupo.PROGRAMADO;
        }
        return periodo.vencidoEn(dia) ? EstadoGrupo.CERRADO : EstadoGrupo.VIGENTE;
    }

    /**
     * Cambia el tope de aprendices. {@code null} devuelve el grupo a la capacidad de la politica
     * de su cohorte en vez de dejarlo sin tope: un grupo regular siempre tiene uno.
     *
     * <p>Bajar la capacidad por debajo de la ocupacion actual NO expulsa a nadie —eso lo resuelve
     * {@link CupoCelula#excedente}: bloquea altas hasta que el grupo vuelva bajo el limite.
     */
    public void cambiarCapacidad(Integer capacidadMaxima, Instant ahora) {
        if (capacidadMaxima != null) {
            // Valida el rango 10-15 en el mismo lugar que lo valida el cupo, no en un if suelto.
            CupoCelula.regular(capacidadMaxima);
        }
        this.capacidadMaxima = capacidadMaxima;
        this.actualizadoEn = ahora;
    }

    /**
     * RECEPCION o REGULAR. Se cambia al crear, no despues: convertir un grupo estable en
     * recepcion a mitad de camino le quitaria el tope con gente adentro.
     */
    public void marcarComo(TipoCelula tipo, Instant ahora) {
        this.tipo = Objects.requireNonNull(tipo, "tipo es obligatorio");
        this.actualizadoEn = ahora;
    }

    /**
     * El cupo efectivo. La recepcion no tiene tope (D-05); un grupo regular usa su override
     * y, si no lo tiene, el de la politica de su cohorte.
     */
    public CupoCelula cupo(int capacidadDeLaPolitica) {
        return esRecepcion()
                ? CupoCelula.recepcion()
                : CupoCelula.regular(capacidadMaxima != null ? capacidadMaxima : capacidadDeLaPolitica);
    }

    public void actualizarDatos(String nombre, String urlVideollamada, boolean tocaUrl, Instant ahora) {
        actualizarDatos(nombre, urlVideollamada, tocaUrl, null, false, ahora);
    }

    /**
     * V48. {@code tocaPeriodo} distingue "no vino" de "vino null para borrarlo", igual que
     * {@code tocaUrl}. Sin esa distincion, un PATCH que solo cambia el nombre le borraria el
     * periodo al grupo sin que nadie lo pidiera.
     */
    public void actualizarDatos(String nombre, String urlVideollamada, boolean tocaUrl, PeriodoGrupo periodo,
                                 boolean tocaPeriodo, Instant ahora) {
        String nombreEfectivo = nombre != null ? nombre : this.nombre;
        requireNombreValido(nombreEfectivo);
        this.nombre = nombreEfectivo;
        if (tocaUrl) {
            this.urlVideollamada = urlVideollamada;
        }
        if (tocaPeriodo) {
            this.periodo = periodo;
        }
        this.actualizadoEn = ahora;
    }

    public void asignarMentor(UserId mentorId, Instant ahora) {
        this.mentorId = Objects.requireNonNull(mentorId, "mentorId es obligatorio");
        this.actualizadoEn = ahora;
    }

    public void quitarMentor(Instant ahora) {
        this.mentorId = null;
        this.actualizadoEn = ahora;
    }

    public void programarSesion(Instant proximaSesionEn, Instant ahora) {
        this.proximaSesionEn = Objects.requireNonNull(proximaSesionEn, "proximaSesionEn es obligatoria");
        this.actualizadoEn = ahora;
    }

    private static void requireNombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la celula es obligatorio");
        }
        if (nombre.length() > 200) {
            throw new IllegalArgumentException("El nombre de la celula no puede pasar de 200 caracteres");
        }
    }

    @Override
    public String toString() {
        return "Celula[" + id + ", " + nombre + ", cohorte=" + cohorteId + "]";
    }
}
