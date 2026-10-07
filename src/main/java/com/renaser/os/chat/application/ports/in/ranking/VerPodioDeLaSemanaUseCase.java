package com.renaser.os.chat.application.ports.in.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Puesto;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/** La vista previa del podio de la última semana cerrada, sin publicar nada (D-262). */
public interface VerPodioDeLaSemanaUseCase {

    /** @throws com.renaser.os.shared.domain.NotAuthorizedException si no es ADMIN/ALCHEMIST con la cuenta activa */
    VistaPreviaDelPodio vistaPrevia(UserId actorId);

    /**
     * @param texto      {@code null} si nadie tiene puntaje (no se publicaría)
     * @param imagen     los bytes de la imagen, o {@code null} si nadie tiene puntaje
     * @param plantilla  la plantilla del texto que toca esa semana (A, B o C)
     * @param yaPublicado si el texto de esa semana ya está en el grupo
     */
    record VistaPreviaDelPodio(LocalDate lunes, LocalDate domingo, List<Puesto> puestos, String texto,
                               byte[] imagen, String tipoDeImagen, String plantilla, boolean yaPublicado) {
    }
}
