package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

/**
 * URL PUT prefirmada (D-34) para la evidencia de un registro diario de habito — el paso
 * que le faltaba al camino generico de evidencia. Hasta ahora {@code habits} solo tenia
 * {@link SubirEvidenciaRegistroUseCase}, que RECIBE un {@code bucket}/{@code rutaStorage}
 * ya subidos pero no daba ninguna forma de subirlos: el cliente movil tenia el endpoint
 * de confirmacion y ningun endpoint de subida. Mismo patron exacto que
 * {@code rocks.SolicitarUrlAdjuntoRocaUseCase} y
 * {@code habits.SolicitarUrlAdjuntoRachaUseCase} — bucket propio {@code evidencia-habitos/}.
 *
 * <p>Sirve para FOTO, VIDEO, AUDIO y CAPTURA por igual: el {@code tipoContenido} (MIME) es
 * lo unico que cambia, y es el cliente el que lo declara. La evidencia de tipo TEXTO no pasa
 * por aca — va directo a {@link SubirEvidenciaRegistroUseCase} con {@code contenidoTexto}.
 *
 * <p><b>El backend no toca los bytes</b>: el archivo viaja del telefono a S3 con esta URL, y
 * despues el cliente confirma con {@code POST /api/v1/habit-tracks/{id}/evidence} mandando la
 * {@code ruta} devuelta aca (NO la URL firmada — lleva credencial y vence).
 */
public interface SolicitarUrlEvidenciaRegistroUseCase {

    UrlEvidenciaRegistro solicitarUrl(SolicitarUrlEvidenciaRegistroCommand command);

    record SolicitarUrlEvidenciaRegistroCommand(@NotNull UserId actorId, @NotNull RegistroHabitoId registroId,
                                                 @NotBlank String tipoContenido) {

        public SolicitarUrlEvidenciaRegistroCommand {
            SelfValidating.validateConstructorArgs(SolicitarUrlEvidenciaRegistroCommand.class, actorId, registroId,
                    tipoContenido);
        }
    }

    record UrlEvidenciaRegistro(URI url, String bucket, String ruta) {
    }
}
