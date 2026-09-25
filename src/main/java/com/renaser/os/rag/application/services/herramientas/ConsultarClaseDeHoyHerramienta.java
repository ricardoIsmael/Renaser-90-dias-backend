package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.ClaseDeHoy;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.RecomendacionDeHoy;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@code consultar_clase_de_hoy} (R0, solo lectura, 2026-09-23): la Clase Diaria de hoy, si ya
 * figura como vista y que hace falta para completarla. Lee de {@code academy} el mismo resultado
 * que {@code GET /api/v1/classroom/clase-diaria}: que clase toca, el dia de programa y el gate de
 * visibilidad los decide {@code academy}, no esta clase.
 *
 * <p><b>Recomendacion adaptativa solo desde cache.</b> {@code GET /academia/recomendacion} la
 * genera con IA la primera vez del dia; pedirla desde una herramienta dispararia ese costo y esa
 * espera dentro de un turno del chat (C-1). Por eso se lee con
 * {@link ClaseDiariaDelAprendizPort#recomendacionDeHoySiExiste}, que nunca genera: si hoy todavia no
 * hay, la herramienta lo dice (se genera al abrir la Academia en la app) para que el modelo no
 * invente una. Si esa lectura falla, se contesta igual la Clase Diaria: la recomendacion es un
 * agregado, no la respuesta.
 *
 * <p><b>Corregido 2026-09-23.</b> Este parrafo decia "Sin recomendacion adaptativa, a proposito
 * [...] no hay contrato para leer solo la que ya esta en cache". Ese contrato ahora existe.
 */
@Component
public class ConsultarClaseDeHoyHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_clase_de_hoy";

    private static final Logger log = LoggerFactory.getLogger(ConsultarClaseDeHoyHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve la Clase Diaria de hoy del aprendiz: titulo de la leccion y del curso, si ya figura como "
                    + "vista y que necesita para completarla (un resumen escrito por la persona, con su largo "
                    + "minimo y maximo), y la recomendacion de Academia Adaptativa de hoy si ya se genero en la "
                    + "app. Usala antes de hablar de la clase de hoy o de la recomendacion, y SIEMPRE antes de "
                    + "proponer entregarla.");

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
        return LecturaDeAcademia.consultar(NOMBRE, () -> ResultadoHerramienta.exito(
                textoDe(claseDiaria.claseDeHoy(actorId)) + "\n" + textoDeRecomendacion(actorId)));
    }

    private String textoDeRecomendacion(UserId actorId) {
        try {
            return recomendacionEnTexto(claseDiaria.recomendacionDeHoySiExiste(actorId));
        } catch (RuntimeException falla) {
            log.warn("[rag] {} no pudo leer la recomendacion de Academia Adaptativa", NOMBRE, falla);
            return "No pude leer la recomendacion de Academia Adaptativa en este momento: no inventes una.";
        }
    }

    static String recomendacionEnTexto(Optional<RecomendacionDeHoy> recomendacion) {
        return recomendacion.map(r -> "Recomendacion de Academia Adaptativa de hoy: '" + r.leccionTitulo()
                        + "' del curso '" + r.cursoTitulo() + "'. Motivo: " + r.motivo()
                        + "\nEs una sugerencia aparte de la Clase Diaria de hoy.")
                // Sin promesa de cuando aparece: hoy el generador (RecomendarClasePort) es NoOp y no
                // crea ninguna, asi que "se genera al abrir la Academia" seria falso (2026-09-23).
                .orElse("Recomendacion de Academia Adaptativa: todavia no hay recomendacion de hoy. "
                        + "No inventes una.");
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
        return texto.toString().stripTrailing();
    }
}
