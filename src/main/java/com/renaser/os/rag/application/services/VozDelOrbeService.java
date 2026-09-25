package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.voz.VozDelOrbeUseCase;
import com.renaser.os.rag.application.ports.out.ia.SintetizarVozPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Voz del orbe (D-159). <b>Sin {@code @Transactional}</b> (regla 01, C-1): generar el audio es una
 * llamada de red de varios segundos y no hay nada que escribir en la base.
 *
 * <p>{@link #preparar} deja el audio generandose en un hilo virtual y devuelve enseguida: cuando a
 * la segunda oracion le toque sonar, ya esta lista. Los audios viven en memoria
 * {@link #VIGENCIA} y se barren en cada pedido nuevo, sin scheduler.
 *
 * <p><b>Limite conocido:</b> la memoria es de ESTA instancia. Hoy produccion es una sola EC2
 * ({@code docs/DESPLIEGUE_Y_CI.md}). Con varias instancias, el {@code GET} podria caer en otra y
 * dar 404: ahi se pasa a Redis, cambiando solo donde se guardan.
 */
@Service
public class VozDelOrbeService implements VozDelOrbeUseCase {

    private static final Logger log = LoggerFactory.getLogger(VozDelOrbeService.class);

    static final Duration VIGENCIA = Duration.ofMinutes(2);
    /** Tope de audios en memoria (~0,5 MB cada uno). Pasado, la app habla con su propia voz. */
    static final int MAXIMO_EN_MEMORIA = 300;

    private final UserSummaryFinder userSummaryFinder;
    private final SintetizarVozPort sintetizarVozPort;
    private final Clock clock;
    private final Executor generador;
    private final Map<UUID, Entrada> audios = new ConcurrentHashMap<>();

    @Autowired
    public VozDelOrbeService(UserSummaryFinder userSummaryFinder, SintetizarVozPort sintetizarVozPort, Clock clock) {
        this(userSummaryFinder, sintetizarVozPort, clock, Executors.newVirtualThreadPerTaskExecutor());
    }

    VozDelOrbeService(UserSummaryFinder userSummaryFinder, SintetizarVozPort sintetizarVozPort, Clock clock,
                      Executor generador) {
        this.userSummaryFinder = userSummaryFinder;
        this.sintetizarVozPort = sintetizarVozPort;
        this.clock = clock;
        this.generador = generador;
    }

    @Override
    public Optional<UUID> preparar(UserId actorId, String texto) {
        requireActivo(actorId);
        String valido = textoValido(texto);
        barrerVencidos();
        if (!sintetizarVozPort.disponible() || audios.size() >= MAXIMO_EN_MEMORIA) {
            return Optional.empty();
        }
        UUID id = UUID.randomUUID();
        AudioEnCurso audio = new AudioEnCurso();
        audios.put(id, new Entrada(actorId, audio, clock.now().plus(VIGENCIA)));
        generador.execute(() -> generar(valido, audio));
        return Optional.of(id);
    }

    @Override
    public Optional<AudioDelOrbe> buscar(UserId actorId, UUID id) {
        requireActivo(actorId);
        Entrada entrada = audios.get(id);
        if (entrada == null || !entrada.dueno().equals(actorId) || entrada.vencio(clock.now())) {
            return Optional.empty();
        }
        return Optional.of(entrada.audio());
    }

    private void generar(String texto, AudioEnCurso audio) {
        try {
            if (!sintetizarVozPort.sintetizar(texto, audio::agregar)) {
                log.warn("La voz del orbe no salio completa; la app usa el TTS del telefono");
            }
        } catch (RuntimeException e) {
            log.warn("La voz del orbe fallo ({})", e.getClass().getSimpleName());
        } finally {
            audio.cerrar();
        }
    }

    private void barrerVencidos() {
        Instant ahora = clock.now();
        audios.values().removeIf(entrada -> entrada.vencio(ahora));
    }

    private static String textoValido(String texto) {
        String recortado = texto == null ? "" : texto.strip();
        if (recortado.isEmpty()) {
            throw new IllegalArgumentException("El texto a leer en voz alta no puede estar vacio");
        }
        if (recortado.length() > LARGO_MAXIMO_TEXTO) {
            throw new IllegalArgumentException(
                    "El texto a leer en voz alta no puede superar " + LARGO_MAXIMO_TEXTO + " caracteres");
        }
        return recortado;
    }

    private void requireActivo(UserId actorId) {
        UserSummary usuario = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }

    private record Entrada(UserId dueno, AudioEnCurso audio, Instant vence) {

        boolean vencio(Instant ahora) {
            return !ahora.isBefore(vence);
        }
    }
}
