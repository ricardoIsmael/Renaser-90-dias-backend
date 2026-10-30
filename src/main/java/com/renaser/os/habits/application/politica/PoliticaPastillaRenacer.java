package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.SelectorHabito;
import org.springframework.stereotype.Component;

/**
 * PASTILLA RENACER: no se marca hecha con el gesto generico. Se cierra unicamente entregando las respuestas del
 * audio, por {@code POST /api/v1/spirit-audio/submit}.
 *
 * <p><b>Por que existe</b> (E-652, issue #33; regla del dueno, 2026-10-29: <i>"si o si deben mandar los mensajes de
 * los audios y audioterapias"</i>). La Pastilla era un {@code JOURNALING} opcional sin politica, asi que caia en
 * {@code RegistroPoliticasHabito.GENERICA} y el check de la tarjeta del APK publicado la cerraba por
 * {@code POST /habit-tracks/&#123;id&#125;/complete} sin el audio y sin las dos respuestas. Era el caso que el javadoc de
 * {@link GestoCompletar#PROPIO_DEL_HABITO} dejaba anotado: <i>"el dia que tenga politica hay que marcarlo"</i>.
 *
 * <p><b>Por que esto NO rompe su camino legitimo.</b> {@code EspirituService.entregar} guarda el resumen y despues
 * llama a {@code PastillaRenacerHabitoService}, que pasa por {@code CompletarRegistroUseCase} para no duplicar el
 * calculo de puntos ni la ventana. Ese comando lleva {@link GestoCompletar#PROPIO_DEL_HABITO} y por eso
 * {@code RegistroService} no le consulta a esta politica. El gesto no viaja desde el telefono (el DTO del endpoint
 * generico no tiene ese campo), asi que no es un atajo alcanzable desde afuera.
 *
 * <p><b>Selecciona por clave y no por tipo</b>, igual que {@link PoliticaClaseDiaria}: su tipo es
 * {@code JOURNALING}, que comparten otros habitos a los que nadie les pidio nada.
 */
@Component
public class PoliticaPastillaRenacer implements PoliticaHabito {

    @Override
    public SelectorHabito selector() {
        return SelectorHabito.porClave(CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER);
    }

    /** Ignora el {@code contexto}: no hay hecho externo que mirar, este habito nunca se cierra de un tiron. */
    @Override
    public DecisionPolitica puedeCompletarseDirecto(Habito habito, ContextoCompletar contexto) {
        // El motivo lo lee una persona en el telefono: dice que falta Y por donde SI se hace.
        return DecisionPolitica.noProcede("Para la Pastilla Renacer, escucha el audio y responde las dos preguntas.");
    }
}
