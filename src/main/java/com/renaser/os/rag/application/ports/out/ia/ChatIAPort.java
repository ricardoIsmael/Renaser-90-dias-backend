package com.renaser.os.rag.application.ports.out.ia;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.CanalConversacion;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;
import com.renaser.os.shared.domain.UserId;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * Le pregunta al modelo y devuelve la respuesta en streaming.
 *
 * <p>D-102 (2026-09-04): la firma pasa a recibir una {@link Consulta} que dice QUE agente habla.
 * Son dos asistentes distintos con dos prompts de sistema distintos (ver
 * {@link AgenteConversacional}); el adaptador elige el prompt segun el agente. Lo que D-100 habia
 * agregado como "un asistente con dos modos" queda repartido asi:
 * <ul>
 *   <li>{@code ambito}: sobre QUE curso/leccion esta hablando la persona ("el curso X, leccion Y").
 *   Solo tiene sentido para {@link AgenteConversacional#COURSE_TUTOR}; para el acompanante llega
 *   siempre nulo. Viaja al prompt de SISTEMA, nunca dentro de la pregunta: el primer intento lo
 *   mandaba concatenado al texto del aprendiz y el backend lo guardaba como si lo hubiera escrito
 *   el.</li>
 *   <li>{@code historial}: los ultimos turnos DEL MISMO AGENTE, en orden cronologico y SIN la
 *   pregunta actual. Sin esto el modelo no recordaba nada.</li>
 * </ul>
 *
 * <p>El adaptador nunca emite {@link EventoRenasia.Fuentes} ni {@link EventoRenasia.Error}: las
 * fuentes las arma el caso de uso a partir de lo recuperado, y el error lo traduce el caso de uso
 * cuando este Flux termina en error.
 */
public interface ChatIAPort {

    Flux<EventoRenasia> responder(Consulta consulta);

    /**
     * Todo lo que el modelo necesita para una respuesta. {@code ambito} es nulo para el
     * acompanante y opcional para el tutor de cursos; el resto es obligatorio.
     *
     * <p>{@code herramientas} (2026-09-05): que puede HACER el agente ademas de responder — hoy,
     * mirar los habitos del dia, decir cuantos puntos hay en juego y marcar uno como hecho. Viaja
     * en la consulta y no se configura dentro del adaptador para que QUE herramientas hay sea una
     * decision de producto y no del proveedor: cambiarlas no toca ningun adaptador, y un
     * adaptador real solo tiene que traducirlas al formato de su SDK (en Spring AI, un
     * {@code ToolCallback}) y llamar a {@code EjecutarHerramientaAgenteUseCase.ejecutar} cuando el
     * modelo pida una. Llega vacia para {@link AgenteConversacional#COURSE_TUTOR}, que no toca
     * habitos (D-102).
     *
     * <p>{@code actorId} (2026-09-05) es EN NOMBRE DE QUIEN se ejecuta una herramienta. Viaja
     * aparte de la pregunta y del historial a proposito: es identidad, no conversacion. Si el
     * duenio de los datos pudiera salir de lo que el modelo escribe, "complete el habito de Juan"
     * seria ejecutable. Sin este campo el adaptador no podia ejecutar herramientas en nombre de
     * nadie, y por eso el modelo respondia "no tengo acceso a tu cuenta".
     *
     * <p>El adaptador {@code NoOp} recibe las herramientas y no las usa — no hay modelo que las
     * pida. El adaptador real de Gemini SI las declara (ver {@code HerramientaToolCallback}).
     *
     * <p>{@code canal} (2026-09-23): si la respuesta se va a leer ({@link CanalConversacion#TEXTO})
     * o a escuchar ({@link CanalConversacion#VOZ}). Con {@code VOZ} el adaptador real agrega al
     * prompt de sistema las pautas de forma hablada; no cambia el contexto, las herramientas ni los
     * limites. El {@code NoOp} lo ignora.
     *
     * <p>{@code memoria} (D-167): lo que el acompanante sabe de la persona. {@code null} si no hay
     * que usarla: el tutor de cursos, o la memoria apagada o ilegible. Con {@code null} el prompt
     * queda byte por byte como antes de D-167.
     */
    record Consulta(AgenteConversacional agente, UserId actorId, String pregunta, List<String> contexto,
                    String ambito, List<MensajeRenasia> historial,
                    List<DefinicionHerramienta> herramientas, SituacionDelAprendiz situacion,
                    CanalConversacion canal, MemoriaDeRenasia memoria) {

        /** Sin memoria (D-167). */
        public Consulta(AgenteConversacional agente, UserId actorId, String pregunta, List<String> contexto,
                        String ambito, List<MensajeRenasia> historial,
                        List<DefinicionHerramienta> herramientas, SituacionDelAprendiz situacion,
                        CanalConversacion canal) {
            this(agente, actorId, pregunta, contexto, ambito, historial, herramientas, situacion, canal, null);
        }

        public Consulta {
            Objects.requireNonNull(agente, "agente no puede ser null");
            Objects.requireNonNull(canal, "canal no puede ser null");
            Objects.requireNonNull(actorId, "actorId no puede ser null");
            Objects.requireNonNull(pregunta, "pregunta no puede ser null");
            contexto = List.copyOf(Objects.requireNonNull(contexto, "contexto no puede ser null"));
            historial = List.copyOf(Objects.requireNonNull(historial, "historial no puede ser null"));
            herramientas = List.copyOf(Objects.requireNonNull(herramientas, "herramientas no puede ser null"));
            // `situacion` SI puede ser null, a proposito: quien conversa puede no ser un
            // participante del programa (un mentor, un administrador, alguien que todavia no lo
            // activo). En ese caso el agente no habla de dias, que es lo correcto — no los tiene.
            // Exigirla aqui obligaria a inventar un dia 0 y el prompt lo leeria como un dato.
        }
    }
}
