package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.soporte.GestionarTicketsAlMentorPort;
import com.renaser.os.rag.application.ports.out.soporte.GestionarTicketsAlMentorPort.TextosDelTicket;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code proponer_ticket_al_mentor} (R2, propuesta; 2026-09-23): abrirle un ticket a su mentor con
 * los tres textos que pide la app (que lo bloquea, que ya intento, como afecta a su meta SMART).
 *
 * <p><b>Excepcion aprobada por el dueno a "el acompanante no le escribe a terceros"</b>
 * ({@code PROPUESTA_ACOMPANANTE_90_DIAS.md} §4.1 la listaba como R3). Por eso las dos defensas:
 * <ul>
 *   <li>el resumen que ve la persona junto a los botones muestra los tres textos <b>exactamente</b>
 *       como se van a enviar, y dice que van a su mentor. Lo que se guarda en la propuesta es lo que
 *       se envia al confirmar, sin retoques;</li>
 *   <li>la descripcion le exige al modelo usar las palabras de la persona: puede ordenarlas, nunca
 *       inventar contenido.</li>
 * </ul>
 * La escritura es {@link AbrirTicketAlMentorConfirmable}, que llama al mismo caso de uso que
 * {@code POST /api/v1/tickets}; ese caso de uso exige el rol TRAINEE y la cuenta activa al
 * confirmar. Aca solo se valida lo que se puede validar con los textos: que esten y que no pasen el
 * largo que {@code support} acepta.
 *
 * <p>Los textos nunca se loguean. Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones}.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeTicketAlMentor implements HerramientaAgente {

    public static final String NOMBRE = "proponer_ticket_al_mentor";
    public static final String ARGUMENTO_DESCRIPCION = "descripcion_bloqueo";
    public static final String ARGUMENTO_SOLUCIONES = "soluciones_intentadas";
    public static final String ARGUMENTO_IMPACTO = "impacto_meta_smart";

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeTicketAlMentor.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone abrirle un ticket a SU MENTOR con tres textos. NO lo envia: la persona ve los tres textos tal "
                    + "cual y tiene que tocar Confirmar en la app. Nunca digas que ya se envio. Usala solo si la "
                    + "persona pidio escribirle a su mentor o acepto hacerlo. Los tres textos tienen que salir de lo "
                    + "que la persona dijo en esta conversacion: puedes ordenar sus palabras y corregir la "
                    + "ortografia, pero nunca inventes, supongas ni agregues contenido. Si falta alguno de los tres, "
                    + "preguntaselo antes de llamarla.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_DESCRIPCION, TipoParametroHerramienta.TEXTO,
                            "Que la bloquea, en sus palabras."),
                    ParametroHerramienta.obligatorio(ARGUMENTO_SOLUCIONES, TipoParametroHerramienta.TEXTO,
                            "Que ya intento para resolverlo, en sus palabras."),
                    ParametroHerramienta.obligatorio(ARGUMENTO_IMPACTO, TipoParametroHerramienta.TEXTO,
                            "Como afecta a su meta SMART, en sus palabras.")));

    private final GestionarTicketsAlMentorPort ticketsPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeTicketAlMentor(GestionarTicketsAlMentorPort ticketsPort, ProponerAccionUseCase proponerAccion) {
        this.ticketsPort = ticketsPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        TextosDelTicket textos = textosDe(invocacion);
        Optional<String> impedimento = impedimento(textos, ticketsPort.largoMaximoDeCadaTexto());
        if (impedimento.isPresent()) {
            return ResultadoHerramienta.fallo(impedimento.get());
        }
        String resumen = resumenDe(textos);
        try {
            proponerAccion.proponer(actorId, invocacionPara(textos), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}: {}", NOMBRE, falla.getClass().getSimpleName());
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada("abrirle un ticket a su mentor con los tres textos mostrados", null);
    }

    /** Sin espacios de mas en los bordes; el interior, tal cual lo escribio. */
    static TextosDelTicket textosDe(InvocacionHerramienta invocacion) {
        return new TextosDelTicket(limpio(invocacion.argumento(ARGUMENTO_DESCRIPCION)),
                limpio(invocacion.argumento(ARGUMENTO_SOLUCIONES)), limpio(invocacion.argumento(ARGUMENTO_IMPACTO)));
    }

    private static Optional<String> impedimento(TextosDelTicket textos, int largoMaximo) {
        if (textos.descripcionBloqueo().isEmpty() || textos.solucionesIntentadas().isEmpty()
                || textos.impactoMetaSmart().isEmpty()) {
            return Optional.of("Faltan textos: preguntale que la bloquea, que ya intento y como afecta a su meta "
                    + "SMART antes de proponer el ticket.");
        }
        if (textos.descripcionBloqueo().length() > largoMaximo || textos.solucionesIntentadas().length() > largoMaximo
                || textos.impactoMetaSmart().length() > largoMaximo) {
            return Optional.of("Cada texto puede tener hasta " + largoMaximo + " caracteres: acortalo con ella, "
                    + "sin cambiar lo que quiso decir.");
        }
        return Optional.empty();
    }

    /** Lo que ve la persona junto a los botones: a quien va y los tres textos exactos. */
    static String resumenDe(TextosDelTicket textos) {
        return "Enviar este ticket a tu mentor:\n"
                + "Que te bloquea: " + textos.descripcionBloqueo() + "\n"
                + "Que ya intentaste: " + textos.solucionesIntentadas() + "\n"
                + "Como afecta a tu meta SMART: " + textos.impactoMetaSmart();
    }

    private static InvocacionHerramienta invocacionPara(TextosDelTicket textos) {
        return new InvocacionHerramienta(NOMBRE, Map.of(ARGUMENTO_DESCRIPCION, textos.descripcionBloqueo(),
                ARGUMENTO_SOLUCIONES, textos.solucionesIntentadas(), ARGUMENTO_IMPACTO, textos.impactoMetaSmart()));
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.strip();
    }
}
