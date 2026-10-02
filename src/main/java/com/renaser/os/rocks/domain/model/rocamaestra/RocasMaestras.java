package com.renaser.os.rocks.domain.model.rocamaestra;

import com.renaser.os.shared.domain.NotAuthorizedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Las Rocas Maestras de una persona, vistas como la llave de toda la cadena de rocas (D-247): sin las
 * tres (una por eje) no se planifica nada, ni la semana ni el dia ({@code ROCKS_LOCKED}). Las escribe el
 * Mapa de Renacimiento al activarse.
 *
 * <p>Hasta D-247 esta regla estaba copiada cinco veces ({@code RocaDiariaService},
 * {@code RocaSemanalService}, {@code AgregarRocaDiariaService} y dos en {@code DashboardRocasService}) y el
 * acompanante no la podia consultar: proponia un plan que despues fallaba al confirmar (E-496). Ahora
 * vive aca, y la usan los casos de uso, el dashboard y {@code rocks.api.CompuertaDeRocasFinder}.
 */
public final class RocasMaestras {

    /** El codigo con el que empieza el mensaje del rechazo; {@code rocks} lo traduce a sus motivos. */
    public static final String CODIGO_BLOQUEO = "ROCKS_LOCKED";

    private final List<RocaMaestra> maestras;

    private RocasMaestras(List<RocaMaestra> maestras) {
        this.maestras = List.copyOf(maestras);
    }

    public static RocasMaestras de(List<RocaMaestra> maestras) {
        return new RocasMaestras(maestras);
    }

    /** Las tres, una por eje: con menos, la planificacion esta cerrada. */
    public boolean completas() {
        return maestras.size() >= EjeObjetivo.values().length;
    }

    /** Las tres por eje, o {@link NotAuthorizedException} con {@code ROCKS_LOCKED} si falta alguna. */
    public Map<EjeObjetivo, RocaMaestra> exigirCompletas() {
        exigirLaLlave();
        return maestras.stream().collect(Collectors.toMap(RocaMaestra::eje, maestra -> maestra));
    }

    /** La del eje, exigiendo antes las tres. */
    public RocaMaestra exigirDelEje(EjeObjetivo eje) {
        exigirLaLlave();
        return delEje(eje).orElseThrow(
                () -> new NotAuthorizedException(CODIGO_BLOQUEO + ": falta la Roca Maestra de " + eje));
    }

    public Optional<RocaMaestra> delEje(EjeObjetivo eje) {
        return maestras.stream().filter(maestra -> maestra.eje() == eje).findFirst();
    }

    private void exigirLaLlave() {
        if (!completas()) {
            throw new NotAuthorizedException(CODIGO_BLOQUEO + ": completa tu onboarding antes de planificar rocas");
        }
    }
}
