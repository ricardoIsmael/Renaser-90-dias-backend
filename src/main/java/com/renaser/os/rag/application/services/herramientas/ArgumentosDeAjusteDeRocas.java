package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort.Cambio;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanMalFormadoException;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lee y escribe los argumentos de {@code proponer_agregar_accion} y {@code proponer_editar_objetivo_semanal}
 * (D-177). Son argumentos sueltos (no un JSON como el plan del dia): una accion o un cambio caben en
 * cinco campos y el modelo se equivoca menos escribiendolos asi.
 *
 * <p>Mismo criterio que {@link PlanDeRocasJson}: solo forma, con el motivo del rechazo ya escrito para el
 * modelo; y lo que se guarda en la propuesta se reescribe desde lo validado, con la fecha y la semana ya
 * resueltas, para que al confirmar se ejecute exactamente lo que la persona vio.
 */
final class ArgumentosDeAjusteDeRocas {

    static final String FECHA = "fecha";
    static final String EJE = "eje";
    static final String TITULO = "titulo";
    static final String INICIO = "inicio";
    static final String FIN = "fin";
    static final String OBSTACULO = "obstaculo";
    static final String CONTINGENCIA = "contingencia";
    static final String AUTOEVALUACION_INICIO = "autoevaluacionInicio";
    static final String SEMANA = "semana";
    static final String SEMANA_SIGUIENTE = "siguiente";
    static final String SEMANA_ACTUAL = "actual";

    private ArgumentosDeAjusteDeRocas() {
    }

    /** @param fecha {@code null} si el modelo no la mando: quien llama pone manana */
    record AccionPedida(LocalDate fecha, AccionDelPlan accion) {
    }

    /** @param siguiente la semana de programa que empieza manana (D-192), en vez de la que esta en curso */
    record EdicionPedida(String eje, Cambio cambio, boolean siguiente) {
    }

    record EdicionGuardada(int semana, String eje, Cambio cambio) {
    }

    static AccionPedida leerAccion(InvocacionHerramienta invocacion, List<String> ejesValidos) {
        String eje = eje(invocacion, ejesValidos);
        String titulo = texto(invocacion, TITULO);
        if (titulo == null) {
            throw new PlanMalFormadoException("Falta el titulo de la accion.");
        }
        return new AccionPedida(fecha(invocacion.argumento(FECHA)),
                new AccionDelPlan(eje, titulo, hora(invocacion, INICIO), hora(invocacion, FIN)));
    }

    static Map<String, String> accionNormalizada(LocalDate fecha, AccionDelPlan accion) {
        Map<String, String> argumentos = new LinkedHashMap<>();
        argumentos.put(FECHA, fecha.toString());
        argumentos.put(EJE, accion.eje());
        argumentos.put(TITULO, accion.titulo());
        ponerSiHay(argumentos, INICIO, accion.inicio() == null ? null : accion.inicio().format(PlanDeRocasJson.HORA));
        ponerSiHay(argumentos, FIN, accion.fin() == null ? null : accion.fin().format(PlanDeRocasJson.HORA));
        return argumentos;
    }

    static EdicionPedida leerEdicion(InvocacionHerramienta invocacion, List<String> ejesValidos) {
        String semana = texto(invocacion, SEMANA);
        boolean siguiente = semana != null && SEMANA_SIGUIENTE.equals(semana.toLowerCase(Locale.ROOT));
        if (semana != null && !siguiente && !SEMANA_ACTUAL.equals(semana.toLowerCase(Locale.ROOT))) {
            throw new PlanMalFormadoException("La semana tiene que ser 'actual' o 'siguiente'.");
        }
        return new EdicionPedida(eje(invocacion, ejesValidos), cambio(invocacion), siguiente);
    }

