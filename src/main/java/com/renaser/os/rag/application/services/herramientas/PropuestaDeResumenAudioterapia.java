package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.audioterapia.AudioterapiaSemanalPort;
import com.renaser.os.rag.application.ports.out.audioterapia.AudioterapiaSemanalPort.AudioterapiaDeHoy;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * {@code proponer_resumen_audioterapia} (D-171): entregar la Audioterapia semanal de hoy con las dos
 * respuestas de la persona. No escribe: deja una propuesta y la persona confirma con el boton. La
 * escritura es {@link EntregarResumenAudioterapiaConfirmable}, que hace lo mismo que la app: las
 * respuestas como evidencia de TEXTO y el registro de hoy completado.
 *
 * <p><b>No es la Pastilla Renacer.</b> Esa se entrega con {@code proponer_resumen_espiritu}, que
 * pasa por {@code /spirit-audio/submit} y completa la Pastilla; usarla aca cerraria el habito
 * equivocado.
 *
 * <p>Las preguntas y el formato son los mismos que los de la Pastilla ({@link PreguntasDelAudio}):
 * la app usa el mismo modal para los dos audios. Se guarda el {@code registro_id} de hoy junto al
 * texto: si la persona confirma pasada su medianoche, {@code habits} lo rechaza en vez de cerrar el
 * registro de otro dia.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeResumenAudioterapia implements HerramientaAgente {

    public static final String NOMBRE = "proponer_resumen_audioterapia";
    /** Los dos los agrega la herramienta al guardar la propuesta; el modelo no los manda. */
    public static final String ARGUMENTO_TEXTO = "texto";
    public static final String ARGUMENTO_REGISTRO_ID = "registro_id";

    private static final int LARGO_VISIBLE = 600;

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeResumenAudioterapia.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone entregar la Audioterapia semanal de hoy con las respuestas de la persona. NO la entrega: "
                    + "deja una propuesta y la persona tiene que tocar Confirmar en la app. Al confirmarse, la "
                    + "Audioterapia de hoy queda hecha. No es la Pastilla Renacer. Consulta antes "
                    + "consultar_audioterapia. " + PreguntasDelAudio.COMO_PREGUNTAR,
            PreguntasDelAudio.PARAMETROS);

    private final AudioterapiaSemanalPort audioterapiaPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeResumenAudioterapia(AudioterapiaSemanalPort audioterapiaPort,
                                          ProponerAccionUseCase proponerAccion) {
        this.audioterapiaPort = audioterapiaPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<String> texto = PreguntasDelAudio.textoDe(invocacion);
        if (texto.isEmpty()) {
            return ResultadoHerramienta.fallo("Falta una de las dos respuestas: preguntasela a la persona y pasala "
                    + "con sus palabras.");
        }
        return LecturaDelEnfoque.con(() -> audioterapiaPort.deHoyDe(actorId), "su Audioterapia",
                deHoy -> proponerSiCorresponde(actorId, deHoy, texto.get()));
    }

    private ResultadoHerramienta proponerSiCorresponde(UserId actorId, AudioterapiaDeHoy deHoy, String texto) {
        Optional<String> motivo = motivoSinPropuesta(deHoy);
        if (motivo.isPresent()) {
            return ResultadoHerramienta.fallo(motivo.get());
        }
        String resumen = resumenDe(deHoy, texto);
        try {
            proponerAccion.proponer(actorId, new InvocacionHerramienta(NOMBRE, Map.of(ARGUMENTO_TEXTO, texto,
                    ARGUMENTO_REGISTRO_ID, deHoy.registroId().toString())), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada(resumen, null);
    }

    static Optional<String> motivoSinPropuesta(AudioterapiaDeHoy deHoy) {
        if (!deHoy.hayAudio()) {
            return Optional.of("Esta semana todavia no hay Audioterapia: no hay nada que entregar.");
        }
        if (deHoy.registroId() == null) {
            return Optional.of("Hoy no le toca entregar la Audioterapia.");
        }
        if ("COMPLETADO".equals(deHoy.estadoRegistro())) {
            return Optional.of("La Audioterapia de hoy ya esta entregada: no hace falta mandarla de nuevo.");
        }
        if (!deHoy.pendienteHoy()) {
            return Optional.of("La Audioterapia de hoy ya se cerro: no se puede entregar.");
        }
        return Optional.empty();
    }

    /** Lo que ve la persona junto a los botones. */
    static String resumenDe(AudioterapiaDeHoy deHoy, String texto) {
        return "Entregar tu Audioterapia de la semana " + deHoy.semana()
                + (deHoy.titulo() == null ? "" : " '" + deHoy.titulo() + "'") + " con tus respuestas: \""
                + (texto.length() <= LARGO_VISIBLE ? texto : texto.substring(0, LARGO_VISIBLE) + "...") + "\"";
    }
}
