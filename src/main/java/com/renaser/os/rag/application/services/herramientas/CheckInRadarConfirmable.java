package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.CheckInRadarRegistrado;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * La escritura de {@code proponer_check_in_radar}, que solo corre cuando la persona confirma con el
 * boton (2026-09-23). La propuso {@link PropuestaDeCheckInRadar}.
 *
 * <p>Delega en {@code RegistrarCheckInRadarUseCase} (via {@code habits.api}), el mismo de
 * {@code POST /api/v1/radar}, que vuelve a correr todas sus guardas. Si entre proponer y confirmar
 * se registro otro en la misma hora (desde la app, por ejemplo), {@code habits} devuelve ese y no
 * guarda estas respuestas: se informa como {@code Fallo}, porque lo que la persona confirmo NO
 * quedo guardado.
 *
 * <p>Sin condicion de flag: una propuesta ya guardada tiene que poder confirmarse aunque el flag se
 * apague despues. No loguea las respuestas.
 */
@Component
public class CheckInRadarConfirmable implements AccionConfirmable {

    private final DiarioYRadarDelAprendizPort radarPort;

    public CheckInRadarConfirmable(DiarioYRadarDelAprendizPort radarPort) {
        this.radarPort = radarPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeCheckInRadar.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        CheckInRadarPedido pedido;
        try {
            pedido = CheckInRadarPedido.de(invocacion);
        } catch (PropuestaImposibleException guardadaInvalida) {
            return ResultadoHerramienta.fallo("La propuesta guardada no es valida: no se registro nada.");
        }
        CheckInRadarRegistrado registrado;
        try {
            registrado = radarPort.registrarCheckInRadar(actorId, pedido.respuestas());
        } catch (RuntimeException rechazo) {
            return TextoDelDiarioYRadar.rechazo(herramienta(), rechazo, ConsultarUltimoRadarHerramienta.SI_NO_AUTORIZADO);
        }
        String hora = TextoDelDiarioYRadar.hora(registrado.registradoEn());
        if (registrado.yaExistia()) {
            return ResultadoHerramienta.fallo("Ya habia un Codigo Renaser registrado en esta hora (a las " + hora
                    + "): se conservo ese y estas respuestas no se guardaron.");
        }
        return ResultadoHerramienta.exito("Codigo Renaser registrado a las " + hora + ".");
    }
}
