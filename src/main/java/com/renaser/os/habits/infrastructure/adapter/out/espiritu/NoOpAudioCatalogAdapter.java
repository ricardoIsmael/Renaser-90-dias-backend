package com.renaser.os.habits.infrastructure.adapter.out.espiritu;

import com.renaser.os.habits.application.ports.out.espiritu.AudioCatalogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Placeholder que devuelve un catalogo vacio. Fue el UNICO adapter mientras no hubo forma de
 * saber que audio le toca a cada dia; hoy la via normal es
 * {@code AudiosEspirituPersistenceAdapter}, que lee las 43 filas reales de
 * {@code audios_espiritu}.
 *
 * <p><b>Ya no es el default</b> — queda como valvula de escape explicita
 * ({@code renaser.espiritu.catalogo=noop}), mismo patron que
 * {@code NoOpAlmacenamientoAdapter} frente a {@code S3AlmacenamientoAdapter}. Efecto de
 * encenderlo: sin audios en el catalogo, {@code EspirituService.asegurarAvance} nunca crea
 * un track nuevo — el aprendiz queda "al dia" esperando contenido, en vez de fallar. Ver
 * docs/MODULO_HABITS.md.
 */
@Component
@ConditionalOnProperty(name = "renaser.espiritu.catalogo", havingValue = "noop")
public class NoOpAudioCatalogAdapter implements AudioCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpAudioCatalogAdapter.class);

    @Override
    public Optional<AudioEspiritu> porDia(int dia) {
        log.warn("AudioCatalogPort.porDia({}) placeholder: catalogo de Espiritu sin integracion a Drive todavia.",
                dia);
        return Optional.empty();
    }

    @Override
    public List<AudioEspiritu> todos() {
        log.warn("AudioCatalogPort.todos() placeholder: catalogo de Espiritu sin integracion a Drive todavia.");
        return List.of();
    }
}
