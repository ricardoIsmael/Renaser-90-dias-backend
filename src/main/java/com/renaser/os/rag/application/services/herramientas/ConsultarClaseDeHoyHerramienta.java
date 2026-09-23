package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.ClaseDeHoy;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * {@code consultar_clase_de_hoy} (R0, solo lectura, 2026-09-23): la Clase Diaria de hoy, si ya
 * figura como vista y que hace falta para completarla. Lee de {@code academy} el mismo resultado
 * que {@code GET /api/v1/classroom/clase-diaria}: que clase toca, el dia de programa y el gate de
 * visibilidad los decide {@code academy}, no esta clase.
 *
 * <p><b>Sin recomendacion adaptativa, a proposito.</b> {@code GET /academia/recomendacion} la
 * genera con IA la primera vez del dia; pedirla desde una herramienta dispararia ese costo y esa
 * espera dentro de un turno del chat (C-1), y no hay contrato para leer solo la que ya esta en
 * cache. La herramienta lo dice para que el modelo no invente una.
 */
@Component
public class ConsultarClaseDeHoyHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_clase_de_hoy";

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve la Clase Diaria de hoy del aprendiz: titulo de la leccion y del curso, si ya figura como "
                    + "vista y que necesita para completarla (un resumen escrito por la persona, con su largo "
                    + "minimo y maximo). Usala antes de hablar de la clase de hoy y SIEMPRE antes de proponer "
                    + "entregarla.");

    private final ClaseDiariaDelAprendizPort claseDiaria;

    public ConsultarClaseDeHoyHerramienta(ClaseDiariaDelAprendizPort claseDiaria) {
        this.claseDiaria = claseDiaria;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        return LecturaDeAcademia.consultar(NOMBRE,
                () -> ResultadoHerramienta.exito(textoDe(claseDiaria.claseDeHoy(actorId))));
    }

    static String textoDe(ClaseDeHoy clase) {
        return switch (clase.estado()) {
            case NO_INICIADO -> "Todavia no arranco su programa de 90 dias (dia 0): hoy no hay Clase Diaria.";
            case PROXIMAMENTE -> "Hoy (dia " + clase.diaPrograma() + " del programa) no hay Clase Diaria "
                    + "publicada. No inventes una.";
            case DISPONIBLE -> textoDeDisponible(clase);
        };
    }

    private static String textoDeDisponible(ClaseDeHoy clase) {
        StringBuilder texto = new StringBuilder("Clase Diaria de hoy (dia ").append(clase.diaPrograma())
                .append(" del programa): '").append(clase.leccionTitulo()).append("' del curso '")
                .append(clase.cursoTitulo()).append("' (leccion_id=").append(clase.leccionId()).append(").\n")
                .append("Leccion vista: ").append(clase.leccionVista() ? "si" : "no").append(".\n")
                .append("Para completarla: entregar un resumen escrito por la persona, con sus palabras, de lo que "
                        + "entendio de la clase, de ").append(clase.resumenMinimo()).append(" a ")
                .append(clase.resumenMaximo()).append(" caracteres. Al entregarlo se cierra el habito de la Clase "
                        + "Diaria, suma sus puntos y la leccion queda vista. Si ya lo entrego hoy, ese habito figura "
                        + "como hecho en consultar_habitos_del_dia.\n");
        if (!clase.leccionVista()) {
            texto.append("Todavia no figura como vista: el resumen es de lo que entendio, asi que conviene que la "
                    + "vea primero en la app.\n");
        }
        return texto.append("La recomendacion de Academia Adaptativa no se consulta desde el chat: no inventes una.")
                .toString();
    }
}
