package com.renaser.os.rag.domain.model.conversacion;

/**
 * Cual de los DOS asistentes conversacionales del programa esta hablando (D-102, 2026-09-04).
 *
 * <p>El dueno del producto lo pidio textual: "Sparkie: su objetivo es ayudar en los cursos. El
 * otro agente, que sera un chat aparte, sera durante su progreso de 90 dias. No los juntes en un
 * mismo". D-100 los habia juntado como "un asistente con dos modos" (un solo prompt con una
 * seccion de ambito, un solo historial). Esto lo deshace: cada agente tiene su prompt de sistema,
 * su historial y su nombre visible.
 *
 * <ul>
 *   <li>{@link #COMPANION}: el acompanante de los 90 dias. Habitos, dias del programa, como esta
 *   armada la app, orientacion y animo. Es el del boton flotante y el saludo de arranque. Su nombre
 *   visible todavia no lo confirmo el dueno (hoy "Renasia", el que siempre tuvo).</li>
 *   <li>{@link #COURSE_TUTOR}: Sparkie, el tutor de los cursos. Responde sobre el curso o la
 *   leccion en la que la persona esta parada. Vive al pie del curso y de la leccion en Recursos
 *   Exclusivos.</li>
 * </ul>
 *
 * <p>Espejo de la columna {@code mensajes_renasia.agente} (V27, texto con CHECK sobre estos dos
 * valores). Los nombres van en ingles y en SCREAMING_SNAKE porque viajan tal cual por el wire
 * ({@code PreguntarRenasiaRequest.agent}, {@code GET ...?agent=}), mismo criterio que
 * {@code MensajeRenasiaResponse.role}.
 *
 * <p><b>D-255 (2026-10-06): Sparkie se retiro y responde SER.</b> Pedido del dueño, textual: «Me
 * dijeron que quites a Sparkie, porque los usuarios se confunden, y que SER haga lo mismo». Los dos
 * valores siguen existiendo porque viajan por el wire y estan en la base: la app no se actualiza
 * por aire y el APK instalado sigue abriendo el chat del curso con {@code COURSE_TUTOR}. Lo que
 * cambia es quien contesta: {@link #queResponde()}.
 */
public enum AgenteConversacional {
    COMPANION,
    COURSE_TUTOR;

    /**
     * El agente que de verdad atiende un turno pedido a este (D-255): siempre SER, el acompanante.
     * Un cliente viejo que pregunta o pagina el historial como {@code COURSE_TUTOR} habla con SER,
     * con su prompt, sus herramientas, su memoria y su historial; el curso desde donde pregunta
     * sigue acotando el material que se recupera.
     */
    public AgenteConversacional queResponde() {
        return COMPANION;
    }
}
