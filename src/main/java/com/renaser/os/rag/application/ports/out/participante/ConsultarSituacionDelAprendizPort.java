package com.renaser.os.rag.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * Donde esta parada la persona con la que el agente esta hablando.
 *
 * <p><b>Por que existe (2026-09-14).</b> El prompt del acompanante dice, textual, que habla de
 * "el dia del programa en que esta" — y nada se lo suministraba. Lo unico que viajaba al modelo
 * era {@code (agente, actorId, pregunta, contexto, ambito, historial, herramientas)}, donde
 * {@code contexto} son fragmentos de LECCIONES. Ni dia, ni fase. El agente no mentia, porque el
 * mismo prompt le prohibe inventar numeros de dia: simplemente decia que no sabia algo que el
 * sistema si sabe.
 *
 * <p><b>Por que es un puerto y no una herramienta.</b> Una herramienta cuesta un turno completo
 * con el modelo —contesta "necesito llamar a X", vuelve al servidor, se ejecuta, vuelve al
 * modelo, y recien entonces responde—, o sea uno o dos segundos mas de espera por un dato que el
 * servidor ya tenia antes de empezar. El dia hace falta en casi toda conversacion, asi que va en
 * el prompt. La regla completa esta en D-123 de {@code docs/MODULO_RAG.md}.
 *
 * <p>Puerto propio de {@code rag} con su propio tipo, mismo criterio que
 * {@code ConsultarAgendaHabitosPort}: la aplicacion no acopla su firma a un contrato ajeno, y el
 * dia que ese contrato cambie la traduccion queda contenida en el adaptador.
 */
public interface ConsultarSituacionDelAprendizPort {

    /** Vacio si quien pregunta no es un participante del programa — un mentor, un administrador,
     * o alguien que todavia no lo activo. En ese caso el agente no habla de dias, que es lo
     * correcto: no los tiene. */
    Optional<SituacionDelAprendiz> de(UserId participanteId);

    /**
     * @param diaPrograma dia de programa, de 1 a 90
     * @param fase        numero de fase, de 1 a 4. Se deriva del DIA y no se lee de la columna
     *                    guardada: {@code users.api.FasePrograma} documenta que hay filas con el
     *                    dia y la fase desincronizados (D-66) y que nunca hay que confiar en el
     *                    valor guardado sin recomputarlo
     */
    record SituacionDelAprendiz(int diaPrograma, int fase) {
    }
}
