package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.PerfilBasico;
import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase.MiCelula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Los grupos del aprendiz, <b>en plural</b>, y los integrantes de cada uno (D-142).
 *
 * <p><b>Qué problema resuelve.</b> {@link ConsultarMiCelulaUseCase} responde por UN grupo: el que
 * nombra {@code participantes_programa.celula_id}. Esa columna es un puntero de un solo valor, así
 * que desde que un aprendiz puede pertenecer a varios (D-139) la pantalla "info del grupo" de la
 * app mostraba <b>siempre el mismo</b> —nombre, mentor e integrantes del grupo principal— sin
 * importar qué grupo estuviera abierto. El chat de cada grupo sí traía a su gente, porque reconcilia
 * contra {@code AcompanamientoFinder}: dos fuentes distintas en la misma pantalla, y la de la
 * derecha mentía.
 *
 * <p><b>De dónde sale la verdad acá.</b> Del historial, {@code asignaciones_celula}, que es la única
 * fuente que sabe de varios grupos. Es la misma que ya usa el detalle del panel de administración
 * ({@code ConsultarCelulasUseCase.obtener}), así que el aprendiz y el administrador pasan a ver la
 * misma lista para el mismo grupo — hasta ahora podían no coincidir.
 *
 * <p><b>Los endpoints viejos no se tocan.</b> {@code GET /me/cell} y {@code /me/cell/members} siguen
 * respondiendo exactamente lo que respondían: hay un APK en manos de probadores que los usa, y
 * cambiarles el significado desde el servidor les cambiaría la pantalla sin actualizar la app.
 */
public interface ConsultarMisCelulasUseCase {

    /**
     * Todos los grupos vigentes de quien pregunta, el principal primero.
     *
     * <p><b>Sirve para el aprendiz Y para el mentor</b>, y eso es deliberado: la pregunta es "¿en
     * qué grupos estoy?", no "¿de cuál soy alumno?". Un mentor está en los que acompaña — desde
     * D-141, incluso en varios. Dejarlo afuera es lo que hizo fracasar el intento anterior de
     * arreglar la cabecera del chat de grupo en la app, que preguntó con {@code /me/cell} y a un
     * mentor recibió "no tienes grupo".
     *
     * <p>Vacía si no tiene ninguno — no es un error, es un estado válido, igual que en
     * {@code /me/cell} desde 2026-09-02.
     *
     * <p><b>Por qué el principal primero y el resto por nombre.</b> Porque la app tiene una pantalla
     * que muestra "tu grupo" en singular y va a seguir teniéndola: si el orden fuera arbitrario, esa
     * pantalla cambiaría de grupo entre dos aperturas sin que nada hubiera pasado. El principal es
     * el que nombra el puntero, que es el mismo criterio que D-139 fijó para todo lo demás.
     */
    List<MiCelula> misCelulas(UserId actorId);

    /**
     * Los integrantes de UNO de sus grupos.
     *
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si el actor no pertenece hoy a ese
     *                                                             grupo. Preguntar por los
     *                                                             integrantes de un grupo ajeno no
     *                                                             es una lista vacía, es un 403.
     */
    List<PerfilBasico> integrantesDe(UserId actorId, CelulaId celulaId);
}
