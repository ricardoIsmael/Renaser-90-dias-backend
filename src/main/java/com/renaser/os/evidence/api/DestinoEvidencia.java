package com.renaser.os.evidence.api;

import java.util.UUID;

/**
 * Destino de una evidencia — arco exclusivo de la tabla {@code evidencias}
 * (CHECK {@code evidencia_un_destino}: exactamente uno de {@code registro_habito_id}/
 * {@code roca_diaria_id}/{@code registro_espiritu_id} no-nulo).
 *
 * <p>En vez de replicar esa regla como una validación en runtime sobre tres campos
 * nullable, se modela como un {@code sealed interface}: un estado inválido (cero o dos
 * destinos a la vez) es irrepresentable en el tipo, no solo rechazado — CLAUDE.MD
 * §5.4.7 ("sealed interface + pattern matching para resultados con variantes
 * cerradas"). Vive en {@code evidence.api} porque es el tipo que {@code rocks}/
 * {@code habits} usan para construir el comando de {@link RegistrarEvidenciaPort}.
 */
public sealed interface DestinoEvidencia {

    record RegistroHabito(UUID registroHabitoId) implements DestinoEvidencia {
        public RegistroHabito {
            if (registroHabitoId == null) {
                throw new IllegalArgumentException("registroHabitoId no puede ser null");
            }
        }
    }

    record RocaDiaria(UUID rocaDiariaId) implements DestinoEvidencia {
        public RocaDiaria {
            if (rocaDiariaId == null) {
                throw new IllegalArgumentException("rocaDiariaId no puede ser null");
            }
        }
    }

    record RegistroEspiritu(UUID registroEspirituId) implements DestinoEvidencia {
        public RegistroEspiritu {
            if (registroEspirituId == null) {
                throw new IllegalArgumentException("registroEspirituId no puede ser null");
            }
        }
    }
}
