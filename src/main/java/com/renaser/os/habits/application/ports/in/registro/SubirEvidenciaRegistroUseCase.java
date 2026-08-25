package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.evidence.api.RegistrarEvidenciaPort.EvidenciaRegistrada;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Sube la evidencia de un registro diario de hábito, delegando en
 * {@code evidence.api.RegistrarEvidenciaPort} — cierra D-H6 de
 * {@code docs/MODULO_HABITS.md} (ningún endpoint de {@code habits} integraba
 * evidencia todavía). No otorga puntos ni cambia el estado del registro: eso lo
 * sigue haciendo {@code CompletarRegistroUseCase} — subir evidencia y completar son
 * dos pasos independientes, igual que en {@code rocks} (donde sí van juntos porque
 * ahí una Roca Diaria se completa ÚNICAMENTE con evidencia — acá un hábito puede
 * completarse sin evidencia si {@code ExigenciaEvidencia} es OPCIONAL).
 */
public interface SubirEvidenciaRegistroUseCase {

    EvidenciaRegistrada subir(SubirEvidenciaRegistroCommand command);

    /**
     * Para {@code tipo != TEXTO}: {@code bucket}+{@code rutaStorage} (ya subidos vía
     * {@code AlmacenamientoPort}). Para {@code TEXTO}: {@code contenidoTexto}. La
     * validación fina (media-o-texto, GPS coherente) la hace
     * {@code RegistrarEvidenciaPort.RegistrarEvidenciaComando} — este comando solo
     * valida que los campos obligatorios de identidad estén presentes.
     */
    record SubirEvidenciaRegistroCommand(@NotNull UserId actorId, @NotNull RegistroHabitoId registroId,
                                          @NotNull TipoEvidencia tipo, String bucket, String rutaStorage,
                                          String contenidoTexto, Instant timestampExif, Double gpsLat,
                                          Double gpsLng) {

        public SubirEvidenciaRegistroCommand {
            SelfValidating.validateConstructorArgs(SubirEvidenciaRegistroCommand.class, actorId, registroId, tipo,
                    bucket, rutaStorage, contenidoTexto, timestampExif, gpsLat, gpsLng);
        }
    }
}
