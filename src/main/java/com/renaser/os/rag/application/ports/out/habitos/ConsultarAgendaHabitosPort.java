package com.renaser.os.rag.application.ports.out.habitos;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Puerto propio de {@code rag} para todo lo que las herramientas del agente necesitan saber y
 * hacer con los habitos del dia. {@code registros_habito} es tabla de {@code habits} — por las
 * reglas de Modulith (D-41), {@code rag} no la consulta de frente: el adaptador que implementa
 * este puerto delega en {@code habits.api.AgendaDelDiaFinder}.
 *
 * <p>Mismo criterio que {@code LeerEntradasDiarioPort}: el puerto nombra la intencion de negocio
 * de ESTE modulo y define su propio {@link HabitoDelDia}, sin exponer el tipo de {@code habits}.
 * Asi {@code rag.application} no acopla su firma a un contrato ajeno, y el dia que ese contrato
 * cambie la traduccion queda contenida en el adaptador.
 *
 * <p><b>Lectura y escritura en el mismo puerto, a proposito.</b> No es un repositorio generico:
 * son las operaciones que el agente puede hacer con la agenda de un aprendiz, ni una mas
 * (ISP se cumple por lo chico del contrato, no por partirlo en dos interfaces de un metodo).
 * {@link #zonaDe} se sumo el 2026-09-23 para {@code consultar_tiempo_para_puntos}: decir los
 * plazos en la hora local del aprendiz. (Este parrafo decia "las tres operaciones"; ahora son
 * dos lecturas, la zona y una escritura.)
 */
public interface ConsultarAgendaHabitosPort {

    /** Vacio si el aprendiz no tiene habitos hoy. */
    List<HabitoDelDia> deHoyDe(UserId participanteId);

    /**
     * Marca uno como hecho, con las mismas guardas que la app.
     *
     * @return los puntos otorgados
     * @throws RuntimeException si el registro no existe, no es suyo, ya esta en estado terminal o
     *                          se le vencio la ventana — el caso de uso lo traduce a un
     *                          {@code ResultadoHerramienta.Fallo} legible, nunca lo deja subir
     */
    int completar(UserId actorId, UUID registroId);

    /**
     * La zona del aprendiz, la MISMA con la que {@code habits} calculo los plazos de
     * {@link #deHoyDe} (2026-09-23). Para decir esos instantes en su hora local y para traducir
     * una hora suya ("si lo hago a las 21:00") a un instante (regla 02).
     */
    ZoneId zonaDe(UserId participanteId);

    /**
     * @param puntosEnJuego  {@code null} cuando ya esta en estado terminal (nada en juego)
     * @param plazo          {@code null} cuando el habito no vence
     * @param exigeEvidencia si el habito pide evidencia. El agente NO puede subirla —el chat no
     *                       recibe fotos ni audios—: con el flag de botones le deja a la app la
     *                       tarjeta de la camara ({@code proponer_registrar_con_foto}, D-171); sin
     *                       el, lo dice y manda a la pantalla de Hoy. Completar sigue funcionando
     *                       igual con o sin ella: quien completa desde la app tampoco la entrega en
     *                       ese paso
     * @param tramos         la escala de puntos del habito hasta el {@code plazo}, ya resuelta por
     *                       {@code habits} (2026-09-23). Vacia si no vence o ya no esta en juego.
     *                       {@code rag} solo ubica instantes en ella: nunca reconstruye la escala
     * @param claveSistema   la clave del habito de catalogo ({@code DAILY_CLASS}...), {@code null}
     *                       en los personales (2026-09-26, D-171). Nunca se decide por el titulo
     */
    record HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego, Integer puntosMaximos,
                         Instant plazo, boolean exigeEvidencia, List<TramoPuntos> tramos, String claveSistema) {

        /** La Clase diaria se cierra con su resumen, nunca por el camino generico ni con una foto. */
        public static final String CLAVE_CLASE_DIARIA = "DAILY_CLASS";

        /** Los tres RITUAL TIERRA - AGUA - FUEGO (V69): los unicos que preguntan "¿Que sentiste?" (D-172). */
        public static final java.util.Set<String> CLAVES_DE_RITUAL =
                java.util.Set.of("RITUAL_MORNING", "RITUAL_MIDDAY", "RITUAL_NIGHT");

        public HabitoDelDia {
            tramos = tramos == null ? List.of() : List.copyOf(tramos);
        }

        /** Sin clave de sistema: para quien no la necesita. */
        public HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego,
                            Integer puntosMaximos, Instant plazo, boolean exigeEvidencia, List<TramoPuntos> tramos) {
            this(registroId, titulo, estado, puntosEnJuego, puntosMaximos, plazo, exigeEvidencia, tramos, null);
        }

        /** Sin escala: para quien no necesita los tramos (las tres herramientas originales). */
        public HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego,
                            Integer puntosMaximos, Instant plazo, boolean exigeEvidencia) {
            this(registroId, titulo, estado, puntosEnJuego, puntosMaximos, plazo, exigeEvidencia, List.of(), null);
        }

        /**
         * Pide foto y se registra con la camara de la app (D-171). La Clase diaria tambien pide
         * evidencia en el catalogo, pero se cierra con su resumen: no entra.
         */
        public boolean seRegistraConFoto() {
            return exigeEvidencia && !CLAVE_CLASE_DIARIA.equals(claveSistema);
        }

        /** Despues de la foto, la app pregunta "¿Que sentiste?" solo en los rituales (D-172). */
        public boolean preguntaQueSintio() {
            return claveSistema != null && CLAVES_DE_RITUAL.contains(claveSistema);
        }

        /** Un habito con puntos en juego es, por definicion, uno que todavia se puede entregar. */
        public boolean sigueEnJuego() {
            return puntosEnJuego != null;
        }
    }

    /** Entregar antes de {@code hasta} (y despues del tramo anterior) paga {@code puntos}. */
    record TramoPuntos(Instant hasta, int puntos) {
    }
}
