package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FiltroLecciones;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FragmentoRelevante;
import com.renaser.os.rag.application.ports.out.conversacion.ConsultarLeccionesVisiblesPort;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code buscar_en_los_cursos} (D-255, 2026-10-06): SER busca en el material de los cursos con sus
 * propias palabras, en medio de la conversacion.
 *
 * <p><b>Por que hace falta, si el turno ya trae material.</b> El material que llega en el prompt se
 * busca UNA vez, antes de hablar con el modelo, con el texto literal de la pregunta. Para «¿y como
 * lo hago?» despues de hablar del ritual de la mañana, o para «¿como hago mi habito de las 7?», esa
 * busqueda no sabe de que habito se habla y trae otra cosa. Con esta herramienta SER busca lo que
 * de verdad necesita («ritual de la mañana pasos»), despues de saber de que habito o leccion se
 * trata. Es lo que el dueño pidio: «que SER haga lo mismo» que Sparkie, que «tenia toda la
 * informacion de los cursos».
 *
 * <p><b>Misma fuente y misma busqueda que tenia Sparkie</b>: {@link VectorStorePort} sobre
 * {@code base_conocimiento}, acotada a las lecciones que la persona puede ver HOY
 * ({@link ConsultarLeccionesVisiblesPort}, el gate de {@code academy}). Una leccion del dia 60 no
 * se le cita a alguien del dia 3, igual que en el material del turno.
 *
 * <p>Sin {@code @Transactional} (C-1): la busqueda empieza con un embedding, que es una llamada a la
 * IA. Si falla (se acabo la cuota de embeddings), vuelve un {@code Fallo} legible: SER lo dice en
 * una linea y no inventa.
 */
@Component
public class BuscarEnLosCursosHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "buscar_en_los_cursos";
    public static final String ARGUMENTO_CONSULTA = "consulta";

    /** Los mismos cinco fragmentos que trae el material del turno. */
    static final int FRAGMENTOS = 5;

    static final String SIN_RESULTADOS = "No hay nada sobre eso en el material de los cursos que la persona "
            + "tiene habilitado hoy. Dilo en una linea y no inventes pasos, citas ni titulos de lecciones.";

    private static final Logger log = LoggerFactory.getLogger(BuscarEnLosCursosHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Busca en el material de los cursos del programa (las transcripciones de las clases y lecciones "
                    + "que la persona ya tiene habilitadas) y devuelve los fragmentos mas parecidos a la consulta. "
                    + "Usala cuando te pregunten como se hace un habito o un ritual, que dice una leccion o un "
                    + "curso, o que ensena el programa sobre un tema, y el contenido recuperado para esta pregunta "
                    + "no lo cubre. Escribe la consulta con las palabras del tema (por ejemplo 'ritual de la manana "
                    + "pasos' o 'jugo verde como prepararlo'), no la pregunta entera.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_CONSULTA, TipoParametroHerramienta.TEXTO,
                    "El tema a buscar, en pocas palabras.")));

    private final VectorStorePort vectorStorePort;
    private final ConsultarLeccionesVisiblesPort leccionesVisiblesPort;

    public BuscarEnLosCursosHerramienta(VectorStorePort vectorStorePort,
                                       ConsultarLeccionesVisiblesPort leccionesVisiblesPort) {
        this.vectorStorePort = vectorStorePort;
        this.leccionesVisiblesPort = leccionesVisiblesPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        String consulta = invocacion.argumento(ARGUMENTO_CONSULTA).strip();
        List<FragmentoRelevante> fragmentos;
        try {
            FiltroLecciones visibles = FiltroLecciones.soloVisibles(leccionesVisiblesPort.visiblesParaActor(actorId));
            fragmentos = vectorStorePort.buscarSimilares(consulta, FRAGMENTOS, visibles);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo buscar en el material de los cursos ({})", falla.getClass().getSimpleName());
            return ResultadoHerramienta.fallo("No pude buscar en el material de los cursos en este momento.");
        }
        return ResultadoHerramienta.exito(textoDe(fragmentos));
    }

    private static String textoDe(List<FragmentoRelevante> fragmentos) {
        if (fragmentos.isEmpty()) {
            return SIN_RESULTADOS;
        }
        return "Material de los cursos para esta consulta (usalo como fuente y di que sale del programa):\n"
                + fragmentos.stream().map(fragmento -> "- " + fragmento.contenido().strip())
                .collect(Collectors.joining("\n"));
    }
}
