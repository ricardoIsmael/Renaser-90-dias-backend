package com.renaser.os.habits.infrastructure.adapter.out.espiritu;

import com.renaser.os.habits.application.ports.out.espiritu.AudioCatalogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Placeholder mientras no hay integracion con Google Drive (encargo explicito: no integrar
 * Drive en esta pasada) — mismo patron EXACTO que {@code NoOpAlmacenamientoAdapter} (S3,
 * D-34) y {@code NoOpEvidenciaValidacionIAAdapter} (Gemini/Vertex, D-39): un adapter
 * placeholder explicito, no un puerto sin implementacion. Efecto: sin audios en el
 * catalogo, {@code EspirituService.asegurarAvance} nunca crea un track nuevo — el
 * aprendiz queda "al dia" esperando contenido, en vez de fallar. Ver docs/MODULO_HABITS.md.
 */
@Component
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
