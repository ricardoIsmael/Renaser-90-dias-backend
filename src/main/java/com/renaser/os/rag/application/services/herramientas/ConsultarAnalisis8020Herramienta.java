package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.api.Analisis8020Finder;
import com.renaser.os.rag.api.Analisis8020Finder.AnalisisLeido;
import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.application.services.circuito.InterruptoresDeSer;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@code consultar_analisis_80_20} (R0, solo lectura, spec 031, FR-026): el análisis 80/20 más reciente de la propia persona, el de su
 * semana y el de su mes, para que SER se lo explique en su tono.
 *
 * <p><b>Solo el propio, siempre.</b> La herramienta no tiene argumento para pedir el de otra persona. <b>Solo lee lo guardado</b>: nunca
 * llama a la IA para generar ni regenerar un análisis (FR-006), así que preguntarle a SER por su semana cien veces no cuesta más que una.
 * Cuenta contra la cuota normal de SER, como cualquier pregunta. Con {@code ia.analisis} apagada no se ofrece el texto (FR-019).
 *
 * <p><b>Contenido sensible:</b> el análisis resume lo que la persona escribió en el Código Renaser. Nunca se loguea, y el resultado le pide
 * al modelo no llevarlo a su memoria de largo plazo (lo emocional y la salud no se recuerdan, FR-026).
 */
@Component
public class ConsultarAnalisis8020Herramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_analisis_80_20";

    private static final Logger log = LoggerFactory.getLogger(ConsultarAnalisis8020Herramienta.class);

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve el analisis 80/20 mas reciente de la persona: el de su ultima semana cerrada y el de su ultimo mes cerrado "
                    + "(que pocas acciones movieron mas su avance, que se le fue, como estuvo y que hacer en el periodo que viene), ya "
                    + "escrito y guardado. Usala cuando la persona pregunte como le fue en su semana o su mes, o que significa o que hacer "
                    + "con su analisis. No genera nada nuevo.");

    private static final String COMO_USARLO = "Como usarlo: explicalo en tu tono, sin cambiar ninguna cifra ni agregar nada que no este "
            + "ahi. No diagnostiques ni etiquetes como se siente. Si sugiere agendar, mover o quitar algo, ofrecelo como propuesta con "
            + "boton de confirmar: el analisis por si solo no cambia su plan. No guardes en tu memoria lo emocional ni lo de su salud.";

    private final Analisis8020Finder analisis;
    private final InterruptoresDeSer interruptores;

    public ConsultarAnalisis8020Herramienta(Analisis8020Finder analisis, InterruptoresDeSer interruptores) {
        this.analisis = analisis;
        this.interruptores = interruptores;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        if (!interruptores.analisisPrendido()) {
            return ResultadoHerramienta.fallo("El analisis 80/20 esta en mantenimiento por ahora. Sus cifras se ven en Plan, en Tu 80/20.");
        }
        try {
            Optional<AnalisisLeido> semana = analisis.ultimoListo(actorId, Tipo.SEMANA);
            Optional<AnalisisLeido> mes = analisis.ultimoListo(actorId, Tipo.MES);
            if (semana.isEmpty() && mes.isEmpty()) {
                return ResultadoHerramienta.exito(sinAnalisis());
            }
            StringBuilder texto = new StringBuilder();
            semana.ifPresent(leido -> texto.append(texto(leido, "su ultima semana")));
            mes.ifPresent(leido -> texto.append(texto(leido, "su ultimo mes")));
            return ResultadoHerramienta.exito(texto.append(COMO_USARLO).toString());
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer el analisis: {}", NOMBRE, falla.getClass().getSimpleName());
            return ResultadoHerramienta.fallo("No pude consultar su analisis 80/20 en este momento.");
        }
    }

    private static String sinAnalisis() {
        return "Todavia no tiene ningun analisis 80/20. Se genera solo al cerrar cada semana y cada mes del programa, si planifico lo "
                + "suficiente (al menos cinco acciones en tres dias distintos en una semana). Sus cifras al dia estan en Plan, en Tu 80/20.";
    }

    static String texto(AnalisisLeido leido, String de) {
        List<String> pasos = leido.texto().proximosPasos();
        StringBuilder texto = new StringBuilder("Analisis de ").append(de).append(" (empezo el ")
                .append(FechaDelPlan.legible(leido.inicio())).append("):")
                .append("\nLo que mas movio: ").append(leido.texto().loQueMasMovio())
                .append("\nLo que se te fue: ").append(leido.texto().loQueSeTeFue())
                .append("\nComo estuviste: ").append(leido.texto().comoEstuviste())
                .append("\nPara el proximo periodo:");
        pasos.forEach(paso -> texto.append("\n- ").append(paso));
        return texto.append("\n\n").toString();
    }
}
