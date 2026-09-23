package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.ClaseDeHoy;
import com.renaser.os.rag.application.services.herramientas.PropuestaDeEntregarClaseDeHoy.EntregaPedida;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * La escritura de {@code proponer_entregar_clase_de_hoy}, que solo corre cuando la persona
 * confirma con el boton (D-153). La propuso {@link PropuestaDeEntregarClaseDeHoy}.
 *
 * <p>Delega en {@code academy} ({@code CompletarClaseDiariaUseCase}, el del {@code POST} de la
 * app), que vuelve a exigir que la leccion sea la de hoy, el largo del resumen, cuenta activa, y
 * cierra el habito en {@code habits} con sus propias guardas.
 *
 * <p><b>Antes, una guarda propia: que la clase de hoy siga siendo la propuesta.</b> Las secciones
 * del catalogo cubren rangos de dias, asi que la misma leccion puede ser la clase de dos dias
 * seguidos: si la persona confirma pasada la medianoche, {@code academy} aceptaria la leccion y el
 * resumen cerraria el habito del dia SIGUIENTE. Se compara el dia de programa guardado con el de
 * ahora y, si cambio, no se entrega nada.
 */
@Component
public class EntregarClaseDeHoyConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(EntregarClaseDeHoyConfirmable.class);

    private final ClaseDiariaDelAprendizPort claseDiaria;

    public EntregarClaseDeHoyConfirmable(ClaseDiariaDelAprendizPort claseDiaria) {
        this.claseDiaria = claseDiaria;
    }

    @Override
    public String herramienta() {
        return PropuestaDeEntregarClaseDeHoy.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        EntregaPedida entrega;
        try {
            entrega = EntregaPedida.de(invocacion);
        } catch (PropuestaImposibleException guardadaInvalida) {
            return ResultadoHerramienta.fallo("La propuesta guardada no es valida; pide la entrega de nuevo.");
        }
        try {
            if (!sigueSiendoLaDeHoy(claseDiaria.claseDeHoy(actorId), entrega)) {
                return ResultadoHerramienta.fallo("No se entrego: la Clase Diaria de hoy ya no es la que se propuso "
                        + "(cambio el dia). Revisa la clase de hoy y, si corresponde, vuelve a proponer la entrega.");
            }
            int puntos = claseDiaria.entregar(actorId, entrega.leccionId(), entrega.resumen());
            return ResultadoHerramienta.exito("Clase Diaria entregada: habito cerrado y leccion marcada como vista. "
                    + "Puntos otorgados: " + puntos + ". Si ya la habia entregado antes hoy, queda la primera "
                    + "entrega y no suma de nuevo.");
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
    }

    private static boolean sigueSiendoLaDeHoy(ClaseDeHoy clase, EntregaPedida entrega) {
        return clase.disponible() && clase.diaPrograma() == entrega.diaPrograma()
                && entrega.leccionId().equals(clase.leccionId());
    }

    /** Por TIPO de excepcion; el mensaje crudo va al log, nunca al modelo. */
    private ResultadoHerramienta traducir(RuntimeException rechazo) {
        log.info("[rag] {} rechazada por academy al confirmar: {}", herramienta(), rechazo.toString());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case NotAuthorizedException sinAcceso ->
                    "No se pudo: la cuenta no esta activa, o esa ya no es la Clase Diaria de hoy.";
            case NoSuchElementException sinRegistro ->
                    "No se pudo: todavia no esta generado el habito de la Clase Diaria de hoy. Puede intentarlo "
                            + "mas tarde desde la app.";
            case IllegalStateException estado ->
                    "No se pudo: hoy no hay una Clase Diaria que se pueda entregar, o ya quedo cerrada sin resumen y "
                            + "no admite uno nuevo.";
            default -> "No pude entregar la Clase Diaria en este momento. Puede hacerlo desde la app.";
        });
    }
}
