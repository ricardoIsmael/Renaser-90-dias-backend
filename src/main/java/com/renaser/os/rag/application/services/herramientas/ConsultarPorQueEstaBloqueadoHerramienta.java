package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort;
import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort.Bloqueo;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code consultar_por_que_esta_bloqueado} (R0, solo lectura, 2026-09-23): por que un curso o una
 * leccion todavia no se le abre a la persona. Es lo mismo que {@code GET /api/v1/cursos/{id}/preview}
 * y {@code GET /api/v1/lecciones/{id}/preview}.
 *
 * <p><b>Solo se revela el bloqueo por dia de programa</b>, igual que en la app: un curso de otro
 * rol, sin publicar o inexistente responde "no bloqueado por dia" (criterio de {@code academy},
 * no de esta clase). La herramienta le dice al modelo que en ese caso no invente otro motivo.
 */
@Component
public class ConsultarPorQueEstaBloqueadoHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_por_que_esta_bloqueado";
    public static final String ARGUMENTO_CURSO_ID = "curso_id";
    public static final String ARGUMENTO_LECCION_ID = "leccion_id";

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Dice por que un curso o una leccion todavia esta bloqueado para el aprendiz y en que dia del programa "
                    + "se le abre. Manda exactamente uno de los dos: curso_id o leccion_id. No inventes ids: el "
                    + "curso_id sale de consultar_mis_cursos y el leccion_id de consultar_clase_de_hoy.",
            List.of(new ParametroHerramienta(ARGUMENTO_CURSO_ID, TipoParametroHerramienta.TEXTO,
                            "El curso_id tal cual lo devolvio consultar_mis_cursos.", false),
                    new ParametroHerramienta(ARGUMENTO_LECCION_ID, TipoParametroHerramienta.TEXTO,
                            "El leccion_id de la leccion, si la pregunta es por una leccion.", false)));

    private final ConsultarCursosPort cursosPort;

    public ConsultarPorQueEstaBloqueadoHerramienta(ConsultarCursosPort cursosPort) {
        this.cursosPort = cursosPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        String cursoId = limpio(invocacion.argumento(ARGUMENTO_CURSO_ID));
        String leccionId = limpio(invocacion.argumento(ARGUMENTO_LECCION_ID));
        if ((cursoId == null) == (leccionId == null)) {
            return ResultadoHerramienta.fallo("Manda exactamente uno: curso_id o leccion_id. Si no tienes el "
                    + "curso_id, llama antes a consultar_mis_cursos.");
        }
        return LecturaDeAcademia.consultar(NOMBRE, () -> ResultadoHerramienta.exito(textoDe(cursoId != null
                ? cursosPort.bloqueoDeCurso(actorId, cursoId)
                : cursosPort.bloqueoDeLeccion(actorId, leccionId))));
    }

    private static String limpio(String argumento) {
        return argumento == null || argumento.isBlank() ? null : argumento.strip();
    }

    static String textoDe(Bloqueo bloqueo) {
        if (!bloqueo.bloqueado()) {
            return "No esta bloqueado por dia de programa. Si igual no lo puede abrir, no tengo un motivo que pueda "
                    + "darle (puede que no sea parte de su plan o que el id no exista): no inventes otro motivo.";
        }
        return "'" + bloqueo.titulo() + "' esta bloqueado por dia de programa: se le abre cuando llegue a su dia "
                + bloqueo.diaDesbloqueo() + " del programa (hoy va en el dia " + bloqueo.diaProgramaActual() + ").";
    }
}
