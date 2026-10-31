package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarPersonasDelProgramaUseCase;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La lista de todos los aprendices que se muestran para el líder, Administración y Alquimista (spec 021): la puerta de
 * entrada a la ficha de una persona sin pasar por un grupo.
 *
 * <p>Autoriza primero ({@link AccesoDeLiderazgo}: MENTOR_LEAD, ADMIN o ALCHEMIST con la cuenta activa) y lee después.
 * No registra nada: abrir una lista no es abrir la ficha de nadie, y el registro de consulta (spec 005, FR-016) lo deja
 * cada ficha que se abre. Nunca devuelve el correo.
 */
@Service
public class PersonasDelProgramaService implements ConsultarPersonasDelProgramaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelPrograma personas;
    private final Clock clock;

    PersonasDelProgramaService(AccesoDeLiderazgo acceso, PersonasDelPrograma personas, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDePersonas listar(ConsultaPersonasDelPrograma consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        return personas.listar(consulta.busqueda(), consulta.pagina(), consulta.tamano(), clock.now());
    }
}
