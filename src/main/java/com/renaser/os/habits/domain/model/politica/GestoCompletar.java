package com.renaser.os.habits.domain.model.politica;

/**
 * Con QUE gesto se esta pidiendo completar un registro.
 *
 * <p><b>Por que existe.</b> {@link PoliticaHabito#puedeCompletarseDirecto} responde una
 * pregunta que su propio javadoc acota: <i>"si el habito puede darse por cumplido con el
 * gesto generico"</i>. Hasta ahora nadie podia contestar la mitad previa de esa pregunta —
 * <i>¿este pedido ES el gesto generico?</i> —, asi que {@code RegistroService} consultaba la
 * politica en TODAS sus invocaciones, incluidas las que llegan desde el gesto propio de un
 * habito. Con una sola forma de invocacion eso no se notaba; con dos, una politica que
 * cierra el gesto generico cerraria tambien el camino legitimo.
 *
 * <p><b>Concretamente</b> (bug E-120): la Clase Diaria no puede completarse sin resumen, pero
 * su unico camino valido ({@code POST /api/v1/classroom/clase-diaria}) pasa a proposito por
 * {@code CompletarRegistroUseCase} para no duplicar el calculo de puntos ni el de la ventana
 * de entrega. Sin este dato, {@link com.renaser.os.habits.application.politica.PoliticaClaseDiaria}
 * rechazaria tanto la ruta generica (lo que se quiere) como su propio gesto (lo que la
 * romperia).
 *
 * <p><b>Por que aca y no en {@link ContextoCompletar}.</b> {@code ContextoCompletar} son
 * "hechos EXTERNOS al catalogo" que una politica <i>consulta para decidir</i> — si publico en
 * el Muro, por ejemplo. El gesto no es un hecho del mundo que la politica deba mirar: es
 * <b>quien esta preguntando</b>. Mezclarlo ahi obligaria a cada politica futura a acordarse de
 * ramificar por el gesto para no romper el camino propio de su habito; con el dato aca, el
 * unico que ramifica es quien orquesta, una sola vez, y las politicas siguen contestando
 * exactamente la pregunta que dice su contrato.
 *
 * <p><b>Por que no llevar el resumen en el contexto y que la politica lo exija.</b> Se evaluo
 * y se descarto: {@code POST /habit-tracks/&#123;id&#125;/complete} acepta un {@code respuestaTexto}
 * libre, asi que "procede si viene texto" dejaria la ruta generica abierta con solo mandar 15
 * caracteres — y esa ruta no marca la leccion como vista ni comprueba que sea la clase de HOY,
 * que son la otra mitad del gesto (ver {@code ClaseDiariaService.completar}).
 */
public enum GestoCompletar {

    /**
     * {@code POST /habit-tracks/&#123;id&#125;/complete} y la herramienta {@code marcar_habito_completado}
     * del agente de {@code rag} — que entra por {@code habits.api.AgendaDelDiaFinder} y termina en
     * el mismo caso de uso. Es el gesto que las politicas gobiernan.
     */
    GENERICO,

    /**
     * El gesto propio del habito, servido por su propio endpoint y con su propia validacion de
     * entrada. No se le consulta a la politica porque la politica existe justamente para empujar
     * al aprendiz hacia aca.
     *
     * <p><b>Hoy lo usa un solo llamador: {@code ClaseDiariaHabitoService}.</b>
     * {@code PastillaRenacerHabitoService} tambien tiene gesto propio y sigue mandando
     * {@link #GENERICO}: hoy da igual porque {@code PASTILLA_RENACER} no tiene politica, pero el
     * dia que la tenga hay que marcarlo aca. Se dejo sin tocar a proposito — quedaba fuera del
     * alcance del arreglo de la Clase Diaria (E-120).
     *
     * <p><b>No es un bypass alcanzable desde afuera:</b> {@code CompletarRegistroUseCase} vive en
     * {@code application/ports/in} y no en {@code api/}, asi que ningun otro modulo puede
     * construir un comando con este valor; y el DTO de entrada del endpoint generico no tiene
     * este campo, asi que tampoco viaja desde el telefono (mismo blindaje que {@code puntos},
     * CLAUDE.MD §5.3.3).
     */
    PROPIO_DEL_HABITO
}
