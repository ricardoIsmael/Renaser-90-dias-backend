package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.domain.model.agenda.AgendaSemanal;
import com.renaser.os.rag.domain.model.agenda.DiasDeSemana;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code proponer_guardar_agenda} (D-161, R2): propone guardar las horas en que la persona suele
 * estar ocupada ciertos dias, o dejarlos libres. NO guarda: la persona confirma con el boton y lo
 * aplica {@link GuardarAgendaConfirmable}. Se valida aca, antes de proponer, para que el boton
 * nunca muestre algo que despues no se pueda guardar.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeGuardarAgenda implements HerramientaAgente {

    public static final String NOMBRE = "proponer_guardar_agenda";
    public static final String ARGUMENTO_DIAS = "dias";
    public static final String ARGUMENTO_OCUPADO = "ocupado";

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeGuardarAgenda.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone guardar las horas en que la persona suele estar ocupada ciertos dias de la semana (trabajo, "
                    + "estudio), para sugerirle horarios sin volver a preguntar. NO lo guarda: la persona confirma "
                    + "con el boton. Reemplaza lo que habia esos dias. Usala solo si la persona te conto su agenda "
                    + "y acepto que la recuerdes; nunca la guardes sin preguntarle.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_DIAS, TipoParametroHerramienta.TEXTO,
                            "Dias de la semana: 'lunes-viernes', 'sabado, domingo', 'fin de semana' o 'todos'."),
                    ParametroHerramienta.obligatorio(ARGUMENTO_OCUPADO, TipoParametroHerramienta.TEXTO,
                            "Tramos HH:mm-HH:mm separados por coma (ej. 09:00-13:00, 14:00-18:00), o 'ninguno' "
                                    + "para dejar esos dias libres.")));

    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeGuardarAgenda(ProponerAccionUseCase proponerAccion) {
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        String diasTexto = invocacion.argumento(ARGUMENTO_DIAS);
        String ocupado = invocacion.argumento(ARGUMENTO_OCUPADO);
        Set<DayOfWeek> dias;
        try {
            dias = DiasDeSemana.leer(diasTexto);
            AgendaSemanal.vacia().conDias(dias, ocupado);
        } catch (IllegalArgumentException mal) {
            return ResultadoHerramienta.fallo(mal.getMessage());
        }
        String resumen = resumenDe(dias, ocupado);
        try {
            proponerAccion.proponer(actorId, new InvocacionHerramienta(NOMBRE,
                    Map.of(ARGUMENTO_DIAS, diasTexto.strip(), ARGUMENTO_OCUPADO, ocupado.strip())), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {} ({})", NOMBRE, falla.getClass().getSimpleName());
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada(resumen, null);
    }

    /** Lo que ve la persona junto a los botones. */
    static String resumenDe(Set<DayOfWeek> dias, String ocupado) {
        String cuales = dias.stream().sorted().map(DiasDeSemana::nombre).collect(Collectors.joining(", "));
        if (ocupado.strip().equalsIgnoreCase("ninguno")) {
            return "Dejar sin horas ocupadas guardadas: " + cuales;
        }
        return "Recordar que estas ocupado/a " + cuales + " de " + AgendaSemanal.vacia()
                .conDias(Set.of(DayOfWeek.MONDAY), ocupado).delDia(DayOfWeek.MONDAY).texto()
                + " (reemplaza lo guardado esos dias)";
    }
}
