package com.renaser.os.habits.application.ports.in.espiritu;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface ConsultarEstadoEspirituUseCase {

    /** Autoservicio: avanza el state machine lazy (ensureAdvanced) y devuelve la vista dia-por-dia. */
    EstadoEspiritu consultar(UserId actorId);

    /** {@code diaActual}: el mayor {@code dia} con track creado, o {@code null} sin ninguno todavia. */
    record EstadoEspiritu(List<DiaEspiritu> dias, Integer diaActual) {
    }

    /**
     * {@code estado}: {@code LOCKED} (sin track — catalogo sin desbloquear todavia),
     * {@code CURRENT} (PENDIENTE), {@code SUBMITTED} (ENTREGADO) o {@code MISSED} (PERDIDO)
     * — mismos cuatro valores del contrato viejo ({@code SpiritDayView.state}, D-36: literal,
     * no traducido).
     *
     * @param audioUrl URL ya firmada, lista para reproducir. Se resuelve SOLO para el dia
     *                 {@code CURRENT} — es el unico que el aprendiz puede escuchar y entregar,
     *                 y firmar los 43 dias en cada lectura de estado seria trabajo tirado en un
     *                 endpoint que la app consulta cada vez que abre Training. {@code null}
     *                 tambien cuando el audio de ese dia todavia no tiene archivo servible
     *                 ({@code audios_espiritu.ruta_storage} en NULL, ver V25): el cliente debe
     *                 mostrar el dia sin reproductor, no romper.
     * @param mimeAudio tipo de contenido del archivo ({@code audio/mpeg} en las 43 filas de hoy)
     * @param tamanoBytes peso del archivo, para que el cliente pueda mostrar progreso de
     *                    descarga y decidir si precargarlo. {@code null} si el catalogo no lo
     *                    tiene.
     */
    record DiaEspiritu(int dia, String titulo, String estado, Instant desbloqueadoEn, Instant fechaLimite,
                        Instant entregadoEn, String resumenTexto, String audioUrl, String mimeAudio,
                        Integer tamanoBytes) {
    }
}