    static EdicionGuardada leerEdicionGuardada(InvocacionHerramienta invocacion, List<String> ejesValidos) {
        try {
            int semana = Integer.parseInt(invocacion.argumento(SEMANA));
            return new EdicionGuardada(semana, eje(invocacion, ejesValidos), cambio(invocacion));
        } catch (NumberFormatException sinSemana) {
            throw new PlanMalFormadoException("La edicion guardada no tiene la semana.");
        }
    }

    static Map<String, String> edicionNormalizada(int semana, String eje, Cambio cambio) {
        Map<String, String> argumentos = new LinkedHashMap<>();
        argumentos.put(SEMANA, Integer.toString(semana));
        argumentos.put(EJE, eje);
        ponerSiHay(argumentos, TITULO, cambio.titulo());
        ponerSiHay(argumentos, OBSTACULO, cambio.obstaculo());
        ponerSiHay(argumentos, CONTINGENCIA, cambio.contingencia());
        ponerSiHay(argumentos, AUTOEVALUACION_INICIO,
                cambio.autoevaluacionInicio() == null ? null : cambio.autoevaluacionInicio().toString());
        return argumentos;
    }

    private static Cambio cambio(InvocacionHerramienta invocacion) {
        Cambio cambio = new Cambio(texto(invocacion, TITULO), texto(invocacion, OBSTACULO),
                texto(invocacion, CONTINGENCIA), autoevaluacion(texto(invocacion, AUTOEVALUACION_INICIO)));
        if (cambio.titulo() == null && cambio.obstaculo() == null && cambio.contingencia() == null
                && cambio.autoevaluacionInicio() == null) {
            throw new PlanMalFormadoException("No hay nada que cambiar: manda al menos uno de titulo, obstaculo, "
                    + "contingencia o autoevaluacionInicio.");
        }
        return cambio;
    }

    private static Integer autoevaluacion(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            // "6" y tambien "6.0": el modelo suele mandar asi un entero (mismo criterio que CheckInRadarPedido).
            int valor = new BigDecimal(texto).intValueExact();
            if (valor >= PlanificarRocasPort.AUTOEVALUACION_MINIMA && valor <= PlanificarRocasPort.AUTOEVALUACION_MAXIMA) {
                return valor;
            }
        } catch (NumberFormatException | ArithmeticException noEsEntero) {
            // cae al rechazo de abajo, con el mismo mensaje
        }
        throw new PlanMalFormadoException("autoevaluacionInicio tiene que ser un numero entero del "
                + PlanificarRocasPort.AUTOEVALUACION_MINIMA + " al " + PlanificarRocasPort.AUTOEVALUACION_MAXIMA + ".");
    }

    private static String eje(InvocacionHerramienta invocacion, List<String> ejesValidos) {
        String eje = texto(invocacion, EJE);
        String normalizado = eje == null ? null : eje.toUpperCase(Locale.ROOT);
        if (normalizado == null || !ejesValidos.contains(normalizado)) {
            throw new PlanMalFormadoException("El eje tiene que ser uno de: " + String.join(", ", ejesValidos) + ".");
        }
        return normalizado;
    }

    /** {@code null} si no viene o viene en blanco; recortado si viene. */
    private static String texto(InvocacionHerramienta invocacion, String campo) {
        String valor = invocacion.argumento(campo);
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static LocalTime hora(InvocacionHerramienta invocacion, String campo) {
        String valor = texto(invocacion, campo);
        if (valor == null) {
            return null;
        }
        try {
            return LocalTime.parse(valor, PlanDeRocasJson.HORA);
        } catch (DateTimeParseException noEsHora) {
            throw new PlanMalFormadoException("'" + campo + "' tiene que ir como HH:MM (por ejemplo 07:30).");
        }
    }

    private static LocalDate fecha(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException noEsFecha) {
            throw new PlanMalFormadoException("La fecha tiene que ir como AAAA-MM-DD (por ejemplo 2026-09-24).");
        }
    }

    private static void ponerSiHay(Map<String, String> argumentos, String campo, String valor) {
        if (valor != null) {
            argumentos.put(campo, valor);
        }
    }
}
