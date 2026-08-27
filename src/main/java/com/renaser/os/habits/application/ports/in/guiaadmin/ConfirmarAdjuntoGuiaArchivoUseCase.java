package com.renaser.os.habits.application.ports.in.guiaadmin;

import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.SeccionGuia;
import com.renaser.os.habits.domain.model.guia.TipoMedioGuia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Paso 2 del adjunto de guia por archivo (hueco #11): confirma una subida ya hecha a la URL
 * de {@link SolicitarUrlAdjuntoGuiaUseCase} y cuelga un {@code AdjuntoGuia} de tipo IMAGEN o
 * AUDIO de una seccion de guia. Mismo comportamiento de "crea la guia si no existe" que
 * {@code CrearAdjuntoGuiaEnlaceUseCase}. {@code tipoMedio} distinto de IMAGEN/AUDIO (o sea,
 * ENLACE) lo rechaza {@link AdjuntoGuia#deArchivo} con {@code IllegalArgumentException} — esa
 * via sigue siendo exclusiva de {@code CrearAdjuntoGuiaEnlaceUseCase}.
 *
 * <p>A diferencia de {@code ConfirmarAvatarUseCase}, esta confirmacion NO resuelve una URL de
 * lectura: el dominio guarda {@code rutaStorage} cruda a proposito (invariante "jamas una
 * URL" de {@link AdjuntoGuia}, ver su javadoc), igual que {@code RocaDiariaService#completar}
 * nunca resuelve la evidencia que recibe. La resolucion a URL firmada queda para el lado que
 * sirve el contenido — no forma parte de este hueco (ver reporte de esta tarea).
 */
public interface ConfirmarAdjuntoGuiaArchivoUseCase {

    AdjuntoGuia confirmar(ConfirmarAdjuntoGuiaArchivoCommand command);

    record ConfirmarAdjuntoGuiaArchivoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId, int diaInicio,
                                               @NotNull SeccionGuia seccion, @NotNull TipoMedioGuia tipoMedio,
                                               @NotBlank String bucket, @NotBlank String ruta, String mime,
                                               Integer tamanoBytes, String nombreOriginal, String titulo) {
        public ConfirmarAdjuntoGuiaArchivoCommand {
            SelfValidating.validateConstructorArgs(ConfirmarAdjuntoGuiaArchivoCommand.class, actorId, habitoId,
                    diaInicio, seccion, tipoMedio, bucket, ruta, mime, tamanoBytes, nombreOriginal, titulo);
        }
    }
}
