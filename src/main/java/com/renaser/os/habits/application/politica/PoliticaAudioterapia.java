package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.api.AudioterapiaDelAprendizPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.SelectorHabito;
import org.springframework.stereotype.Component;

/**
 * AUDIOTERAPIA SEMANAL: el gesto generico solo la cierra si el registro ya tiene el texto con las dos respuestas.
 *
 * <p><b>Por que existe</b> (E-652, issue #33; regla del dueno, 2026-10-29: <i>"si o si deben mandar los mensajes de
 * los audios y audioterapias"</i>). Es un {@code JOURNALING} opcional sin politica: el check de la tarjeta del APK
 * publicado la cerraba por {@code POST /habit-tracks/&#123;id&#125;/complete} sin el audio y sin respuestas.
 *
 * <p><b>Por que condiciona en vez de rechazar del todo</b> (a diferencia de {@link PoliticaPastillaRenacer}). La
 * Audioterapia NO tiene endpoint propio: la app sube las dos respuestas como evidencia de TEXTO
 * ({@code POST /habit-tracks/&#123;id&#125;/evidence}) y despues completa por el camino generico, y el acompanante de SER
 * hace lo mismo ({@code AudioterapiaDelAprendizService}). Ese es su unico camino legitimo, asi que la regla es
 * "completar solo despues de escribir", no "completar por otro lado". Misma forma que
 * {@link PoliticaPostDiarioComunidad}: un hecho externo, perezoso, que arma {@code RegistroService}.
 *
 * <p><b>El largo mide lo mismo que la app.</b> La app exige {@value #LARGO_MINIMO_DE_UNA_RESPUESTA} caracteres en
 * cada una de las {@value #RESPUESTAS} respuestas. El servidor no separa las respuestas dentro del texto (lleva las
 * preguntas en medio, y la app puede cambiarlas): comprueba el total, que es el piso que la app ya garantiza.
 * Lo que de verdad cierra el agujero es la ausencia de texto, no el numero.
 */
@Component
public class PoliticaAudioterapia implements PoliticaHabito {

    /** Las dos preguntas fijas de D-97. */
    public static final int RESPUESTAS = 2;

    /** Lo que la app exige a cada respuesta ({@code RESPUESTA_MIN_LENGTH} de {@code spiritApi.ts}). */
    public static final int LARGO_MINIMO_DE_UNA_RESPUESTA = 15;

    /** Lo menos que puede medir el texto de las dos respuestas juntas. */
    public static final int LARGO_MINIMO_DEL_TEXTO = RESPUESTAS * LARGO_MINIMO_DE_UNA_RESPUESTA;

    @Override
    public SelectorHabito selector() {
        return SelectorHabito.porClave(AudioterapiaDelAprendizPort.CLAVE_SISTEMA_AUDIOTERAPIA);
    }

    @Override
    public DecisionPolitica puedeCompletarseDirecto(Habito habito, ContextoCompletar contexto) {
        if (contexto.tieneLasRespuestasDelAudio()) {
            return DecisionPolitica.procede();
        }
        // El motivo lo lee una persona en el telefono: dice que falta Y por donde se hace.
        return DecisionPolitica.noProcede(
                "Para la Audioterapia semanal, escucha el audio y responde las dos preguntas.");
    }
}
