package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.BitacoraDeHoy;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * La escritura de {@code proponer_bitacora_de_hoy}, que solo corre cuando la persona confirma con
 * el boton (2026-09-23). La propuso {@link PropuestaDeBitacoraDeHoy}.
 *
 * <p>Delega en {@code EscribirBitacoraNocturnaUseCase} (via {@code habits.api}), el mismo del
 * {@code PUT /api/v1/journal/today}, que vuelve a correr sus guardas.
 *
 * <p><b>Guarda del dia:</b> la bitacora se propone para un dia concreto. La nocturna se escribe de
 * noche y la propuesta vive minutos, asi que confirmarla despues de la medianoche del participante
 * es un caso real: en ese caso no se escribe (seria la del dia siguiente) y se pide de nuevo.
 *
 * <p>Sin condicion de flag: una propuesta ya guardada tiene que poder confirmarse aunque el flag se
 * apague despues. No loguea el texto.
 */
@Component
public class BitacoraDeHoyConfirmable implements AccionConfirmable {

    private static final String SI_NO_AUTORIZADO = "La cuenta esta suspendida: no se guardo la bitacora.";

    private final DiarioYRadarDelAprendizPort diarioPort;

    public BitacoraDeHoyConfirmable(DiarioYRadarDelAprendizPort diarioPort) {
        this.diarioPort = diarioPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeBitacoraDeHoy.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        String texto = invocacion.argumento(PropuestaDeBitacoraDeHoy.ARGUMENTO_TEXTO);
        Optional<LocalDate> fecha = FechaDelPlan.leer(invocacion.argumento(PropuestaDeBitacoraDeHoy.ARGUMENTO_FECHA));
        if (texto == null || texto.isBlank() || fecha.isEmpty()) {
            return ResultadoHerramienta.fallo("La propuesta guardada no es valida: no se guardo nada.");
        }
        try {
            return escribirSiSigueSiendoHoy(actorId, fecha.get(), texto);
        } catch (RuntimeException rechazo) {
            return TextoDelDiarioYRadar.rechazo(herramienta(), rechazo, SI_NO_AUTORIZADO);
        }
    }

    private ResultadoHerramienta escribirSiSigueSiendoHoy(UserId actorId, LocalDate fechaPropuesta, String texto) {
        LocalDate hoy = diarioPort.bitacoraDeHoy(actorId).fecha();
        if (!hoy.equals(fechaPropuesta)) {
            return ResultadoHerramienta.fallo("Ya cambio el dia: esa era la bitacora del "
                    + FechaDelPlan.legible(fechaPropuesta) + " y hoy es " + FechaDelPlan.legible(hoy)
                    + ". No se guardo nada; pidela de nuevo si la quiere para hoy.");
        }
        BitacoraDeHoy guardada = diarioPort.escribirBitacoraDeHoy(actorId, texto);
        return ResultadoHerramienta.exito("Bitacora Nocturna del " + FechaDelPlan.legible(guardada.fecha())
                + " guardada.");
    }
}
