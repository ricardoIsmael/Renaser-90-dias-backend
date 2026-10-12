package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.audiencia.HistorialDeRolPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.audiencia.HistorialDeRol;
import com.renaser.os.shared.domain.UserId;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;

/**
 * Para las pruebas anteriores a la spec 001: un {@link CatalogoAplicable} sin audiencias ni historial de rol, que deja
 * pasar todo el catálogo activo — el comportamiento de antes de D-264.
 */
final class CatalogoAplicableDePrueba {

    private static final HistorialDeRolPort SIN_HISTORIAL = new HistorialDeRolPort() {
        @Override
        public HistorialDeRol de(UserId persona) {
            return HistorialDeRol.vacio();
        }

        @Override
        public void anotarCambio(UserId persona, CodigoDeRol anterior, CodigoDeRol nuevo, Instant cuando) {
            throw new UnsupportedOperationException("solo lectura en pruebas");
        }
    };

    private CatalogoAplicableDePrueba() {
    }

    static CatalogoAplicable sinAudiencias(LoadHabitoPort loadHabitoPort) {
        return new CatalogoAplicable(loadHabitoPort, habitos -> Map.of(), SIN_HISTORIAL);
    }

    /** Para servicios que no leen el catálogo: no excluye nada. */
    static CatalogoAplicable sinAudiencias() {
        return sinAudiencias(Mockito.mock(LoadHabitoPort.class));
    }
}
