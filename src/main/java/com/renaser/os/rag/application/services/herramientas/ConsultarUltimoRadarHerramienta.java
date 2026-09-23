package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.CheckInRadar;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.RespuestasRadar;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@code consultar_ultimo_radar} (R0, solo lectura, 2026-09-23): el ultimo Codigo Renaser del
 * aprendiz, cuando lo hizo (en su hora local) y si ya ocupa la hora en curso. Es lo que el
 * acompanante mira antes de proponer uno nuevo.
 *
 * <p>Lee de {@code ConsultarUltimoRadarUseCase} (el de {@code GET /api/v1/radar/latest}) via
 * {@code habits.api.DiarioYRadarPort}, con sus guardas (suspendida, programa sin activar). La app
 * solo recibe la hora de ese endpoint; aca tambien van las respuestas, que son de la propia persona
 * (las mismas que le muestra {@code GET /api/v1/radar/history}).
 */
@Component
public class ConsultarUltimoRadarHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_ultimo_radar";

    static final String SI_NO_AUTORIZADO = "La cuenta esta suspendida o todavia no tiene el Codigo Renaser "
            + "habilitado (es de quienes estan cursando el programa).";

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve el ultimo Codigo Renaser (check-in: que hace, piensa, siente, su energia del 1 al 10 y que "
                    + "evita) del aprendiz, a que hora lo hizo y si ya registro el de esta hora (es uno por hora). "
                    + "Usala cuando pregunte por su ultimo Codigo Renaser y SIEMPRE antes de proponer uno nuevo. "
                    + "Es contenido personal: no lo repitas entero si no lo pidio.");

    private final DiarioYRadarDelAprendizPort radarPort;

    public ConsultarUltimoRadarHerramienta(DiarioYRadarDelAprendizPort radarPort) {
        this.radarPort = radarPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<CheckInRadar> ultimo;
        try {
            ultimo = radarPort.ultimoCheckInRadar(actorId);
        } catch (RuntimeException rechazo) {
            return TextoDelDiarioYRadar.rechazo(NOMBRE, rechazo, SI_NO_AUTORIZADO);
        }
        return ResultadoHerramienta.exito(ultimo.map(ConsultarUltimoRadarHerramienta::textoDe)
                .orElse("Todavia no registro ningun Codigo Renaser."));
    }

    static String textoDe(CheckInRadar ultimo) {
        RespuestasRadar respuestas = ultimo.respuestas();
        String estaHora = ultimo.deEstaHora()
                ? " Ya registro el de esta hora: uno por hora, si registra otro ahora no se guarda."
                : " Todavia no registro el de esta hora.";
        return "Ultimo Codigo Renaser: " + TextoDelDiarioYRadar.momento(ultimo.registradoEn())
                + " (hora de la persona)." + estaHora
                + "\nQue hacia: " + respuestas.queHago()
                + "\nQue pensaba: " + respuestas.quePienso()
                + "\nQue sentia: " + respuestas.queSiento()
                + "\nEnergia: " + respuestas.nivelEnergia() + "/" + DiarioYRadarDelAprendizPort.RADAR_ENERGIA_MAXIMA
                + "\nQue evitaba: " + respuestas.queEvito();
    }
}
