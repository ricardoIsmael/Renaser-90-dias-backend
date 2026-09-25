package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort;
import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort.CursoAccesible;
import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort.CursoBloqueado;
import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort.CursosDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * {@code consultar_mis_cursos} (R0, solo lectura, 2026-09-23): los cursos que la persona ya puede
 * ver, con su avance, y los que se le abren mas adelante por dia de programa. Existe para que el
 * modelo tenga los {@code curso_id} que pide {@code consultar_por_que_esta_bloqueado} sin
 * inventarlos.
 *
 * <p>Es lo mismo que {@code GET /api/v1/cursos} y {@code GET /api/v1/cursos/bloqueados}: la
 * visibilidad y el criterio de mostrar con candado solo lo bloqueado por dia los decide
 * {@code academy}.
 */
@Component
public class ConsultarMisCursosHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_mis_cursos";

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve los cursos que el aprendiz ya puede ver (con cuantas lecciones completo) y los que se le "
                    + "desbloquean mas adelante por dia de programa, cada uno con su curso_id. Usala cuando pregunte "
                    + "que cursos tiene o como va, y antes de consultar_por_que_esta_bloqueado si no tienes el "
                    + "curso_id.");

    private final ConsultarCursosPort cursosPort;

    public ConsultarMisCursosHerramienta(ConsultarCursosPort cursosPort) {
        this.cursosPort = cursosPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        return LecturaDeAcademia.consultar(NOMBRE,
                () -> ResultadoHerramienta.exito(textoDe(cursosPort.cursosDe(actorId))));
    }

    static String textoDe(CursosDelAprendiz cursos) {
        StringBuilder texto = new StringBuilder("Cursos que ya puede ver:\n");
        if (cursos.accesibles().isEmpty()) {
            texto.append("Ninguno por ahora.\n");
        }
        cursos.accesibles().forEach(curso -> texto.append(lineaDe(curso)).append('\n'));
        if (cursos.bloqueados().isEmpty()) {
            return texto.append("No tiene cursos que se desbloqueen mas adelante por dia de programa.").toString();
        }
        texto.append("Se desbloquean mas adelante, por dia de programa:\n");
        cursos.bloqueados().forEach(curso -> texto.append(lineaDe(curso)).append('\n'));
        return texto.toString().stripTrailing();
    }

    private static String lineaDe(CursoAccesible curso) {
        return "curso_id=" + curso.cursoId() + " | " + curso.titulo() + " | " + curso.leccionesCompletadas() + " de "
                + curso.totalLecciones() + " lecciones completadas";
    }

    private static String lineaDe(CursoBloqueado curso) {
        return "curso_id=" + curso.cursoId() + " | " + curso.titulo() + " | se desbloquea en su dia "
                + curso.diaDesbloqueo() + " del programa (hoy va en el dia " + curso.diaProgramaActual() + ")";
    }
}
