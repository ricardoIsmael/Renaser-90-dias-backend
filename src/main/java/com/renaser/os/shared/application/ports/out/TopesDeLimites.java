package com.renaser.os.shared.application.ports.out;

import com.renaser.os.shared.domain.ContadorDeVentanas.Ventana;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ModoDeLimite;

import java.time.Duration;
import java.util.List;

/**
 * Lo que la política del límite por cuenta necesita saber de la configuración (spec 050, FR-013 y FR-016): los topes de cada grupo,
 * su modo de partida y cada cuánto se relee el modo que el dueño pone en Redis. Lo implementa la clase de propiedades
 * ({@code renaser.proteccion.limites.*}); la política no conoce Spring Boot.
 */
public interface TopesDeLimites {

    /** Apaga todo el filtro (vuelta atrás de emergencia): ningún grupo cuenta ni anota nada. */
    boolean habilitado();

    /** Cada cuánto relee la política el modo que está en Redis (Q6: 30 s). */
    Duration cacheDelModo();

    /** El modo de este grupo si Redis no dice otro. */
    ModoDeLimite modoPorDefecto(GrupoDeLimite grupo);

    /** Las ventanas de este grupo, de la más corta a la más larga; solo las que tienen tope. Vacía si el grupo no tiene ninguno. */
    List<Ventana> ventanasDe(GrupoDeLimite grupo);
}
