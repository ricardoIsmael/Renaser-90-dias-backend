package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.agenda.AgendaSemanalPort;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code consultar_mi_agenda} (D-161, R0): las horas ocupadas que la persona guardo, por dia de la
 * semana. Sirve para sugerir sin volver a preguntar, y para que la persona vea que se guardo.
 */
@Component
public class ConsultarMiAgendaHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_mi_agenda";

    private static final Logger log = LoggerFactory.getLogger(ConsultarMiAgendaHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Devuelve las horas en que la persona guardo que suele estar ocupada, por dia de la semana. Usala "
                    + "antes de sugerirle horarios, o si pregunta que tiene guardado.",
            List.of());

    private final AgendaSemanalPort agendaPort;

    public ConsultarMiAgendaHerramienta(AgendaSemanalPort agendaPort) {
        this.agendaPort = agendaPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        try {
            return ResultadoHerramienta.exito("Horas ocupadas guardadas: " + agendaPort.de(actorId).texto() + ".");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer la agenda ({})", NOMBRE, falla.getClass().getSimpleName());
            return ResultadoHerramienta.fallo("No pude consultar sus horas ocupadas en este momento.");
        }
    }
}
