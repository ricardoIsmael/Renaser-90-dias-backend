package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Las asignaciones relevantes para una decisión, con las invariantes temporales que el
 * dominio sostiene antes de tocar la base.
 *
 * <p>No sustituye a las restricciones de la base: un check-then-insert pierde carreras y por
 * eso V45 repite las mismas reglas como índices únicos parciales. Esta clase existe para que
 * el rechazo llegue como regla de negocio explicable y no como una violación de constraint
 * traducida a 500 (plan.md §3).
 */
public final class ConjuntoAsignaciones {

    private final List<AsignacionCelula> asignaciones;

    private ConjuntoAsignaciones(List<AsignacionCelula> asignaciones) {
        this.asignaciones = List.copyOf(asignaciones);
    }

    public static ConjuntoAsignaciones de(List<AsignacionCelula> asignaciones) {
        return new ConjuntoAsignaciones(Objects.requireNonNull(asignaciones, "asignaciones es obligatorio"));
    }

    public static ConjuntoAsignaciones vacio() {
        return new ConjuntoAsignaciones(List.of());
    }

    public List<AsignacionCelula> todas() {
        return asignaciones;
    }

    /**
     * @throws AsignacionInvalidaException si abrir {@code candidata} rompería una invariante
     *                                     temporal contra lo que ya existe.
     */
    public void verificarPuedeAbrir(AsignacionCelula candidata) {
        if (candidata.funcion().esExclusivaPorCelula()) {
            solapadaEnCelula(candidata).ifPresent(chocada -> {
                throw new AsignacionInvalidaException("El grupo " + candidata.celulaId()
                        + " ya tiene un mentor asignado en ese periodo (" + chocada.usuarioId() + ")");
            });
            solapadaDelMismoUsuarioEnOtraCelula(candidata).ifPresent(chocada -> {
                throw new AsignacionInvalidaException("El mentor " + candidata.usuarioId()
                        + " ya lidera el grupo " + chocada.celulaId() + " en ese periodo");
            });
        }
        if (candidata.funcion() == FuncionAcompanamiento.APRENDIZ) {
            solapadaDelMismoUsuario(candidata).ifPresent(chocada -> {
                throw new AsignacionInvalidaException("El aprendiz " + candidata.usuarioId()
                        + " ya pertenece al grupo " + chocada.celulaId() + " en ese periodo");
            });
        }
    }

    /**
     * Repetir un comando con la misma clave devuelve la asignación que ya se creó, en vez de
     * abrir otro intervalo idéntico.
     */
    public Optional<AsignacionCelula> yaAplicada(String claveOperacion) {
        return asignaciones.stream()
                .filter(a -> a.claveOperacion().equals(claveOperacion))
                .findFirst();
    }

    public Optional<UserId> mentorVigenteEn(CelulaId celulaId, Instant instante) {
        return vigentesEn(celulaId, instante, FuncionAcompanamiento.MENTOR)
                .stream()
                .findFirst();
    }

    public List<UserId> aprendicesVigentesEn(CelulaId celulaId, Instant instante) {
        return vigentesEn(celulaId, instante, FuncionAcompanamiento.APRENDIZ);
    }

    /** Funciones vigentes del grupo, para medir el cupo sin contar acompañamiento. */
    public List<FuncionAcompanamiento> ocupantesVigentesEn(CelulaId celulaId, Instant instante) {
        return asignaciones.stream()
                .filter(a -> a.celulaId().equals(celulaId))
                .filter(a -> a.vigenteEn(instante))
                .map(AsignacionCelula::funcion)
                .toList();
    }

    /**
     * Un grupo sin mentor sigue siendo un grupo. Devolver {@link CoberturaCelula} en vez de
     * un {@code Optional<UserId>} vacío es lo que impide que la UI muestre "no tienes grupo"
     * a alguien que sí lo tiene (plan.md §10).
     */
    public CoberturaCelula coberturaEn(CelulaId celulaId, Instant instante) {
        if (mentorVigenteEn(celulaId, instante).isPresent()) {
            return CoberturaCelula.CON_MENTOR;
        }
        if (!vigentesEn(celulaId, instante, FuncionAcompanamiento.SOPORTE).isEmpty()) {
            return CoberturaCelula.SOPORTE;
        }
        return CoberturaCelula.SIN_COBERTURA;
    }

    /** Intervalos de un usuario con una función dada, recortados contra una ventana. */
    public List<PeriodoAsignacion> periodosDe(UserId usuarioId, FuncionAcompanamiento funcion,
                                               PeriodoAsignacion ventana) {
        return asignaciones.stream()
                .filter(a -> a.usuarioId().equals(usuarioId))
                .filter(a -> a.funcion() == funcion)
                .map(a -> a.periodo().interseccionCon(ventana))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(PeriodoAsignacion::inicio))
                .toList();
    }

    private List<UserId> vigentesEn(CelulaId celulaId, Instant instante, FuncionAcompanamiento funcion) {
        return asignaciones.stream()
                .filter(a -> a.celulaId().equals(celulaId))
                .filter(a -> a.funcion() == funcion)
                .filter(a -> a.vigenteEn(instante))
                .map(AsignacionCelula::usuarioId)
                .toList();
    }

    private Optional<AsignacionCelula> solapadaEnCelula(AsignacionCelula candidata) {
        return asignaciones.stream()
                .filter(a -> !a.id().equals(candidata.id()))
                .filter(a -> a.celulaId().equals(candidata.celulaId()))
                .filter(a -> a.funcion() == candidata.funcion())
                .filter(a -> a.periodo().solapaCon(candidata.periodo()))
                .findFirst();
    }

    private Optional<AsignacionCelula> solapadaDelMismoUsuarioEnOtraCelula(AsignacionCelula candidata) {
        return solapadaDelMismoUsuario(candidata)
                .filter(a -> !a.celulaId().equals(candidata.celulaId()));
    }

    private Optional<AsignacionCelula> solapadaDelMismoUsuario(AsignacionCelula candidata) {
        return asignaciones.stream()
                .filter(a -> !a.id().equals(candidata.id()))
                .filter(a -> a.usuarioId().equals(candidata.usuarioId()))
                .filter(a -> a.funcion() == candidata.funcion())
                .filter(a -> a.periodo().solapaCon(candidata.periodo()))
                .findFirst();
    }
}
