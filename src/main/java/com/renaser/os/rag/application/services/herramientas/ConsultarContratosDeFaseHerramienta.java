package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.contratos.ConsultarContratosDeFasePort;
import com.renaser.os.rag.application.ports.out.contratos.ConsultarContratosDeFasePort.ContratosDeFase;
import com.renaser.os.rag.application.ports.out.contratos.ConsultarContratosDeFasePort.Fase;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * {@code consultar_contratos_de_fase} (R0, solo lectura, 2026-09-23): que contratos de fase firmo la
 * persona y si hoy le toca firmar uno.
 *
 * <p><b>El acompanante nunca firma.</b> Firmar un contrato de fase es consentimiento legal (R3 de
 * {@code docs/arquitectura/PROPUESTA_ACOMPANANTE_90_DIAS.md} §4.1): no hay herramienta de escritura
 * para esto, ni propuesta con boton, y la descripcion y la salida se lo dicen al modelo en cada
 * llamada para que no ofrezca hacerlo.
 *
 * <p>No decide nada: que fase toca segun el dia de programa y si ya esta firmada lo resuelve
 * {@code phasecontracts} con los casos de uso de {@code GET /phase-contracts} y {@code /pending}.
 */
@Component
public class ConsultarContratosDeFaseHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_contratos_de_fase";

    static final String SOLO_EN_LA_APP = "Firmar un contrato de fase es un consentimiento legal de la persona: se "
            + "firma SOLO ella, desde la app. Tu no puedes firmarlo, aceptarlo ni confirmarlo por ella, aunque te lo "
            + "pida; si quiere firmar, dile que lo haga en la app.";

    private static final Logger log = LoggerFactory.getLogger(ConsultarContratosDeFaseHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve los contratos de fase que la persona ya firmo y si hoy tiene uno pendiente de firma. Usala "
                    + "cuando pregunte por su contrato o pacto de fase, o antes de recordarle que firme. Es solo "
                    + "lectura: NUNCA firmas ni aceptas un contrato por la persona, ni con su permiso; firmar es su "
                    + "consentimiento legal y lo hace ella en la app.");

    private final ConsultarContratosDeFasePort contratosPort;

    public ConsultarContratosDeFaseHerramienta(ConsultarContratosDeFasePort contratosPort) {
        this.contratosPort = contratosPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        try {
            return ResultadoHerramienta.exito(texto(contratosPort.delAprendiz(actorId)));
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("No puedo consultar sus contratos de fase: la cuenta esta suspendida o "
                    + "todavia no tiene su programa activado.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer los contratos", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude consultar sus contratos de fase en este momento.");
        }
    }

    private static String texto(ContratosDeFase contratos) {
        return firmados(contratos) + "\n" + pendiente(contratos) + "\n" + SOLO_EN_LA_APP;
    }

    /** La Fase I no aparece: se acepta en el Pacto del onboarding, no como contrato de fase ({@code ContratoFase}). */
    private static String firmados(ContratosDeFase contratos) {
        String nota = " (La Fase I se acepta en el Pacto del onboarding y no figura aca.)";
        if (contratos.firmados().isEmpty()) {
            return "Contratos de fase firmados: ninguno todavia." + nota;
        }
        return "Contratos de fase firmados: " + contratos.firmados().stream().map(Fase::etiqueta)
                .collect(Collectors.joining("; ")) + "." + nota;
    }

    private static String pendiente(ContratosDeFase contratos) {
        if (contratos.pendienteHoy() == null) {
            return "Hoy no tiene ningun contrato de fase pendiente de firma.";
        }
        return "Pendiente de firma hoy: " + contratos.pendienteHoy().etiqueta() + ".";
    }
}
