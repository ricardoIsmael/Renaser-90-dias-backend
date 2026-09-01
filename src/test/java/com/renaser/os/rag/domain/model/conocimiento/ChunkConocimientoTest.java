package com.renaser.os.rag.domain.model.conocimiento;

import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkConocimientoTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));
    /** El id ya no lo sortea el agregado: entra por parametro, lo arma el caso de uso (IdGenerator). */
    private static final ChunkConocimientoId ID = ChunkConocimientoId.of(
            UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private static List<Float> vectorValido() {
        return Collections.nCopies(ChunkConocimiento.DIMENSION_EMBEDDING, 0.1f);
    }

    @Test
    void indexaUnChunkConDatosValidos() {
        ChunkConocimiento chunk = ChunkConocimiento.indexar(ID, "LECCION", "texto", "doc-1", "leccion-1",
                "contenido de prueba", vectorValido(), Map.of("origen", "manual"), CLOCK);

        assertThat(chunk.id()).isNotNull();
        assertThat(chunk.tipoFuente()).isEqualTo("LECCION");
        assertThat(chunk.clase()).isEqualTo("texto");
        assertThat(chunk.documentoId()).isEqualTo("doc-1");
        assertThat(chunk.leccionId()).isEqualTo("leccion-1");
        assertThat(chunk.embedding()).hasSize(ChunkConocimiento.DIMENSION_EMBEDDING);
        assertThat(chunk.metadatos()).containsEntry("origen", "manual");
        assertThat(chunk.creadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void rechazaContenidoVacio() {
        assertThatThrownBy(() -> ChunkConocimiento.indexar(ID, "LECCION", null, null, null, "   ", vectorValido(),
                Map.of(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contenido");
    }

    @Test
    void rechazaTipoFuenteVacio() {
        assertThatThrownBy(() -> ChunkConocimiento.indexar(ID, " ", null, null, null, "contenido", vectorValido(),
                Map.of(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipoFuente");
    }

    @Test
    void rechazaEmbeddingConDimensionIncorrecta() {
        assertThatThrownBy(() -> ChunkConocimiento.indexar(ID, "LECCION", null, null, null, "contenido",
                List.of(0.1f, 0.2f), Map.of(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("768");
    }

    @Test
    void metadatosNuloSeConvierteEnMapaVacio() {
        ChunkConocimiento chunk = ChunkConocimiento.indexar(ID, "LECCION", null, null, null, "contenido",
                vectorValido(), null, CLOCK);

        assertThat(chunk.metadatos()).isEmpty();
    }

    @Test
    void rehydrateNoRevalidaInvariantesDeCreacion() {
        ChunkConocimientoId id = ChunkConocimientoId.of(UUID.randomUUID());

        ChunkConocimiento chunk = ChunkConocimiento.rehydrate(id, "LECCION", null, null, null, "contenido",
                vectorValido(), null, CLOCK.now());

        assertThat(chunk.id()).isEqualTo(id);
        assertThat(chunk.metadatos()).isEmpty();
    }
}
