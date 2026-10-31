package com.renaser.os;

import com.renaser.os.shared.domain.Clock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * El reloj de las pruebas de integración: UN solo bean {@code @Primary} para todo el contexto de
 * Spring, en vez de uno propio por clase de prueba.
 *
 * <p><b>Por qué existe.</b> Cada IT que necesitaba una hora fija declaraba su propio
 * {@code @TestConfiguration} con un {@code @Primary Clock}. Spring cachea los contextos por la
 * configuración de la clase, así que cada una de esas clases arrancaba su propio contexto —con su
 * Postgres y su Redis— aunque en lo demás fuera idéntica a las vecinas (E-649). Con este reloj
 * todas comparten contexto y cada prueba pone la hora que necesita.
 *
 * <p><b>Cómo se usa.</b> Se inyecta con {@code @Autowired RelojDePrueba reloj} y se fija como
 * <b>primera</b> línea del {@code @BeforeEach} (antes de sembrar nada que dependa de la hora):
 * {@code reloj.fijar(AHORA)}. No hace falta restablecerlo: {@link RestablecerRelojDePrueba} lo
 * deja en hora real al terminar CADA prueba, también si la prueba falla o se olvida. Una clase
 * que necesita la hora desde un {@code @BeforeAll} debe pasarla a {@code @BeforeEach}: el reloj
 * ya no sobrevive entre pruebas.
 *
 * <p><b>Mientras nadie lo fije</b> es el reloj del sistema (UTC, igual que {@code SystemClock}), así
 * que las pruebas que no lo tocan ven exactamente lo que veían antes. Fijo, {@link #today()} sale
 * del instante fijado en UTC, igual que el reloj de producción.
 */
public final class RelojDePrueba implements Clock {

    private final AtomicReference<Instant> fijo = new AtomicReference<>();

    /** Congela el reloj en ese instante hasta que se vuelva a fijar, se avance o se restablezca. */
    public void fijar(Instant instante) {
        fijo.set(java.util.Objects.requireNonNull(instante, "instante"));
    }

    /** Mueve un reloj ya fijado hacia adelante (o atrás, con una duración negativa). */
    public void avanzar(Duration duracion) {
        if (fijo.updateAndGet(actual -> actual == null ? null : actual.plus(duracion)) == null) {
            throw new IllegalStateException("El reloj de prueba no está fijado: fija la hora antes de avanzarla");
        }
    }

    /** Vuelve a la hora real. Lo llama {@link RestablecerRelojDePrueba} después de cada prueba. */
    public void restablecer() {
        fijo.set(null);
    }

    /** Si alguna prueba lo dejó fijo. */
    public boolean estaFijo() {
        return fijo.get() != null;
    }

    @Override
    public Instant now() {
        Instant fijado = fijo.get();
        return fijado != null ? fijado : Instant.now();
    }

    @Override
    public LocalDate today() {
        return now().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
