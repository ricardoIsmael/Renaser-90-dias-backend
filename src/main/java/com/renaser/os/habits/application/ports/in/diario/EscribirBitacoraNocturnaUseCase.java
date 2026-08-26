package com.renaser.os.habits.application.ports.in.diario;

import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * "Bitacora Nocturna" (Diario Nocturno, {@code TipoEntradaDiario.BITACORA_NOCTURNA}) —
 * traduccion de {@code PUT /api/v1/journal/today} (repo viejo, R-06 — vivia en `rocks`
 * feature, pero `entradas_diario` es tabla de `habits` en este backend). Upsert: escribir
 * dos veces el mismo dia pisa el contenido anterior, no acumula. Desbloquea el Espejo
 * Sombra de `rag`, que analiza estas entradas semana a semana (D-50, {@code habits.api.EntradaDiarioFinder}).
 */
public interface EscribirBitacoraNocturnaUseCase {

    EntradaDiario escribir(EscribirBitacoraNocturnaCommand command);

    /**
     * Al menos uno de {@code contenidoTexto} o {@code audioBucket}+{@code audioRuta} es
     * obligatorio (mismo refine que {@code UpsertJournalEntryInput} del repo viejo). El
     * audio ya debe estar subido via {@code AlmacenamientoPort} — el patron upload-url de
     * este backend, no una URL directa.
     */
    record EscribirBitacoraNocturnaCommand(@NotNull UserId actorId, String contenidoTexto, String audioBucket,
                                            String audioRuta) {
        public EscribirBitacoraNocturnaCommand {
            SelfValidating.validateConstructorArgs(EscribirBitacoraNocturnaCommand.class, actorId, contenidoTexto,
                    audioBucket, audioRuta);
            boolean sinTexto = contenidoTexto == null || contenidoTexto.isBlank();
            boolean sinAudio = audioBucket == null || audioRuta == null;
            if (sinTexto && sinAudio) {
                throw new IllegalArgumentException("Debes escribir texto o adjuntar un audio");
            }
        }
    }
}
