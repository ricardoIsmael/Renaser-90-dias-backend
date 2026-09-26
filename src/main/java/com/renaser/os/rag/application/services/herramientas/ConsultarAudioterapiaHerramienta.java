package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.audioterapia.AudioterapiaSemanalPort;
import com.renaser.os.rag.application.ports.out.audioterapia.AudioterapiaSemanalPort.AudioterapiaDeHoy;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * {@code consultar_audioterapia} (D-171): que audio de la Audioterapia semanal le toca a la persona
 * y si ya la entrego hoy. Antes no habia herramienta: el acompanante no sabia ni el titulo del audio
 * de la semana. Solo lectura, sin flag, como {@code consultar_espiritu_de_hoy}. No devuelve la URL:
 * el audio se escucha en la app.
 */
@Component
public class ConsultarAudioterapiaHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_audioterapia";

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve la Audioterapia semanal de la persona: que audio le toca esta semana (numero y titulo), "
                    + "desde que dia del programa cambia, y si hoy ya la entrego. Usala cuando pregunte por la "
                    + "audioterapia o antes de proponer_resumen_audioterapia. No es la Pastilla Renacer (esa es "
                    + "el audio de Espiritu de cada dia).");

    private final AudioterapiaSemanalPort audioterapiaPort;

    public ConsultarAudioterapiaHerramienta(AudioterapiaSemanalPort audioterapiaPort) {
        this.audioterapiaPort = audioterapiaPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        return LecturaDelEnfoque.con(() -> audioterapiaPort.deHoyDe(actorId), "su Audioterapia",
                deHoy -> ResultadoHerramienta.exito(textoDe(deHoy)));
    }

    static String textoDe(AudioterapiaDeHoy deHoy) {
        if (!deHoy.hayAudio()) {
            return "Todavia no hay Audioterapia para esta semana: no se desbloqueo o aun no se cargo el audio.";
        }
        String audio = "Audioterapia de la semana " + deHoy.semana()
                + (deHoy.titulo() == null ? "" : ": '" + deHoy.titulo() + "'")
                + (deHoy.diaSiguienteCambio() == null ? "" : ". Cambia de audio el dia "
                        + deHoy.diaSiguienteCambio() + " del programa") + ".";
        return audio + " " + estadoDeHoy(deHoy);
    }

    private static String estadoDeHoy(AudioterapiaDeHoy deHoy) {
        if (deHoy.registroId() == null || deHoy.estadoRegistro() == null) {
            return "Hoy no le toca entregarla.";
        }
        return switch (deHoy.estadoRegistro()) {
            case "PENDIENTE", "EN_CURSO" -> "Hoy todavia no la entrego: se entrega contestando dos preguntas "
                    + "despues de escucharla.";
            case "COMPLETADO" -> "Hoy ya la entrego.";
            default -> "La de hoy ya se cerro sin entregar.";
        };
    }
}
