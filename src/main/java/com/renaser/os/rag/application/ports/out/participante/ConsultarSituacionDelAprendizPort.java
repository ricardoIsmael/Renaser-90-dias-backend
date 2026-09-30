package com.renaser.os.rag.application.ports.out.participante;

import com.renaser.os.rag.domain.model.mapa.ResumenDelMapa;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
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
 * <p><b>Los habitos de hoy, con el mismo razonamiento (D-176, 2026-09-26).</b> La bateria de 110
 * preguntas mostro al acompanante contestando sobre un habito sin mirar su estado, aunque el prompt
 * se lo ordenaba: "no encuentro ninguna ducha fria" (estaba pausada), "hazla mas tarde" de una
 * ultima comida ya completada, "te deje el boton" de un jugo verde ya hecho. Pedir una herramienta
 * es opcional para el modelo; lo que esta en el prompt no. Por eso {@link SituacionDelAprendiz#habitos}
 * lleva el estado de hoy, que arma {@code SituacionDelTurnoService} con los puertos de habitos. Este
 * puerto sigue devolviendo solo dia, fase y fecha: {@code habitos} llega {@code null} desde aca.
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
     * @param hoy         la fecha de hoy EN SU ZONA, con el año. Puede faltar ({@code null}) en quien no
     *                    la necesita. Sin ella el modelo armaba "el 2 de octubre" con el año de su
     *                    entrenamiento y la herramienta respondia "fuera del programa" (bateria del
     *                    2026-09-25, #41)
     * @param habitos     como estaba su dia de habitos al empezar el turno (D-176). {@code null} =
     *                    no se sabe (no se consulto o fallo): el prompt lo dice y el modelo vuelve a
     *                    las herramientas
     * @param trato       como tratarla (E-457): masculino, femenino o neutro. {@code null} = neutro
     * @param mapa        la prioridad y el proximo hito de su Mapa de Renacimiento (D-233). {@code null} =
     *                    no se consulto o fallo: el prompt no dice nada y el modelo usa consultar_mi_mapa
     */
    record SituacionDelAprendiz(int diaPrograma, int fase, LocalDate hoy, HabitosDeHoy habitos,
                                ConsultarTratoDeLaPersonaPort.TratoDeLaPersona trato, ResumenDelMapa mapa) {

        public SituacionDelAprendiz(int diaPrograma, int fase, LocalDate hoy, HabitosDeHoy habitos,
                                    ConsultarTratoDeLaPersonaPort.TratoDeLaPersona trato) {
            this(diaPrograma, fase, hoy, habitos, trato, null);
        }

        public SituacionDelAprendiz(int diaPrograma, int fase, LocalDate hoy, HabitosDeHoy habitos) {
            this(diaPrograma, fase, hoy, habitos, null);
        }

        public SituacionDelAprendiz(int diaPrograma, int fase, LocalDate hoy) {
            this(diaPrograma, fase, hoy, null);
        }

        public SituacionDelAprendiz(int diaPrograma, int fase) {
            this(diaPrograma, fase, null, null);
        }

        public SituacionDelAprendiz conHabitos(HabitosDeHoy habitosDeHoy) {
            return new SituacionDelAprendiz(diaPrograma, fase, hoy, habitosDeHoy, trato, mapa);
        }

        public SituacionDelAprendiz conTrato(ConsultarTratoDeLaPersonaPort.TratoDeLaPersona tratoDeLaPersona) {
            return new SituacionDelAprendiz(diaPrograma, fase, hoy, habitos, tratoDeLaPersona, mapa);
        }

        public SituacionDelAprendiz conMapa(ResumenDelMapa resumenDelMapa) {
            return new SituacionDelAprendiz(diaPrograma, fase, hoy, habitos, trato, resumenDelMapa);
        }
    }
}
