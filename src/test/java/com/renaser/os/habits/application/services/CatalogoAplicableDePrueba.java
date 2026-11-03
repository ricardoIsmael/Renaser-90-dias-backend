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
        responderElCatalogoPorFormacion(loadHabitoPort);
        return new CatalogoAplicable(loadHabitoPort, habitos -> Map.of(), SIN_HISTORIAL);
    }

    /**
     * H-04 (auditoria de consultas del 2026-11-02): {@code CatalogoAplicable} pide el catalogo de la formacion de la persona
     * ({@code catalogoActivoDeFormacion}) en vez de leer el entero y filtrar. Las pruebas siguen sembrando {@code catalogoActivo()}: este doble hace que la
     * consulta por formacion conteste con lo que la prueba sembro, filtrado por la formacion, y se evalua al llamarse (cuando la prueba ya sembro).
     * Va con {@code doAnswer(...).when(...)} para no ejecutar la llamada al armarlo.
     */
    static void responderElCatalogoPorFormacion(LoadHabitoPort loadHabitoPort) {
        if (!Mockito.mockingDetails(loadHabitoPort).isMock()) {
            return;
        }
        Mockito.lenient().doAnswer(invocacion -> {
            com.renaser.os.shared.domain.Formacion formacion = invocacion.getArgument(0);
            return loadHabitoPort.catalogoActivo().stream().filter(habito -> habito.esDeFormacion(formacion)).toList();
        }).when(loadHabitoPort).catalogoActivoDeFormacion(Mockito.any());
    }

    /** Para servicios que no leen el catálogo: no excluye nada. */
    static CatalogoAplicable sinAudiencias() {
        return sinAudiencias(Mockito.mock(LoadHabitoPort.class));
    }
}
