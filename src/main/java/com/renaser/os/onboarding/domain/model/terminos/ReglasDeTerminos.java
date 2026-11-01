package com.renaser.os.onboarding.domain.model.terminos;

import java.util.List;
import java.util.Optional;

/**
 * Las reglas de cuándo una persona debe aceptar unos términos (spec 027, FR-015 a FR-017). Lógica pura.
 */
public final class ReglasDeTerminos {

    private ReglasDeTerminos() {
    }

    /**
     * @param vigente             la última versión publicada de la formación de la persona; vacía si todavía no tiene ninguna
     * @param ultimaAceptada      el número de la última versión de ESA formación que la persona aceptó; vacío si nunca aceptó la de esta formación
     * @param aceptoAlgunaVez     si aceptó términos alguna vez, de cualquier formación (o venía de antes de las versiones)
     * @param publicadas          las versiones publicadas de la formación, con la nota y si pidieron aceptar de nuevo
     */
    public static QuePideLaAceptacion quePide(Optional<VersionDeTerminos> vigente, Optional<Integer> ultimaAceptada, boolean aceptoAlgunaVez,
                                              List<VersionDeTerminos> publicadas) {
        if (vigente.isEmpty()) {
            return QuePideLaAceptacion.NADA;
        }
        if (ultimaAceptada.isEmpty()) {
            return aceptoAlgunaVez ? QuePideLaAceptacion.PRIMERA_EN_LA_FORMACION : QuePideLaAceptacion.PRIMERA_VEZ;
        }
        boolean algunaPidioAceptar = publicadas.stream()
                .filter(v -> v.numero() > ultimaAceptada.get() && v.numero() <= vigente.get().numero())
                .anyMatch(VersionDeTerminos::pideNuevaAceptacion);
        return algunaPidioAceptar ? QuePideLaAceptacion.NUEVA_VERSION : QuePideLaAceptacion.NADA;
    }

    /** La nota que se le muestra a quien debe aceptar de nuevo: la de la versión vigente, o si no la tiene, la de la última que lo pidió. */
    public static Optional<String> notaDelCambio(Optional<VersionDeTerminos> vigente, List<VersionDeTerminos> publicadas, int ultimaAceptada) {
        if (vigente.isPresent() && vigente.get().nota() != null) {
            return Optional.of(vigente.get().nota());
        }
        return publicadas.stream().filter(v -> v.numero() > ultimaAceptada && v.pideNuevaAceptacion() && v.nota() != null)
                .reduce((primera, segunda) -> segunda).map(VersionDeTerminos::nota);
    }
}
