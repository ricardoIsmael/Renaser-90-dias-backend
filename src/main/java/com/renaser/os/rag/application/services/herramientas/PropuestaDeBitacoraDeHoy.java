package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.BitacoraDeHoy;
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

/**
 * {@code proponer_bitacora_de_hoy} (R2, propuesta, 2026-09-23): escribir la Bitacora Nocturna de hoy
 * con lo que la persona le conto al acompanante. No escribe: deja una propuesta y la persona
 * confirma con el boton. La escritura es {@link BitacoraDeHoyConfirmable}, que llama al mismo caso
 * de uso que {@code PUT /api/v1/journal/today} (solo texto; el audio no pasa por aca).
 *
 * <p><b>Es un upsert que pisa.</b> Si ya hay bitacora hoy, el resumen lo dice y muestra lo que hay
 * y lo que quedaria (recortado para mostrar; se guarda el texto completo). El audio que tuviera se
 * conserva: el caso de uso solo reemplaza el texto.
 *
 * <p>La fecha de hoy (en la zona del participante, resuelta por {@code habits}) se guarda en la
 * propuesta: si al confirmar ya es otro dia, no se escribe la bitacora del dia equivocado.
 *
 * <p>El texto es personal: no se loguea. Solo existe con
 * {@code renaser.ia.acompanante.confirmacion-con-botones} prendido.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeBitacoraDeHoy implements HerramientaAgente {

    public static final String NOMBRE = "proponer_bitacora_de_hoy";
    public static final String ARGUMENTO_TEXTO = "texto";
    /** Lo agrega el codigo al guardar la propuesta; el modelo no lo manda. */
    static final String ARGUMENTO_FECHA = "fecha";

    /** Solo para el resumen junto a los botones. */
    private static final int MAXIMO_A_MOSTRAR = 280;

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeBitacoraDeHoy.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone escribir la Bitacora Nocturna de HOY con lo que la persona te conto. NO la guarda: deja una "
                    + "propuesta y la persona tiene que tocar Confirmar en la app. Nunca digas que ya quedo "
                    + "guardada. Usa SUS palabras: no inventes, no completes, no resumas ni embellezcas lo que "
                    + "dijo; si no te dicto nada todavia, preguntale que quiere escribir. Si ya escribio una hoy, "
                    + "confirmar la REEMPLAZA: consulta antes consultar_bitacora_de_hoy y, si quiere sumar, incluye "
                    + "el texto anterior.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_TEXTO, TipoParametroHerramienta.TEXTO,
                    "El texto completo de la bitacora, con las palabras de la persona.")));

    private final DiarioYRadarDelAprendizPort diarioPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeBitacoraDeHoy(DiarioYRadarDelAprendizPort diarioPort, ProponerAccionUseCase proponerAccion) {
        this.diarioPort = diarioPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        String texto = invocacion.argumento(ARGUMENTO_TEXTO);
        if (texto == null || texto.isBlank()) {
            return ResultadoHerramienta.fallo("Falta el texto de la bitacora: preguntale que quiere escribir.");
        }
        BitacoraDeHoy hoy;
        try {
            hoy = diarioPort.bitacoraDeHoy(actorId);
        } catch (RuntimeException rechazo) {
            return TextoDelDiarioYRadar.rechazo(NOMBRE, rechazo,
                    "La cuenta esta suspendida: no puede escribir su bitacora.");
        }
        return proponer(actorId, hoy, texto.strip());
    }

    private ResultadoHerramienta proponer(UserId actorId, BitacoraDeHoy hoy, String texto) {
        if (hoy.existe() && texto.equals(textoDe(hoy))) {
            return ResultadoHerramienta.fallo("Su bitacora de hoy ya dice exactamente eso: no hay nada que cambiar.");
        }
        String resumen = resumenDe(hoy, texto);
        try {
            proponerAccion.proponer(actorId, new InvocacionHerramienta(NOMBRE,
                    Map.of(ARGUMENTO_TEXTO, texto, ARGUMENTO_FECHA, hoy.fecha().toString())), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}: {}", NOMBRE, falla.getClass().getSimpleName());
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada(resumen, hoy.existe()
                ? "Ya habia una bitacora hoy: al confirmar se REEMPLAZA su texto. Diselo con claridad." : null);
    }

    /** Lo que ve la persona junto a los botones: que dia, si pisa algo, y el texto (recortado). */
    static String resumenDe(BitacoraDeHoy hoy, String texto) {
        String dia = "tu Bitacora Nocturna de hoy (" + FechaDelPlan.legible(hoy.fecha()) + ")";
        String nuevo = "«" + TextoDelDiarioYRadar.recortado(texto, MAXIMO_A_MOSTRAR) + "»";
        String audio = hoy.tieneAudio() ? " Tu audio se conserva." : "";
        if (!hoy.existe()) {
            return "Escribir " + dia + ": " + nuevo;
        }
        if (textoDe(hoy).isEmpty()) {
            return "Agregar texto a " + dia + ": " + nuevo + "." + audio;
        }
        return "Reemplazar " + dia + ". Ahora dice: «" + TextoDelDiarioYRadar.recortado(hoy.texto(), MAXIMO_A_MOSTRAR)
                + "». Quedaria: " + nuevo + "." + audio;
    }

    private static String textoDe(BitacoraDeHoy hoy) {
        return hoy.texto() == null ? "" : hoy.texto().strip();
    }
}
