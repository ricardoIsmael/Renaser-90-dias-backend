package com.renaser.os.phasecontracts.domain.model.contrato;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class ContratoFase {

    /** Default de la columna `bucket` en el SQL — mismo nombre que el bucket viejo de Supabase Storage. */
    public static final String BUCKET_DEFAULT = "onboarding-signatures";

    private static final String PREFIJO_RUTA = "firmas";

    private final ContratoFaseId id;
    private final UserId participanteId;
    private final FasePrograma fase;
    private final String bucket;
    private final String rutaFirma;
    private final Instant firmadoEn;
    private final Instant creadoEn;

    public static ContratoFase firmar(UserId participanteId, int diaProgramaActual, Clock clock) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        FasePrograma fase = FasePrograma.paraDiaPrograma(diaProgramaActual);
        requireFirmable(fase, diaProgramaActual);
        Instant ahora = clock.now();
        return new ContratoFase(ContratoFaseId.newId(), participanteId, fase, BUCKET_DEFAULT,
                rutaFirma(participanteId, fase), ahora, ahora);
    }

    /** Solo para el adaptador de persistencia: reconstruye un contrato ya existente. */
    public static ContratoFase rehydrate(ContratoFaseId id, UserId participanteId, FasePrograma fase, String bucket,
                                          String rutaFirma, Instant firmadoEn, Instant creadoEn) {
        return new ContratoFase(id, participanteId, fase, bucket, rutaFirma, firmadoEn, creadoEn);
    }

    /**
     * Ruta deterministica dentro del bucket: firmas/{participanteId}/fase_{numero}.svg.
     * Publica porque el caso de uso que pide la URL prefirmada de SUBIDA (antes de
     * que exista el ContratoFase) necesita calcular la misma ruta que usara firmar().
     */
    public static String rutaFirma(UserId participanteId, FasePrograma fase) {
        return PREFIJO_RUTA + "/" + participanteId + "/fase_" + fase.numero() + ".svg";
    }

    private static void requireFirmable(FasePrograma fase, int diaProgramaActual) {
        if (fase == FasePrograma.FASE_1_RENACER) {
            throw new IllegalArgumentException("La Fase 1 se firma en el Pacto del onboarding, no aqui");
        }
        if (!fase.firmaDesbloqueadaEnDia(diaProgramaActual)) {
            throw new IllegalArgumentException("Todavia no te toca firmar el pacto de " + fase.etiqueta()
                    + " (se desbloquea el dia " + fase.diaDesbloqueoFirma() + " de programa)");
        }
    }

    @Override
    public String toString() {
        return "ContratoFase[" + id + ", " + participanteId + ", " + fase + "]";
    }
}
