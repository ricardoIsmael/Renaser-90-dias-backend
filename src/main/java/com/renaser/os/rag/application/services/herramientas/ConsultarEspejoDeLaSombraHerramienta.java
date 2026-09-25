package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.espejosombra.ListarInformesEspejoSombraUseCase;
import com.renaser.os.rag.domain.model.espejosombra.DistribucionTemporal;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.rag.domain.model.espejosombra.PreguntaConfrontacion;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@code consultar_espejo_de_la_sombra} (R0, solo lectura, 2026-09-23): el informe mas reciente del
 * Espejo de la Sombra de la propia persona.
 *
 * <p><b>Solo el propio, siempre.</b> Llama a {@link ListarInformesEspejoSombraUseCase} con quien
 * pregunta como participante: la herramienta no tiene argumento para pedir el de otro, asi que ni
 * un mentor ni un ADMIN pueden leer por aca el informe de un aprendiz, aunque D-47 se lo permita en
 * la app. La cuenta activa la sigue exigiendo el caso de uso.
 *
 * <p><b>Contenido sensible (D-47):</b> es un analisis de su diario. Nunca se loguea (ni el informe
 * ni el id de la persona), y el texto le pide al modelo tratarlo como una lectura, no como un
 * diagnostico ni un juicio sobre la persona.
 */
@Component
public class ConsultarEspejoDeLaSombraHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_espejo_de_la_sombra";

    private static final Logger log = LoggerFactory.getLogger(ConsultarEspejoDeLaSombraHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve el informe mas reciente del Espejo de la Sombra de la persona (el analisis semanal de su "
                    + "diario): patron que aparece, hacia donde miro su escritura (pasado, presente, futuro), la "
                    + "lectura y las preguntas para reflexionar. Usala solo si la persona pregunta por su Espejo de "
                    + "la Sombra o quiere trabajar sobre el. Es sensible: nunca la uses por iniciativa propia.");

    private static final String COMO_USARLO = "Como usarlo: es una lectura generada a partir de su propio diario, "
            + "no un diagnostico ni un juicio sobre la persona. Compartela con cuidado y en tono neutro, sin "
            + "etiquetarla; presentala como algo para mirar juntos, no como una verdad sobre ella.";

    private final ListarInformesEspejoSombraUseCase listarInformes;

    public ConsultarEspejoDeLaSombraHerramienta(ListarInformesEspejoSombraUseCase listarInformes) {
        this.listarInformes = listarInformes;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        try {
            List<InformeEspejoSombra> informes = listarInformes.deParticipante(actorId, actorId);
            return ResultadoHerramienta.exito(informes.isEmpty() ? sinInforme() : texto(informes.getFirst()));
        } catch (NoSuchElementException sinCuenta) {
            return ResultadoHerramienta.fallo("No encontre esta cuenta.");
        } catch (NotAuthorizedException suspendida) {
            return ResultadoHerramienta.fallo("No puedo consultar su Espejo de la Sombra: la cuenta esta suspendida.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer el informe: {}", NOMBRE, falla.getClass().getSimpleName());
            return ResultadoHerramienta.fallo("No pude consultar su Espejo de la Sombra en este momento.");
        }
    }

    private static String sinInforme() {
        return "Todavia no tiene ningun informe del Espejo de la Sombra. Se genera una vez por semana a partir de "
                + "lo que escribe en su diario.";
    }

    static String texto(InformeEspejoSombra informe) {
        DistribucionTemporal distribucion = informe.distribucion();
        StringBuilder texto = new StringBuilder("Su informe mas reciente del Espejo de la Sombra es de la semana que "
                + "empezo el " + FechaDelPlan.legible(informe.semanaInicio()) + " (se basa en "
                + informe.cantidadEntradas() + " entradas de su diario).")
                .append("\nPatron que aparece: ").append(informe.patronDominante().strip())
                .append("\nHacia donde miro su escritura: pasado ").append(distribucion.pctPasado())
                .append("%, presente ").append(distribucion.pctPresente())
                .append("%, futuro ").append(distribucion.pctFuturo()).append('%')
                .append("\nLectura: ").append(informe.insight().strip());
        List<PreguntaConfrontacion> preguntas = informe.preguntas().stream()
                .sorted(Comparator.comparingInt(PreguntaConfrontacion::orden)).toList();
        if (!preguntas.isEmpty()) {
            texto.append("\nPreguntas para reflexionar:");
            preguntas.forEach(p -> texto.append("\n").append(p.orden()).append(". ").append(p.pregunta().strip()));
        }
        return texto.append("\n").append(COMO_USARLO).toString();
    }
}
