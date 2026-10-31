package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Todos los aprendices que se muestran, estén o no en un grupo, para el líder de mentores, Administración y Alquimista
 * (spec 021, decisión del dueño del 2026-10-31: «el líder de mentores puede ver la ficha de todos los aprendices sin
 * necesidad de estar en grupo»). Es la puerta de entrada a la ficha de una persona sin pasar por un grupo. Solo
 * lectura y no registra nada: lo que se registra es abrir la ficha.
 */
public interface ConsultarPersonasDelProgramaUseCase {

    PaginaDePersonas listar(ConsultaPersonasDelPrograma consulta);

    int TAMANO_POR_DEFECTO = 30;
    int TAMANO_MAXIMO = 100;

    /**
     * @param busqueda filtra por nombre sin tildes ni mayúsculas; null o vacío = todos
     * @param pagina   desde 0
     * @param tamano   de 1 a {@link #TAMANO_MAXIMO}
     */
    record ConsultaPersonasDelPrograma(UserId actorId, String busqueda, int pagina, int tamano) {

        public ConsultaPersonasDelPrograma {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            if (pagina < 0) {
                throw new IllegalArgumentException("page no puede ser negativa");
            }
            if (tamano < 1 || tamano > TAMANO_MAXIMO) {
                throw new IllegalArgumentException("size debe estar entre 1 y " + TAMANO_MAXIMO);
            }
        }
    }

    /**
     * @param grupoId       el grupo con que se la mira (el regular con mentor primero); null si no está en ninguno
     * @param mentorNombre  el mentor de ese grupo; null si no tiene (o si es la recepción)
     * @param diaPrograma   null si todavía no eligió su Día 1
     */
    record PersonaDelPrograma(UUID personaId, String nombre, String avatarUrl, UUID grupoId, String grupoNombre,
                              String mentorNombre, Integer diaPrograma) {
    }

    /** @param total cuántas personas coinciden con la búsqueda, no solo las de esta página */
    record PaginaDePersonas(List<PersonaDelPrograma> items, int pagina, int tamano, long total) {

        public PaginaDePersonas {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
