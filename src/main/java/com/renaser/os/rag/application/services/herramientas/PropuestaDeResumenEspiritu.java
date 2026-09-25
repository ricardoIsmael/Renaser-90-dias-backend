package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.AudioDeHoy;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.EspirituDeHoy;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.EstadoEspiritu;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code proponer_resumen_espiritu} (R2, propuesta, 2026-09-23): enviar el resumen del audio de
 * Espiritu de hoy. No escribe: deja una propuesta y la persona confirma con el boton. La escritura
 * es {@link EntregarResumenEspirituConfirmable}, que llama al mismo caso de uso que
 * {@code POST /spirit-audio/submit}.
 *
 * <p><b>El resumen es de la persona.</b> La descripcion le prohibe al modelo redactarlo, y el
 * texto completo va en el resumen de la propuesta: la persona ve exactamente lo que se enviaria
 * antes de tocar Confirmar.
 *
 * <p><b>Da puntos, y lo dice:</b> entregar completa ademas el habito "Pastilla Renacer" de hoy
 * ({@code EspirituService.reflejarEnPastillaRenacer}); el resumen de la propuesta dice cuantos
 * paga ahora, con el numero que calcula {@code habits}.
 *
 * <p>El dia del audio NO lo elige el modelo: sale del audio de hoy que devuelve {@code habits}.
 * Solo se propone con el audio PENDIENTE y sin entregar; una entrega fuera de plazo ya hecha no se
 * reemplaza desde el chat.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeResumenEspiritu implements HerramientaAgente {

    public static final String NOMBRE = "proponer_resumen_espiritu";
    public static final String ARGUMENTO_RESUMEN = "resumen";
    /** Lo agrega la herramienta al guardar la propuesta; el modelo no lo manda. */
    public static final String ARGUMENTO_DIA = "dia";

    /** Cuanto del texto se muestra junto a los botones; el texto completo va en los argumentos. */
    private static final int LARGO_VISIBLE = 280;

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeResumenEspiritu.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone enviar el resumen del audio de Espiritu de hoy. NO lo envia: deja una propuesta y la persona "
                    + "tiene que tocar Confirmar en la app. Al confirmarse tambien marca 'Pastilla Renacer' de hoy "
                    + "y suma sus puntos. El resumen tiene que ser el texto que la persona escribio con SUS "
                    + "palabras: no lo redactes, no lo completes ni lo mejores. Si no te lo dio, pideselo. Consulta "
                    + "antes consultar_espiritu_de_hoy.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_RESUMEN, TipoParametroHerramienta.TEXTO,
                    "El resumen, tal cual lo escribio la persona.")));

    private final EnfoqueDiarioDelAprendizPort enfoquePort;
    private final ProponerAccionUseCase proponerAccion;
    private final Clock clock;

    public PropuestaDeResumenEspiritu(EnfoqueDiarioDelAprendizPort enfoquePort, ProponerAccionUseCase proponerAccion,
                                      Clock clock) {
        this.enfoquePort = enfoquePort;
        this.proponerAccion = proponerAccion;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        String resumen = invocacion.argumento(ARGUMENTO_RESUMEN);
        if (resumen == null || resumen.isBlank()) {
            return ResultadoHerramienta.fallo("Falta el resumen: pideselo a la persona con sus palabras.");
        }
        return LecturaDelEnfoque.con(() -> enfoquePort.espirituDeHoy(actorId), "su Espiritu de hoy",
                espiritu -> proponerSiCorresponde(actorId, espiritu, resumen.trim()));
    }

    private ResultadoHerramienta proponerSiCorresponde(UserId actorId, EspirituDeHoy espiritu, String resumen) {
        if (espiritu.estado() != EstadoEspiritu.PENDIENTE) {
            return ResultadoHerramienta.fallo(motivoSinPropuesta(espiritu));
        }
        MomentoDelAprendiz momento = new MomentoDelAprendiz(clock.now(), espiritu.zona());
        String textoResumen = resumenDe(espiritu, resumen, momento);
        try {
            proponerAccion.proponer(actorId, new InvocacionHerramienta(NOMBRE, Map.of(ARGUMENTO_RESUMEN, resumen,
                    ARGUMENTO_DIA, String.valueOf(espiritu.audio().dia()))), textoResumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada(textoResumen, null);
    }

    private static String motivoSinPropuesta(EspirituDeHoy espiritu) {
        return switch (espiritu.estado()) {
            case ANTES_DE_LA_HORA_DE_DESBLOQUEO -> "Todavia no se desbloquea el audio de hoy (se abre a las "
                    + espiritu.horaDesbloqueo() + "): no hay nada que entregar aun.";
            case ENTREGADO_A_TIEMPO, ENTREGADO_FUERA_DE_PLAZO -> "El resumen de hoy ya se envio: no hace falta "
                    + "mandarlo de nuevo.";
            case PERDIDO -> "El audio de hoy ya figura como perdido: no se puede entregar.";
            default -> "Hoy no tiene un audio de Espiritu desbloqueado: no hay resumen que entregar.";
        };
    }

    /** Lo que ve la persona junto a los botones: que texto se envia, si llega a tiempo y que puntos suma. */
    static String resumenDe(EspirituDeHoy espiritu, String resumen, MomentoDelAprendiz momento) {
        AudioDeHoy audio = espiritu.audio();
        String plazo = momento.ahora().isAfter(audio.fechaLimite())
                ? ", FUERA DE PLAZO (vencio a las " + momento.horaDe(audio.fechaLimite()) + "): queda guardado pero "
                        + "no cuenta como a tiempo"
                : ", a tiempo (vence a las " + momento.horaDe(audio.fechaLimite()) + ")";
        return "Enviar tu resumen de Espiritu del audio " + audio.dia()
                + (audio.titulo() == null ? "" : " '" + audio.titulo() + "'") + plazo + ": \"" + visible(resumen)
                + "\"" + pastillaRenacer(espiritu.puntosPastillaRenacer());
    }

    private static String pastillaRenacer(Integer puntos) {
        if (puntos == null) {
            return "";
        }
        return ". Tambien marca 'Pastilla Renacer' de hoy como hecha"
                + (puntos > 0 ? " (+" + puntos + " puntos si lo confirmas ahora)" : "");
    }

    private static String visible(String resumen) {
        return resumen.length() <= LARGO_VISIBLE ? resumen : resumen.substring(0, LARGO_VISIBLE) + "...";
    }
}
