package com.renaser.os.rag.domain.model.herramienta;

import java.util.List;
import java.util.Optional;

/**
 * Las herramientas que tiene el acompanante de los 90 dias, hoy: mirar los habitos del dia, decir
 * cuantos puntos hay en juego y marcar uno como hecho (encargo del 2026-09-05).
 *
 * <p><b>Solo para {@code COMPANION}.</b> Sparkie ({@code COURSE_TUTOR}) responde sobre el curso
 * en el que la persona esta parada y no tiene por que tocar sus habitos: darle estas herramientas
 * seria ampliarle el alcance sin que nadie lo haya pedido (D-102 separo los dos agentes
 * justamente para que no se mezclaran).
 *
 * <p><b>Estado: definidas y ejecutables, sin ningun modelo detras.</b> Ningun proveedor de IA
 * esta conectado en este backend ({@code renaser.ia.proveedor=noop} y las autoconfiguraciones de
 * Google GenAI excluidas, a proposito). Estas definiciones viajan igual hasta
 * {@code ChatIAPort.Consulta}, asi que el adaptador real las recibe el primer dia sin que haya
 * que tocar el dominio ni el caso de uso.
 *
 * <p>Un enum queda descartado a proposito: la descripcion de una herramienta se va a ajustar
 * muchas veces contra el comportamiento real del modelo, y un enum invita a colgarle logica a
 * cada constante hasta convertirlo en el despachador — que es responsabilidad del caso de uso.
 */
public final class CatalogoHerramientasAgente {

    public static final String CONSULTAR_HABITOS_DEL_DIA = "consultar_habitos_del_dia";
    public static final String CONSULTAR_PUNTOS_EN_JUEGO = "consultar_puntos_en_juego";
    public static final String MARCAR_HABITO_COMPLETADO = "marcar_habito_completado";

    /** El nombre del argumento de {@link #MARCAR_HABITO_COMPLETADO}. */
    public static final String ARGUMENTO_REGISTRO_ID = "registro_id";

    private static final ParametroHerramienta PARAMETRO_REGISTRO_ID = ParametroHerramienta.obligatorio(
            ARGUMENTO_REGISTRO_ID, TipoParametroHerramienta.IDENTIFICADOR,
            "El identificador del habito de hoy, tal cual lo devolvio " + CONSULTAR_HABITOS_DEL_DIA + ". No lo inventes.");

    private static final DefinicionHerramienta CONSULTAR_HABITOS = DefinicionHerramienta.sinParametros(
            CONSULTAR_HABITOS_DEL_DIA,
            "Devuelve los habitos de hoy del aprendiz con su estado, los puntos que paga cada uno si lo "
                    + "entrega ahora y hasta que hora puede entregarlo. Usala antes de responder cualquier "
                    + "cosa sobre que le toca hacer, que le falta o como va el dia.");

    private static final DefinicionHerramienta CONSULTAR_PUNTOS = DefinicionHerramienta.sinParametros(
            CONSULTAR_PUNTOS_EN_JUEGO,
            "Devuelve cuantos puntos tiene todavia en juego hoy el aprendiz, sumando los habitos que aun "
                    + "puede entregar. Usala cuando pregunte cuanto puede ganar o cuanto esta por perder.");

    private static final List<DefinicionHerramienta> DEFINICIONES = List.of(CONSULTAR_HABITOS, CONSULTAR_PUNTOS,
            new DefinicionHerramienta(MARCAR_HABITO_COMPLETADO,
                    "Marca un habito de hoy como completado y devuelve los puntos otorgados. Usala SOLO cuando el "
                            + "aprendiz pida explicitamente registrar que ya lo hizo. Nunca la uses por tu cuenta ni "
                            + "para adivinar: registra puntos reales en su cuenta.",
                    List.of(PARAMETRO_REGISTRO_ID)));

    /**
     * Las mismas tres, con {@link #MARCAR_HABITO_COMPLETADO} descrita como lo que hace cuando la
     * escritura pasa por una propuesta con botones (fase 2, D-153): ya no marca, propone.
     *
     * <p>Mismo nombre y mismo parametro a proposito: el nombre viaja en conversaciones vivas y
     * cambiarlo al prender el flag romperia las que estan a mitad de camino. Lo unico que cambia es
     * la descripcion, que es lo que el modelo lee para decidir que decirle a la persona: con la de
     * arriba ("marca... y devuelve los puntos otorgados") anunciaria "listo, sumaste 10 puntos"
     * antes de que nadie confirme. Cual de las dos listas se usa lo decide el flag, fuera del dominio.
     */
    private static final List<DefinicionHerramienta> DEFINICIONES_CON_CONFIRMACION = List.of(CONSULTAR_HABITOS,
            CONSULTAR_PUNTOS,
            new DefinicionHerramienta(MARCAR_HABITO_COMPLETADO,
                    "Propone marcar un habito de hoy como completado: la app le muestra a la persona un boton "
                            + "Confirmar. NO lo marca ni otorga puntos hasta que la persona lo toque, asi que nunca "
                            + "digas que ya quedo hecho. Usala SOLO cuando el aprendiz pida explicitamente registrar "
                            + "que ya lo hizo. Nunca la uses por tu cuenta ni para adivinar. No la uses con los "
                            + "habitos marcados exige_evidencia=si: esos van con proponer_registrar_con_foto (D-171).",
                    List.of(PARAMETRO_REGISTRO_ID)));

    private CatalogoHerramientasAgente() {
    }

    public static List<DefinicionHerramienta> definiciones() {
        return DEFINICIONES;
    }

    /** Ver {@link #DEFINICIONES_CON_CONFIRMACION}. */
    public static List<DefinicionHerramienta> definicionesConConfirmacion() {
        return DEFINICIONES_CON_CONFIRMACION;
    }

    /** Vacio si el modelo se invento una herramienta que no existe — pasa, y no es un error del sistema. */
    public static Optional<DefinicionHerramienta> porNombre(String nombre) {
        return DEFINICIONES.stream().filter(definicion -> definicion.nombre().equals(nombre)).findFirst();
    }
}
