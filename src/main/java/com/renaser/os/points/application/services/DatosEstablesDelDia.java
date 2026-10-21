package com.renaser.os.points.application.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.renaser.os.points.domain.model.semaforo.mensaje.HabitosDestacados;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Lo que se sabe de la persona y no cambia durante su día —el nombre y los hábitos que más y menos le cuestan en la
 * ventana del semáforo, que termina ayer— guardado en memoria por persona y día local (D-277, SC-004). Con esto,
 * releer {@code /home} diez veces el mismo día cuesta una lectura de cada dato, no diez.
 *
 * <p><b>Vence a la medianoche de la zona de la persona</b> ({@link Clave#medianoche()}), contada con el {@link Clock}
 * del sistema y no con el reloj de la máquina: la entrada del 18 de octubre en Lima deja de servir a las 05:00 UTC
 * del 19, no a las 00:00 UTC ni 24 horas después de guardarse (regla 02 §1 y §3). El día va además en la clave, así
 * que un día nuevo nunca encuentra lo de ayer aunque el vencimiento se retrase.
 *
 * <p><b>Acotado</b> a {@value #MAXIMO_DE_ENTRADAS} personas-día: pasado el tope sale la que menos se usa y se vuelve
 * a leer cuando haga falta. Es por instancia (no se comparte entre servidores): cada una lee una vez al día por
 * persona. El nombre que se cambie a mitad de día se ve al día siguiente, como pidió el dueño.
 */
@Component
class DatosEstablesDelDia {

    static final int MAXIMO_DE_ENTRADAS = 2000;
    private static final long NANOS_POR_SEGUNDO = 1_000_000_000L;

    private final Cache<Clave, Memoria> memorias;

    DatosEstablesDelDia(Clock clock) {
        this.memorias = Caffeine.newBuilder()
                .maximumSize(MAXIMO_DE_ENTRADAS)
                .executor(Runnable::run)
                .ticker(() -> nanosDe(clock.now()))
                .expireAfter(new HastaLaMedianoche(clock))
                .build();
    }

    /** La memoria de esa persona en ese día local; vacía la primera vez. */
    Memoria de(UserId persona, LocalDate dia, ZoneId zona) {
        return memorias.get(new Clave(persona, dia, zona), clave -> new Memoria());
    }

    /** Solo para las pruebas: cuántas hay guardadas después de aplicar el tope y los vencimientos. */
    long guardadas() {
        memorias.cleanUp();
        return memorias.estimatedSize();
    }

    private static long nanosDe(Instant instante) {
        return Math.addExact(Math.multiplyExact(instante.getEpochSecond(), NANOS_POR_SEGUNDO), instante.getNano());
    }

    /** Una persona en un día local de su zona. */
    private record Clave(UserId persona, LocalDate dia, ZoneId zona) {

        Instant medianoche() {
            return dia.plusDays(1).atStartOfDay(zona).toInstant();
        }
    }

    /** Cada entrada dura hasta la medianoche local de su clave; si ya pasó, no se guarda. */
    private record HastaLaMedianoche(Clock clock) implements Expiry<Clave, Memoria> {

        @Override
        public long expireAfterCreate(Clave clave, Memoria memoria, long ahora) {
            Duration resta = Duration.between(clock.now(), clave.medianoche());
            return resta.isNegative() ? 0 : resta.toNanos();
        }

        @Override
        public long expireAfterUpdate(Clave clave, Memoria memoria, long ahora, long duracionActual) {
            return duracionActual;
        }

        @Override
        public long expireAfterRead(Clave clave, Memoria memoria, long ahora, long duracionActual) {
            return duracionActual;
        }
    }

    /**
     * Los datos de una persona-día, cada uno leído la primera vez que alguien lo pide. Dos lecturas simultáneas
     * pueden leer el mismo dato dos veces; el resultado es el mismo y la segunda solo lo vuelve a guardar.
     */
    static final class Memoria {

        private volatile Optional<String> nombre;
        private volatile HabitosDestacados destacados;

        Optional<String> nombre(Supplier<Optional<String>> lector) {
            Optional<String> guardado = nombre;
            if (guardado == null) {
                guardado = lector.get();
                nombre = guardado;
            }
            return guardado;
        }

        HabitosDestacados destacados(Supplier<HabitosDestacados> lector) {
            HabitosDestacados guardado = destacados;
            if (guardado == null) {
                guardado = lector.get();
                destacados = guardado;
            }
            return guardado;
        }
    }
}
