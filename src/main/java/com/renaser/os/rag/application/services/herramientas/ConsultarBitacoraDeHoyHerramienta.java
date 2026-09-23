package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.BitacoraDeHoy;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * {@code consultar_bitacora_de_hoy} (R0, solo lectura, 2026-09-23): si la Bitacora Nocturna de hoy
 * ya existe y que dice. Es lo que el acompanante mira ANTES de proponer escribirla, porque escribir
 * la de hoy REEMPLAZA la que haya.
 *
 * <p>Lee de {@code ConsultarBitacoraNocturnaUseCase} (el de {@code GET /api/v1/journal/today}) via
 * {@code habits.api.DiarioYRadarPort}. "Hoy" es el de la zona del participante, resuelto alla.
 */
@Component
public class ConsultarBitacoraDeHoyHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_bitacora_de_hoy";

    /** Solo para no inflar el turno del modelo con una entrada muy larga; no es una regla del diario. */
    static final int MAXIMO_PARA_EL_MODELO = 4_000;

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Dice si el aprendiz ya escribio hoy su Bitacora Nocturna (el diario de la noche) y que escribio. "
                    + "Usala cuando pregunte por su bitacora de hoy y SIEMPRE antes de proponer escribirla, porque "
                    + "escribirla reemplaza la que ya haya. Es contenido personal: no lo repitas entero si no lo "
                    + "pidio.");

    private final DiarioYRadarDelAprendizPort diarioPort;

    public ConsultarBitacoraDeHoyHerramienta(DiarioYRadarDelAprendizPort diarioPort) {
        this.diarioPort = diarioPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        BitacoraDeHoy bitacora;
        try {
            bitacora = diarioPort.bitacoraDeHoy(actorId);
        } catch (RuntimeException rechazo) {
            return TextoDelDiarioYRadar.rechazo(NOMBRE, rechazo,
                    "La cuenta esta suspendida: no puedo consultar su bitacora.");
        }
        return ResultadoHerramienta.exito(textoDe(bitacora));
    }

    static String textoDe(BitacoraDeHoy bitacora) {
        String dia = "Bitacora Nocturna de hoy (" + FechaDelPlan.legible(bitacora.fecha()) + "): ";
        if (!bitacora.existe()) {
            return dia + "todavia no la escribio.";
        }
        String audio = bitacora.tieneAudio() ? " Tiene un audio grabado (no puedo escucharlo)." : "";
        if (bitacora.texto() == null || bitacora.texto().isBlank()) {
            return dia + "existe, pero sin texto." + audio;
        }
        return dia + "ya la escribio." + audio + "\nTexto: "
                + TextoDelDiarioYRadar.recortado(bitacora.texto(), MAXIMO_PARA_EL_MODELO);
    }
}
