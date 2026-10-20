package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.domain.model.cumplimiento.ConteoDeLaLista;
import com.renaser.os.leadership.domain.model.cumplimiento.PersonaDeLaLista;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Las personas de un grupo con mentor, con el color de la semana y el porcentaje cumplido, para el líder de mentores
 * (y Administración y Alquimista) (spec 005, FR-001, FR-002, FR-006 y FR-007).
 *
 * <p>Es la tabla del semáforo del grupo, la misma que ve el mentor y el administrador, con el orden del líder y con
 * quien lleva su propio programa en el grupo. No registra nada: la consulta que se registra es la de una persona.
 */
public interface ConsultarGrupoDelLiderUseCase {

    GrupoDelLider grupoDe(ConsultaGrupoDelLider consulta);

    /**
     * @param semanaHasta null = la ventana vigente del resumen por grupos (los 7 días que terminan ayer); si viene, un
     *                    viernes que ya llegó: la semana de sábado a viernes que termina ese día
     */
    record ConsultaGrupoDelLider(UserId actorId, UUID grupoId, LocalDate semanaHasta) {

        public ConsultaGrupoDelLider {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
        }
    }

    /**
     * @param cerrada true solo si la ventana termina en una semana ya cerrada el sábado (sale del calendario)
     * @param resumen cuántos hay en cada color; no cuenta a quien no se mide
     */
    record GrupoDelLider(UUID grupoId, String grupoNombre, LocalDate desde, LocalDate hasta, boolean cerrada,
                         ConteoDeLaLista resumen, List<PersonaDeLaLista> personas) {

        public GrupoDelLider {
            personas = personas == null ? List.of() : List.copyOf(personas);
        }
    }
}
