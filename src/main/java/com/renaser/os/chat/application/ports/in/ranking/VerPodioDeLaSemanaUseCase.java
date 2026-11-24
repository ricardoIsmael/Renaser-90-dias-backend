package com.renaser.os.chat.application.ports.in.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Puesto;
import com.renaser.os.chat.domain.model.ranking.TipoDeRankingSemanal;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/** La vista previa del podio de la última semana cerrada, sin publicar nada (D-262; los cuatro rankings desde D-367). */
public interface VerPodioDeLaSemanaUseCase {

    /** @throws com.renaser.os.shared.domain.NotAuthorizedException si no es ADMIN/ALCHEMIST con la cuenta activa */
    default VistaPreviaDelPodio vistaPrevia(UserId actorId) {
        return vistaPrevia(actorId, Formacion.POR_DEFECTO);
    }

    /** Spec 024: el podio General de una formación (el contrato de siempre). */
    default VistaPreviaDelPodio vistaPrevia(UserId actorId, Formacion formacion) {
        return vistaPrevia(actorId, formacion, TipoDeRankingSemanal.GENERAL);
    }

    /** D-367: el podio de uno de los cuatro rankings. */
    VistaPreviaDelPodio vistaPrevia(UserId actorId, Formacion formacion, TipoDeRankingSemanal ranking);

    /** D-367: los cuatro podios de la formación, en el orden en que se publican: General, Coherencia, Kilómetros y Gimnasio. */
    List<VistaPreviaDelPodio> vistasPrevias(UserId actorId, Formacion formacion);

    /**
     * @param texto      {@code null} si nadie tiene valor (no se publicaría)
     * @param imagen     los bytes de la imagen, o {@code null} si nadie tiene valor
     * @param plantilla  la plantilla del texto que toca esa semana a ese ranking (A, B o C)
     * @param yaPublicado si el texto de esa semana de ese ranking ya está en el grupo
     * @param ranking    cuál de los cuatro es
     */
    record VistaPreviaDelPodio(LocalDate lunes, LocalDate domingo, List<Puesto> puestos, String texto,
                               byte[] imagen, String tipoDeImagen, String plantilla, boolean yaPublicado,
                               TipoDeRankingSemanal ranking) {

        /** El contrato de antes de D-367: el General. */
        public VistaPreviaDelPodio(LocalDate lunes, LocalDate domingo, List<Puesto> puestos, String texto,
                                   byte[] imagen, String tipoDeImagen, String plantilla, boolean yaPublicado) {
            this(lunes, domingo, puestos, texto, imagen, tipoDeImagen, plantilla, yaPublicado, TipoDeRankingSemanal.GENERAL);
        }
    }
}
