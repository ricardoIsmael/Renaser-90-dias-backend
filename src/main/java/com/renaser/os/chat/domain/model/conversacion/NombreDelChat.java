package com.renaser.os.chat.domain.model.conversacion;

import com.renaser.os.shared.domain.Formacion;

/**
 * Cómo se llama cada chat de grupo (D-221, pedido del dueño del 29/09):
 *
 * <ul>
 *   <li>la comunidad (GLOBAL): <b>«Formación Renaser Global»</b>;</li>
 *   <li>el grupo con su mentor (CELULA): <b>«&lt;primer nombre del mentor&gt; y sus aprendices»</b>
 *       («Luisa y sus aprendices»); sin mentor vigente, el nombre del grupo;</li>
 *   <li>el soporte (aprendiz + Admin + Alquimista): <b>«&lt;primer nombre&gt; – Formación Renaser»</b>
 *       (D-173), ahora para todos los soportes y no solo los nuevos.</li>
 * </ul>
 *
 * <p>Los dos últimos se <b>derivan al leer</b> y no se guardan (regla 04: una columna que duplica una
 * regla se desincroniza en cuanto la regla cambia): si el mentor rota, el nombre cambia solo; si la
 * persona corrige su nombre, también. El primer nombre sigue la regla de D-173 ({@link PrimerNombre}).
 *
 * <p>Sin Spring ni puertos: quien llama le da los nombres completos ya resueltos.
 */
public final class NombreDelChat {

    /** El nombre de la comunidad. Lo guarda la conversación (el Admin puede renombrarla, #28). */
    public static final String DE_LA_COMUNIDAD = "Formación Renaser Global";

    private static final String GLOBAL = " Global";

    /** Cierra el nombre de cada soporte y firma los mensajes del programa. */
    public static final String FORMACION_RENASER = "Formación Renaser";

    private static final String DEL_MENTOR = " y sus aprendices";
    private static final String SEPARADOR_SOPORTE = " – ";

    private NombreDelChat() {
    }

    /**
     * @param nombreCompletoDelMentor el de su mentor vigente; {@code null} si no tiene
     * @param nombreDelGrupo          el nombre del grupo en {@code community}; respaldo sin mentor
     * @return {@code null} solo si no hay ni mentor con nombre ni nombre de grupo: la app pone el suyo
     */
    public static String deGrupo(String nombreCompletoDelMentor, String nombreDelGrupo) {
        String primerNombre = PrimerNombre.de(nombreCompletoDelMentor);
        if (!primerNombre.isEmpty()) {
            return primerNombre + DEL_MENTOR;
        }
        return nombreDelGrupo == null || nombreDelGrupo.isBlank() ? null : nombreDelGrupo.strip();
    }

    /** Sin nombre legible del aprendiz queda «Formación Renaser» a secas, nunca vacío. */
    public static String deSoporte(String nombreCompletoDelAprendiz) {
        return deSoporte(nombreCompletoDelAprendiz, FORMACION_RENASER);
    }

    /**
     * El soporte de un aprendiz con el nombre de SU formación (spec 024, FR-030): «Pedro – Diplomado Renaser». La Escuela conserva
     * «Pedro – Formación Renaser» (decisión del dueño: los chats de hoy no cambian de nombre).
     */
    public static String deSoporte(String nombreCompletoDelAprendiz, String nombreDeLaFormacion) {
        String primerNombre = PrimerNombre.de(nombreCompletoDelAprendiz);
        return primerNombre.isEmpty() ? nombreDeLaFormacion : primerNombre + SEPARADOR_SOPORTE + nombreDeLaFormacion;
    }

    /**
     * Con qué nombre firma el programa sus mensajes de sistema en las conversaciones de {@code formacion} (spec 035): «Formación Renaser» en la Escuela (como siempre)
     * y el nombre de la institución en las otras dos. Son los mismos nombres que llevan sus chats ({@code «Pedro – Diplomado Renaser»}).
     */
    public static String delPrograma(Formacion formacion) {
        return switch (formacion) {
            case ESCUELA -> FORMACION_RENASER;
            case DIPLOMADO -> "Diplomado Renaser";
            case CERTIFICACION -> "Certificación Renaser";
        };
    }

    /** La comunidad general de una formación que no es la Escuela: «Diplomado Renaser Global». La de la Escuela es {@link #DE_LA_COMUNIDAD}. */
    public static String deLaComunidad(String nombreDeLaFormacion) {
        return nombreDeLaFormacion.strip() + GLOBAL;
    }
}
