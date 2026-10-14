package com.renaser.os.rag.domain.model.circuito;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * El circuito automático de una parte de SER (spec 002, FR-016 y FR-017; plan.md D7): una foto inmutable del estado
 * que comparten todas las instancias. Las reglas viven acá y en ningún otro lado:
 * <ul>
 *   <li><b>Cerrado</b>: pasa todo. Con {@code fallasParaAbrir} fallas del proveedor dentro de la ventana, se abre.</li>
 *   <li><b>Abierto</b>: no pasa nada hasta {@code proximaPrueba}. La espera crece con cada reapertura seguida
 *       (2, 5 y 15 min por defecto) y vuelve a la primera cuando el circuito se cierra.</li>
 *   <li><b>Medio abierto</b> ({@code PROBANDO}): vencida la espera, <b>un</b> pedido toma el turno de prueba
 *       ({@code pruebaVence}); los demás siguen cortados. Si sale bien, se cierra; si falla, se reabre con la espera
 *       siguiente. Si la prueba se corta sin decir cómo le fue, vence y otro pedido puede probar.</li>
 * </ul>
 * Lo manual no pasa por acá: lo combina la evaluación de {@code plataforma}, donde un apagado a mano gana siempre.
 *
 * @param aperturasSeguidas cuántas veces se abrió sin cerrarse en el medio (elige la espera)
 * @param abiertoDesde      cuándo empezó la racha abierta; {@code null} cerrado
 * @param proximaPrueba     cuándo vence (o venció) la espera; {@code null} cerrado
 * @param pruebaVence       hasta cuándo es del pedido que está probando; {@code null} si nadie prueba
 * @param version           sube con cada escritura: es el control de concurrencia entre instancias (y la versión que
 *                          suma {@code /funcionalidades/mias})
 */
public record CircuitoDeIa(ParteDeLaIa parte, EstadoDelCircuito estado, int aperturasSeguidas, Instant abiertoDesde,
                           Instant proximaPrueba, Instant pruebaVence, long version) {

    public CircuitoDeIa {
        Objects.requireNonNull(parte, "parte es obligatoria");
        Objects.requireNonNull(estado, "estado es obligatorio");
        if (aperturasSeguidas < 0 || version < 0) {
            throw new IllegalArgumentException("aperturasSeguidas y version no pueden ser negativas");
        }
        if (estado != EstadoDelCircuito.CERRADO && (abiertoDesde == null || proximaPrueba == null)) {
            throw new IllegalArgumentException("un circuito " + estado + " necesita abiertoDesde y proximaPrueba");
        }
    }

    /** Sin nada guardado: cerrado, versión 0. */
    public static CircuitoDeIa cerrado(ParteDeLaIa parte) {
        return new CircuitoDeIa(parte, EstadoDelCircuito.CERRADO, 0, null, null, null, 0);
    }

    /**
     * Si corta el paso ahora: abierto con la espera corriendo, o con una prueba en curso. Es lo que ve la evaluación
     * de los interruptores y, por ella, la app. Vencida la espera ya no corta: así la app vuelve a ofrecer SER y el
     * próximo pedido puede ser la prueba.
     */
    public boolean cortaElPaso(Instant ahora) {
        return switch (estado) {
            case CERRADO -> false;
            case ABIERTO -> ahora.isBefore(proximaPrueba);
            case PROBANDO -> pruebaVence != null && ahora.isBefore(pruebaVence);
        };
    }

    /** Si un pedido puede tomar ahora el turno de prueba. */
    public boolean admitePrueba(Instant ahora) {
        return switch (estado) {
            case CERRADO -> false;
            case ABIERTO -> !ahora.isBefore(proximaPrueba);
            case PROBANDO -> pruebaVence == null || !ahora.isBefore(pruebaVence);
        };
    }

    /** El estado que se muestra en la consola: abierto con la espera vencida ya está medio abierto. */
    public EstadoDelCircuito estadoEn(Instant ahora) {
        return estado == EstadoDelCircuito.ABIERTO && !ahora.isBefore(proximaPrueba) ? EstadoDelCircuito.PROBANDO
                : estado;
    }

    /** El pedido que llama ahora es la prueba: los demás quedan cortados hasta que diga cómo le fue. */
    public CircuitoDeIa conPruebaEnCurso(Instant ahora, ReglasDelCircuito reglas) {
        if (!admitePrueba(ahora)) {
            throw new IllegalStateException("El circuito de " + parte.clave() + " no admite una prueba ahora");
        }
        return new CircuitoDeIa(parte, EstadoDelCircuito.PROBANDO, aperturasSeguidas, abiertoDesde, proximaPrueba,
                ahora.plus(reglas.plazoDeLaPrueba()), version + 1);
    }

    /**
     * Lo que cambia con una falla del proveedor. Cerrado: se abre si con esta ya son {@code fallasParaAbrir} en la
     * ventana (la quinta abre, la cuarta no). Medio abierto: se reabre con la espera siguiente. Abierto: nada (la
     * falla igual se cuenta, para la consola).
     */
    public Optional<CambioDelCircuito> trasFalla(int fallasEnVentana, Instant ahora, ReglasDelCircuito reglas) {
        return switch (estado) {
            case CERRADO -> fallasEnVentana < reglas.fallasParaAbrir() ? Optional.empty()
                    : Optional.of(abrir(1, ahora, ahora, reglas, fallasEnVentana + " fallas del proveedor en "
                    + enPalabras(reglas.ventana())));
            case PROBANDO -> Optional.of(abrir(aperturasSeguidas + 1, abiertoDesde, ahora, reglas,
                    "Falló el pedido de prueba"));
            case ABIERTO -> Optional.empty();
        };
    }

    /** Lo que cambia con una respuesta del proveedor: solo medio abierto se cierra (y la espera vuelve a la primera). */
    public Optional<CambioDelCircuito> trasExito(Instant ahora) {
        if (estado != EstadoDelCircuito.PROBANDO) {
            return Optional.empty();
        }
        CircuitoDeIa cerrado = new CircuitoDeIa(parte, EstadoDelCircuito.CERRADO, 0, null, null, null, version + 1);
        return Optional.of(new CambioDelCircuito(this, cerrado, "Salió bien el pedido de prueba", ahora));
    }

    private CambioDelCircuito abrir(int aperturas, Instant desde, Instant ahora, ReglasDelCircuito reglas,
                                    String motivo) {
        Duration espera = reglas.esperaTrasLaApertura(aperturas);
        CircuitoDeIa abierto = new CircuitoDeIa(parte, EstadoDelCircuito.ABIERTO, aperturas, desde,
                ahora.plus(espera), null, version + 1);
        return new CambioDelCircuito(this, abierto, motivo + "; se prueba de nuevo en " + enPalabras(espera), ahora);
    }

    /** «2 min» o «45 s»: va en la auditoría y en el push. */
    static String enPalabras(Duration duracion) {
        return duracion.toSecondsPart() == 0 && duracion.toMinutes() > 0 ? duracion.toMinutes() + " min"
                : duracion.toSeconds() + " s";
    }
}
