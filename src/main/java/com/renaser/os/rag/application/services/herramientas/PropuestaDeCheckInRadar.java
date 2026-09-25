package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.CheckInRadar;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.RespuestasRadar;
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
import java.util.Optional;

/**
 * {@code proponer_check_in_radar} (R2, propuesta, 2026-09-23): registrar un Codigo Renaser con las
 * respuestas que la persona le dio al acompanante. No escribe: deja una propuesta y la persona
 * confirma con el boton. La escritura es {@link CheckInRadarConfirmable}, que llama al mismo caso de
 * uso que {@code POST /api/v1/radar}.
 *
 * <p><b>Uno por hora.</b> {@code habits} devuelve el registro existente si ya hay uno en la hora en
 * curso, sin guardar las respuestas nuevas. Por eso, si la franja ya esta ocupada al proponer, no se
 * ofrece un boton que no guardaria nada; y el resumen avisa que, si se ocupa antes de confirmar, se
 * conserva el anterior. Leer el ultimo tambien corre las guardas del caso de uso (suspendida,
 * programa sin activar) antes de proponer.
 *
 * <p>Las respuestas son personales: no se loguean. Solo existe con
 * {@code renaser.ia.acompanante.confirmacion-con-botones} prendido.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeCheckInRadar implements HerramientaAgente {

    public static final String NOMBRE = "proponer_check_in_radar";

    /** Solo para el resumen junto a los botones. */
    private static final int MAXIMO_A_MOSTRAR = 120;

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeCheckInRadar.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone registrar un Codigo Renaser (check-in) con las respuestas de la persona. NO lo registra: deja "
                    + "una propuesta y la persona tiene que tocar Confirmar en la app. Nunca digas que ya quedo "
                    + "registrado. Las cinco respuestas tienen que ser SUYAS, con sus palabras: preguntale cada una "
                    + "que falte; no inventes, no completes ni deduzcas ninguna, tampoco el nivel de energia. Es uno "
                    + "por hora: consulta antes consultar_ultimo_radar.",
            List.of(ParametroHerramienta.obligatorio(CheckInRadarPedido.ARGUMENTO_QUE_HAGO,
                            TipoParametroHerramienta.TEXTO, "Que esta haciendo, con sus palabras."),
                    ParametroHerramienta.obligatorio(CheckInRadarPedido.ARGUMENTO_QUE_PIENSO,
                            TipoParametroHerramienta.TEXTO, "Que esta pensando, con sus palabras."),
                    ParametroHerramienta.obligatorio(CheckInRadarPedido.ARGUMENTO_QUE_SIENTO,
                            TipoParametroHerramienta.TEXTO, "Que esta sintiendo, con sus palabras."),
                    ParametroHerramienta.obligatorio(CheckInRadarPedido.ARGUMENTO_NIVEL_ENERGIA,
                            TipoParametroHerramienta.ENTERO, "Su nivel de energia, entero del "
                                    + DiarioYRadarDelAprendizPort.RADAR_ENERGIA_MINIMA + " al "
                                    + DiarioYRadarDelAprendizPort.RADAR_ENERGIA_MAXIMA + ", el que ella diga."),
                    ParametroHerramienta.obligatorio(CheckInRadarPedido.ARGUMENTO_QUE_EVITO,
                            TipoParametroHerramienta.TEXTO, "Que esta evitando, con sus palabras.")));

    private final DiarioYRadarDelAprendizPort radarPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeCheckInRadar(DiarioYRadarDelAprendizPort radarPort, ProponerAccionUseCase proponerAccion) {
        this.radarPort = radarPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        CheckInRadarPedido pedido;
        try {
            pedido = CheckInRadarPedido.de(invocacion);
        } catch (PropuestaImposibleException invalida) {
            return ResultadoHerramienta.fallo(invalida.getMessage());
        }
        Optional<CheckInRadar> ultimo;
        try {
            ultimo = radarPort.ultimoCheckInRadar(actorId);
        } catch (RuntimeException rechazo) {
            return TextoDelDiarioYRadar.rechazo(NOMBRE, rechazo, ConsultarUltimoRadarHerramienta.SI_NO_AUTORIZADO);
        }
        if (ultimo.isPresent() && ultimo.get().deEstaHora()) {
            return ResultadoHerramienta.fallo("Ya registro su Codigo Renaser de esta hora (a las "
                    + TextoDelDiarioYRadar.hora(ultimo.get().registradoEn()) + "). Es uno por hora: si registra "
                    + "otro ahora, no se guarda.");
        }
        return proponer(actorId, pedido);
    }

    private ResultadoHerramienta proponer(UserId actorId, CheckInRadarPedido pedido) {
        String resumen = resumenDe(pedido.respuestas());
        try {
            proponerAccion.proponer(actorId, pedido.invocacion(NOMBRE), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}: {}", NOMBRE, falla.getClass().getSimpleName());
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada(resumen, null);
    }

    /** Lo que ve la persona junto a los botones: sus cinco respuestas (recortadas) y el "uno por hora". */
    static String resumenDe(RespuestasRadar respuestas) {
        return "Registrar tu Codigo Renaser de ahora. Hago: " + corto(respuestas.queHago())
                + " · Pienso: " + corto(respuestas.quePienso())
                + " · Siento: " + corto(respuestas.queSiento())
                + " · Energia: " + respuestas.nivelEnergia() + "/" + DiarioYRadarDelAprendizPort.RADAR_ENERGIA_MAXIMA
                + " · Evito: " + corto(respuestas.queEvito())
                + ". Es uno por hora: si antes de confirmar ya registraste otro en esta misma hora, se conserva "
                + "ese y este no se guarda.";
    }

    private static String corto(String texto) {
        return "«" + TextoDelDiarioYRadar.recortado(texto, MAXIMO_A_MOSTRAR) + "»";
    }
}
