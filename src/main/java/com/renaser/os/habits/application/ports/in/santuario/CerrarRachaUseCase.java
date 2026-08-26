package com.renaser.os.habits.application.ports.in.santuario;

import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public interface CerrarRachaUseCase {

    /**
     * Cierra la racha activa del actor CON EVIDENCIA — obligatoria, igual que el repo
     * viejo (`PhoneFreeCompleteInput`, {@code phoneFree.ts}: "el cierre va SIEMPRE con
     * evidencia"). Full-cycle (24h) otorga puntos; hito parcial libera el track. La
     * evidencia se cuelga del registro en que ARRANCO la racha (no del de hoy), via
     * {@code evidence.api.RegistrarEvidenciaPort} — mismo puerto que usa el resto de
     * `habits`/`rocks`.
     */
    RachaSinCelular cerrar(CerrarRachaCommand command);

    /**
     * Para {@code tipo != TEXTO}: {@code bucket}+{@code rutaStorage} (ya subidos via
     * {@code SolicitarUrlAdjuntoRachaUseCase}). Para {@code TEXTO}: {@code contenidoTexto}.
     * La validacion fina (media-o-texto) la hace {@code RegistrarEvidenciaComando} — este
     * comando solo exige que el tipo este presente.
     */
    record CerrarRachaCommand(@NotNull UserId actorId, @NotNull TipoEvidencia tipoEvidencia, String bucket,
                               String rutaStorage, String contenidoTexto, Instant timestampExif) {
        public CerrarRachaCommand {
            SelfValidating.validateConstructorArgs(CerrarRachaCommand.class, actorId, tipoEvidencia, bucket,
                    rutaStorage, contenidoTexto, timestampExif);
        }
    }
}
