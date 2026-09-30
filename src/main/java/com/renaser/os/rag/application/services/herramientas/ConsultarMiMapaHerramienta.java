package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.mapa.ConsultarMapaDeRenacimientoPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@code consultar_mi_mapa} (R0, solo lectura, D-233): el Mapa de Renacimiento que la persona armo el
 * dia 7 — su prioridad, sus tres objetivos con el porque, los hitos 30/60/90 marcando el proximo, sus
 * acciones y su protocolo de retorno.
 *
 * <p>Es la fuente para conectar un habito o una accion con lo que la persona se propuso, con SUS
 * palabras. No decide nada: el Mapa lo lee {@code onboarding} y el dia de hoy (derivado de las fechas en
 * su zona, regla 02) lo da el mismo puerto que la situacion del turno, asi que el "dia N" del prompt y
 * el de esta herramienta no pueden diferir. El texto lo arma {@link TextoDelMapa}.
 */
@Component
public class ConsultarMiMapaHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_mi_mapa";

    private static final Logger log = LoggerFactory.getLogger(ConsultarMiMapaHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve el Mapa de Renacimiento que la persona armo el dia 7: el area que prioriza, sus tres "
                    + "objetivos de 90 dias (salud, negocio y dinero, relaciones) con la meta redactada, como "
                    + "esta hoy y a donde quiere llegar al dia 90, con que lo evidencia y POR QUE lo eligio; "
                    + "sus hitos de los dias 30, 60 y 90 marcando cual es el proximo y cuantos dias faltan; "
                    + "las acciones que eligio para llegar; y su protocolo de retorno (su accion minima para "
                    + "volver en menos de 24 h si se cae). Usala cuando pregunte por sus objetivos o metas, "
                    + "como va, para que hace un habito o una accion, cual es su proximo hito, o cuando se "
                    + "desanima o quiere dejar. Si no tiene Mapa, lo dice.");

    private final ConsultarMapaDeRenacimientoPort mapaPort;
    private final ConsultarSituacionDelAprendizPort situacionPort;

    public ConsultarMiMapaHerramienta(ConsultarMapaDeRenacimientoPort mapaPort,
                                      ConsultarSituacionDelAprendizPort situacionPort) {
        this.mapaPort = mapaPort;
        this.situacionPort = situacionPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        MapaDeLaPersona mapa;
        try {
            mapa = mapaPort.de(actorId);
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer el Mapa", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude leer su Mapa de Renacimiento en este momento.");
        }
        return ResultadoHerramienta.exito(TextoDelMapa.completo(mapa, situacionSiSePuede(actorId)));
    }

    /** Sin dia (no cursa, o no se pudo leer) el Mapa sale igual, sin marcar el proximo hito. */
    private Optional<SituacionDelAprendiz> situacionSiSePuede(UserId actorId) {
        try {
            return situacionPort.de(actorId);
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} sale sin el dia del programa ({})", NOMBRE,
                    falla.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
