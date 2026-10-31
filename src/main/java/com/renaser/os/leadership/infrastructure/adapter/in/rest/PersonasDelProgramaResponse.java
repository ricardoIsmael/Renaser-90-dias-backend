package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarPersonasDelProgramaUseCase.PaginaDePersonas;
import com.renaser.os.leadership.application.ports.in.ConsultarPersonasDelProgramaUseCase.PersonaDelPrograma;

import java.util.List;
import java.util.UUID;

/**
 * La lista de aprendices del líder (spec 021), en el JSON de {@code specs/021-lider-ve-a-todos/spec.md}. Mapeo a mano y
 * no automático (CLAUDE.MD §5.4.5): un campo nuevo del dominio no puede aparecer solo en la respuesta, y en particular
 * el correo no tiene por dónde salir.
 *
 * @param total cuántas personas coinciden con la búsqueda, no solo las de esta página
 */
public record PersonasDelProgramaResponse(List<PersonaResponse> items, int page, int size, long total) {

    public static PersonasDelProgramaResponse from(PaginaDePersonas pagina) {
        return new PersonasDelProgramaResponse(pagina.items().stream().map(PersonaResponse::from).toList(),
                pagina.pagina(), pagina.tamano(), pagina.total());
    }

    /**
     * @param photoPath    la foto de la persona (la misma que los demás listados llaman {@code avatarUrl}); null si no tiene
     * @param grupoId      el grupo con que se la mira; null si no está en ninguno
     * @param mentorNombre null si el grupo no tiene mentor o no hay grupo
     * @param diaPrograma  null si todavía no eligió su Día 1
     */
    public record PersonaResponse(UUID personId, String nombre, String photoPath, UUID grupoId, String grupoNombre,
                                  String mentorNombre, Integer diaPrograma) {

        static PersonaResponse from(PersonaDelPrograma persona) {
            return new PersonaResponse(persona.personaId(), persona.nombre(), persona.avatarUrl(), persona.grupoId(),
                    persona.grupoNombre(), persona.mentorNombre(), persona.diaPrograma());
        }
    }
}
