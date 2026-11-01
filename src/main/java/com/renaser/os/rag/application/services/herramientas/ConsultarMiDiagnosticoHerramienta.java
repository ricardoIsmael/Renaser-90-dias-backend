package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.diagnostico.ConsultarDiagnosticoDeLaPersonaPort;
import com.renaser.os.rag.domain.model.diagnostico.DiagnosticoDeLaPersona;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code consultar_mi_diagnostico} (R0, solo lectura, spec 026 y decisión 026-8 B del dueño: SER lee el diagnóstico completo): todas las respuestas del
 * diagnóstico inicial de la persona, con las palabras de ella. Reemplaza al Mapa de Renacimiento como fuente de «qué se propuso».
 *
 * <p>Son datos sensibles (ingresos, emociones, situaciones personales): se usan para acompañarla mejor, con tacto, y no se repiten a nadie más.
 */
@Component
public class ConsultarMiDiagnosticoHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_mi_diagnostico";

    private static final Logger log = LoggerFactory.getLogger(ConsultarMiDiagnosticoHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve el diagnostico inicial que la persona completo al entrar: como esta hoy en sus relaciones, su salud, sus emociones, su disciplina, su "
                    + "economia y su tiempo; que quiere conseguir en cada area, cual es su prioridad principal y su primera accion, y que le cuesta cuando no "
                    + "actua. Usala cuando pregunte por sus objetivos o metas, cuando se desanime o quiera dejar, o cuando quieras conectar un habito o una "
                    + "accion con lo que ella misma escribio. Es informacion personal y sensible: usala con tacto, con sus palabras, y no la repitas "
                    + "textual si no hace falta. Si no tiene diagnostico, lo dice.");

    private final ConsultarDiagnosticoDeLaPersonaPort diagnosticoPort;

    public ConsultarMiDiagnosticoHerramienta(ConsultarDiagnosticoDeLaPersonaPort diagnosticoPort) {
        this.diagnosticoPort = diagnosticoPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        DiagnosticoDeLaPersona diagnostico;
        try {
            diagnostico = diagnosticoPort.de(actorId);
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer el diagnostico", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude leer su diagnostico en este momento.");
        }
        return ResultadoHerramienta.exito(texto(diagnostico));
    }

    static String texto(DiagnosticoDeLaPersona d) {
        if (!d.asignado()) {
            return "La persona no tiene diagnostico: completo la ficha inicial estandar. No inventes lo que escribiria en uno.";
        }
        if (d.secciones().isEmpty()) {
            return "La persona todavia no empezo su diagnostico.";
        }
        StringBuilder sb = new StringBuilder("Diagnostico inicial de la persona")
                .append(d.completado() ? " (completo)" : " (incompleto: todavia lo esta respondiendo)").append(".\n");
        if (d.prioridad() != null) {
            sb.append("Prioridad principal: ").append(nombreDelEje(d.prioridad())).append(".\n");
        }
        for (DiagnosticoDeLaPersona.Seccion seccion : d.secciones()) {
            sb.append('\n').append(seccion.titulo()).append(":\n");
            for (DiagnosticoDeLaPersona.Pregunta pregunta : seccion.preguntas()) {
                sb.append("- ").append(plano(pregunta.texto())).append(" -> ")
                        .append(pregunta.respuesta() == null ? "(sin responder)" : plano(pregunta.respuesta())).append('\n');
            }
        }
        return sb.toString();
    }

    private static String nombreDelEje(String eje) {
        return switch (eje) {
            case "CUERPO" -> "Salud y bienestar";
            case "TRABAJO" -> "Riqueza y negocios";
            case "RELACIONES" -> "Relaciones";
            default -> eje;
        };
    }

    private static String plano(String t) {
        return t.replaceAll("\\s+", " ").trim();
    }
}
