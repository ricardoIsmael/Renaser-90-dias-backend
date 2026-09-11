package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Una persona dentro de un grupo durante un intervalo concreto. Es el historial que hoy no
 * existe: {@code celulas.mentor_id} y {@code participantes_programa.mentor_id} son punteros
 * al presente, y de un puntero no se puede deducir quién acompañaba en agosto
 * (research.md). Los punteros se conservan como proyección del intervalo vigente, no como
 * fuente de verdad.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class AsignacionCelula {

    private final AsignacionId id;
    private final CelulaId celulaId;
    private final UserId usuarioId;
    private final FuncionAcompanamiento funcion;
    private PeriodoAsignacion periodo;
    private MotivoAsignacion motivo;
    /** Quién la ejecutó. {@code null} = un job; no se fabrica un usuario técnico falso. */
    private final UserId actorId;
    private final String claveOperacion;

    /**
     * @param claveOperacion identificador estable del comando que abre este intervalo.
     *                       Repetir el mismo comando debe encontrar la asignación ya creada
     *                       en vez de abrir otra (contracts.md, "Errores y concurrencia").
     */
    public static AsignacionCelula abrir(AsignacionId id, CelulaId celulaId, UserId usuarioId,
                                          FuncionAcompanamiento funcion, Instant desde, MotivoAsignacion motivo,
                                          UserId actorId, String claveOperacion) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(celulaId, "celulaId es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(funcion, "funcion es obligatoria");
        Objects.requireNonNull(motivo, "motivo es obligatorio");
        requireClaveValida(claveOperacion);
        return new AsignacionCelula(id, celulaId, usuarioId, funcion, PeriodoAsignacion.abierto(desde), motivo,
                actorId, claveOperacion.trim());
    }

    /** Solo para el adaptador de persistencia. */
    public static AsignacionCelula rehydrate(AsignacionId id, CelulaId celulaId, UserId usuarioId,
                                              FuncionAcompanamiento funcion, PeriodoAsignacion periodo,
                                              MotivoAsignacion motivo, UserId actorId, String claveOperacion) {
        return new AsignacionCelula(id, celulaId, usuarioId, funcion, periodo, motivo, actorId, claveOperacion);
    }

    /**
     * Cierra con la hora real de ejecución. Cerrar dos veces revienta en vez de pasar
     * inadvertido: un segundo cierre significa que el comando se ejecutó sin comprobar el
     * estado, y tragárselo dejaría un intervalo con la fecha equivocada.
     */
    public void cerrar(Instant momento, MotivoAsignacion motivoCierre) {
        this.periodo = periodo.cerrarEn(momento);
        this.motivo = Objects.requireNonNull(motivoCierre, "motivoCierre es obligatorio");
    }

    public boolean vigente() {
        return periodo.vigente();
    }

    public boolean vigenteEn(Instant instante) {
        return periodo.contiene(instante);
    }

    public boolean ejecutadaPorSistema() {
        return actorId == null;
    }

    private static void requireClaveValida(String claveOperacion) {
        if (claveOperacion == null || claveOperacion.isBlank()) {
            throw new IllegalArgumentException(
                    "claveOperacion es obligatoria: sin ella repetir el comando duplicaria el intervalo");
        }
        if (claveOperacion.length() > 200) {
            throw new IllegalArgumentException("claveOperacion no puede pasar de 200 caracteres");
        }
    }

    @Override
    public String toString() {
        return "AsignacionCelula[" + funcion + " " + usuarioId + " en " + celulaId + " " + periodo + "]";
    }
}
