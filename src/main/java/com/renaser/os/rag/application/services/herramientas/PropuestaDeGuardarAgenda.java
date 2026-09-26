package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code proponer_guardar_agenda} (D-161, R2): propone guardar las horas en que la persona suele
 * estar ocupada ciertos dias, o dejarlos libres. NO guarda: la persona confirma con el boton y lo
 * aplica {@link GuardarAgendaConfirmable}. Se valida aca, antes de proponer, para que el boton
 * nunca muestre algo que despues no se pueda guardar.
 *
 * <p><b>Corregido 2026-09-26 (D-177, decision del dueno).</b> La descripcion decia "usala solo si la
 * persona te conto su agenda y acepto que la recuerdes", y el prompt, que nunca se propusiera en la misma
 * respuesta en que la contaba (bateria #61). El dueno eligio lo contrario: dejar la tarjeta y preguntar en
 * la misma frase ("¿Quieres que recuerde tu horario? Te deje la tarjeta para confirmarlo"). La tarjeta es
 * la pregunta: sin Confirmar no se guarda nada.
 *
 * <p><b>Una sola tarjeta de agenda a la vez (E-291).</b> Despues de "estudio los sabados de 8 a 12"
 * quedo la tarjeta; al "si, guardalo" el modelo dejo OTRA con dias inventados ("sabado, domingo"), y
 * el deduplicado de D-176 no la freno porque los argumentos eran distintos. Ahora, si ya hay una
 * pendiente, no se crea otra salvo que el modelo diga con {@value #ARGUMENTO_CAMBIA_LA_PENDIENTE}
 * que la persona pidio cambiarla; si no, se le contesta que la confirme con el boton. Que los dias y
 * las horas salgan de lo que ella dijo no lo puede saber la herramienta: eso lo dice el prompt.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeGuardarAgenda implements HerramientaAgente {

    public static final String NOMBRE = "proponer_guardar_agenda";
    public static final String ARGUMENTO_DIAS = "dias";
    public static final String ARGUMENTO_OCUPADO = "ocupado";
    public static final String ARGUMENTO_CAMBIA_LA_PENDIENTE = "cambia_la_pendiente";

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeGuardarAgenda.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone guardar las horas en que la persona suele estar ocupada ciertos dias de la semana (trabajo, "
                    + "estudio), para sugerirle horarios sin volver a preguntar. NO lo guarda: la persona confirma "
                    + "con el boton. Reemplaza lo que habia esos dias. Usala cuando la persona te cuente su agenda: "
                    + "deja la tarjeta y, en la misma frase, preguntale si quiere que la recuerdes; solo se guarda "
                    + "si confirma. Nunca la guardes sin preguntarle.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_DIAS, TipoParametroHerramienta.TEXTO,
                            "Dias de la semana: 'lunes-viernes', 'sabado, domingo', 'fin de semana' o 'todos'."),
                    ParametroHerramienta.obligatorio(ARGUMENTO_OCUPADO, TipoParametroHerramienta.TEXTO,
                            "Tramos HH:mm-HH:mm separados por coma (ej. 09:00-13:00, 14:00-18:00), o 'ninguno' "
                                    + "para dejar esos dias libres."),
                    new ParametroHerramienta(ARGUMENTO_CAMBIA_LA_PENDIENTE, TipoParametroHerramienta.TEXTO,
                            "'si' SOLO cuando la persona pidio cambiar los dias o las horas de la tarjeta de agenda "
                                    + "que ya tiene pendiente. Un 'si' o un 'guardalo' no es un cambio: omitelo.",
                            false)));

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
        Optional<PropuestaCreada> pendiente = pideCambiarLaPendiente(invocacion) ? Optional.empty()
                : pendienteDeAgenda(actorId);
        if (pendiente.isPresent()) {
            return yaTieneTarjeta(pendiente.get());
        }
        String resumen = resumenDe(dias, ocupado);
        PropuestaCreada creada;
        try {
            creada = proponerAccion.proponer(actorId, new InvocacionHerramienta(NOMBRE,
                    Map.of(ARGUMENTO_DIAS, diasTexto.strip(), ARGUMENTO_OCUPADO, ocupado.strip())), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {} ({})", NOMBRE, falla.getClass().getSimpleName());
            return AvisoDePropuesta.noSePudoPreparar();
        }
        if (AvisoDePropuesta.yaEstaba(creada)) {
            return AvisoDePropuesta.yaEstabaPendiente(creada);
        }
        return AvisoDePropuesta.creada(resumen, null);
    }

    private static boolean pideCambiarLaPendiente(InvocacionHerramienta invocacion) {
        String valor = invocacion.argumento(ARGUMENTO_CAMBIA_LA_PENDIENTE);
        return valor != null && Set.of("si", "sí", "true").contains(valor.strip().toLowerCase(java.util.Locale.ROOT));
    }

    /** Si no se puede leer, se propone igual: una tarjeta de mas es mejor que no poder proponer. */
    private Optional<PropuestaCreada> pendienteDeAgenda(UserId actorId) {
        try {
            return proponerAccion.pendienteDe(actorId, NOMBRE);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo leer la agenda pendiente ({})", falla.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static ResultadoHerramienta yaTieneTarjeta(PropuestaCreada pendiente) {
        return ResultadoHerramienta.exito("Ya tiene una tarjeta de agenda pendiente (" + pendiente.resumen()
                + "): no se creo otra. TODAVIA NO esta guardado. Dile en una frase que la confirme con el boton "
                + "de esa tarjeta, sin anunciar una nueva. Solo si la persona pidio cambiar los dias o las horas, "
                + "vuelve a llamar con " + ARGUMENTO_CAMBIA_LA_PENDIENTE + "='si' y exactamente lo que ella dijo.");
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
