package com.renaser.os.habits.application.ports.in.guiaadmin;

import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.SeccionGuia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuelga un adjunto de tipo ENLACE (URL pegada, ej. un video de YouTube — decision de
 * producto 2026-08-11 de no subir video) de una seccion de guia. Si el habito no tiene
 * todavia una guia que empiece en {@code diaInicio}, se crea una con los textos vacios
 * (mismo comportamiento que el contrato viejo, `habitsAdmin.ts` javadoc de
 * {@code CreateGuideAttachmentInput}).
 *
 * <p>NO cubre adjuntos IMAGEN/AUDIO (subida de archivo real) — esos van por
 * {@code SolicitarUrlAdjuntoGuiaUseCase} + {@code ConfirmarAdjuntoGuiaArchivoUseCase}
 * (patron upload-url -> PUT -> confirmar, no multipart).
 */
public interface CrearAdjuntoGuiaEnlaceUseCase {

    AdjuntoGuia crear(CrearAdjuntoGuiaEnlaceCommand command);

    record CrearAdjuntoGuiaEnlaceCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId, int diaInicio,
                                          @NotNull SeccionGuia seccion, @NotBlank String url, String titulo) {
        public CrearAdjuntoGuiaEnlaceCommand {
            SelfValidating.validateConstructorArgs(CrearAdjuntoGuiaEnlaceCommand.class, actorId, habitoId, diaInicio,
                    seccion, url, titulo);
        }
    }
}
