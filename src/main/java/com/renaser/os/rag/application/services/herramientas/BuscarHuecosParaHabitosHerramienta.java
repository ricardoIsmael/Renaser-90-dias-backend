package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.application.ports.out.agenda.AgendaSemanalPort;
import com.renaser.os.rag.domain.model.agenda.AgendaOcupada;
import com.renaser.os.rag.domain.model.agenda.AgendaOcupada.Tramo;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * {@code buscar_huecos_para_habitos} (D-160): la persona cuenta a que hora esta ocupada y el
 * acompanante le sugiere cuando hacer cada habito. Es de solo lectura (R0): si hace falta mover un
 * horario, el modelo lo PROPONE con {@code proponer_cambio_de_horario}, que ya respeta la cuota y
 * las validaciones de {@code habits}, y la persona confirma con el boton.
 *
 * <p>Reparto del trabajo (patron "LLM-Modulo"): el modelo entiende la agenda dicha en palabras y la
 * pasa como tramos; esta clase cruza esos tramos con la franja de cada habito (inicio a limite) y
 * devuelve huecos y una hora sugerida ya calculados.
 *
 * <p>Si no se pasa {@code ocupado}, usa la agenda que la persona confirmo guardar (D-161,
 * {@code agenda_ocupada}) para ese dia de la semana. Lo que se pasa manda: es lo que dijo recien.
 *
 * <p>Corregido 2026-09-23: la primera version (D-160) no guardaba nada ("sin tablas"); el dueno
 * aprobo despues una tabla para poder sugerir sin volver a preguntar.
 *
 * <p>No inventa cuanto dura un habito: dice cuantos minutos libres hay y la persona decide.
 */
@Component
public class BuscarHuecosParaHabitosHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "buscar_huecos_para_habitos";
    public static final String ARGUMENTO_OCUPADO = "ocupado";
    public static final String ARGUMENTO_FECHA = "fecha";

    private static final int FIN_DEL_PROGRAMA = 90;
    private static final Logger log = LoggerFactory.getLogger(BuscarHuecosParaHabitosHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Cruza las horas en que la persona dice estar ocupada con la franja (inicio a limite) de cada habito de "
                    + "un dia, y devuelve que habitos chocan, el hueco libre dentro de su franja, una hora sugerida y "
                    + "las horas libres del dia. Usala cuando la persona cuente su agenda (\"trabajo de 9 a 6\") o pida "
                    + "ayuda para encajar sus habitos. NUNCA calcules tu los huecos. Si un habito no tiene hueco en su "
                    + "franja, ofrece moverlo con proponer_cambio_de_horario a una de las horas libres. Si no pasas "
                    + "'ocupado', usa las horas ocupadas que la persona guardo. No guarda nada.",
            List.of(new ParametroHerramienta(ARGUMENTO_OCUPADO, TipoParametroHerramienta.TEXTO,
                            "Tramos en que esta ocupada ese dia, en su hora local, HH:mm-HH:mm separados por coma. "
                                    + "Ejemplo: 09:00-13:00, 14:00-18:00. Un tramo nocturno como 23:00-06:00 vale. "
                                    + "Omitelo para usar sus horas ocupadas guardadas.",
                            false),
                    new ParametroHerramienta(ARGUMENTO_FECHA, TipoParametroHerramienta.TEXTO,
                            "Dia en formato yyyy-MM-dd. Omitelo para hoy; no calcules tu la fecha de hoy.", false)));

    private final ConsultarHorariosPort horariosPort;
    private final AgendaSemanalPort agendaPort;

    public BuscarHuecosParaHabitosHerramienta(ConsultarHorariosPort horariosPort, AgendaSemanalPort agendaPort) {
        this.horariosPort = horariosPort;
        this.agendaPort = agendaPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        String ocupado = invocacion.argumento(ARGUMENTO_OCUPADO);
        AgendaOcupada dicha;
        LocalDate fecha;
        try {
            dicha = ocupado == null || ocupado.isBlank() ? null : AgendaOcupada.leer(ocupado);
            fecha = fechaDe(invocacion.argumento(ARGUMENTO_FECHA));
        } catch (IllegalArgumentException mal) {
            return ResultadoHerramienta.fallo(mal.getMessage());
        }
        try {
            HorariosDelDia dia = horariosPort.deFecha(actorId, fecha);
            AgendaOcupada agenda = dicha != null ? dicha
                    : agendaPort.de(actorId).delDia(dia.fecha().getDayOfWeek());
            if (dicha == null && agenda.estaLibre()) {
                return ResultadoHerramienta.fallo("No tiene horas ocupadas guardadas para ese dia: preguntale a que "
                        + "hora esta ocupada y vuelve a llamar con 'ocupado'.");
            }
            return ResultadoHerramienta.exito(new InformeDeHuecos(dia, agenda).texto());
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException suspendida) {
            return ResultadoHerramienta.fallo("La cuenta esta suspendida: no puedo consultar sus horarios.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer los horarios ({})", NOMBRE, falla.getClass().getSimpleName());
            return ResultadoHerramienta.fallo("No pude consultar sus horarios en este momento.");
        }
    }

    private static LocalDate fechaDe(String argumento) {
        try {
            return argumento == null || argumento.isBlank() ? null : LocalDate.parse(argumento.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La fecha tiene que venir como yyyy-MM-dd (por ejemplo 2026-09-24).");
        }
    }

    /** El texto que ve el modelo: una linea por habito con lo ya calculado. */
    private record InformeDeHuecos(HorariosDelDia dia, AgendaOcupada agenda) {

        String texto() {
            if (dia.diaPrograma() < 0 || dia.diaPrograma() > FIN_DEL_PROGRAMA) {
                return "El " + dia.fecha() + " queda fuera de sus 90 dias de programa: no hay habitos que encajar.";
            }
            StringBuilder texto = new StringBuilder("Huecos del ").append(dia.fecha())
                    .append(" (dia ").append(dia.diaPrograma()).append(" del programa). Ocupada: ")
                    .append(tramos(agenda.ocupados())).append(".\nHoras libres del dia: ")
                    .append(tramos(agenda.libresEntre(0, AgendaOcupada.FIN_DEL_DIA))).append(".\n");
            dia.habitos().forEach(habito -> texto.append(lineaDe(habito)).append('\n'));
            return texto.toString().stripTrailing();
        }

        private String lineaDe(HorarioDeHabito habito) {
            String cabeza = "habito_id=" + habito.habitoId() + " | " + habito.titulo();
            if (habito.pausado() || habito.apagado()) {
                return cabeza + " | no cuenta ese dia (" + (habito.pausado() ? "pausado" : "apagado") + ")";
            }
            if (habito.horaDisparo() == null) {
                // Sin hora fija no hay franja que proteger: sugerir "00:00" seria absurdo.
                return cabeza + " | sin hora fija: le sirve cualquiera de las horas libres del dia";
            }
            int inicio = AgendaOcupada.minutos(habito.horaDisparo());
            int limite = habito.horaLimite() == null ? AgendaOcupada.FIN_DEL_DIA : AgendaOcupada.minutos(habito.horaLimite());
            return cabeza + " | franja " + new Tramo(inicio, limite).texto() + " | " + veredicto(inicio, limite);
        }

        private String veredicto(int inicio, int limite) {
            List<Tramo> libres = agenda.libresEntre(inicio, limite);
            if (libres.isEmpty()) {
                return "sin hueco en su franja: para cumplirlo habria que mover el horario";
            }
            if (libres.size() == 1 && libres.get(0).minutos() == limite - inicio) {
                return "sin choque: le queda libre toda su franja, sugerida=" + AgendaOcupada.hora(inicio);
            }
            // El primer hueco: mas temprano suele pagar mas puntos (la escala baja con la hora).
            Tramo mejor = libres.get(0);
            return "choca en parte | libre dentro de su franja: " + tramos(libres) + " | sugerida="
                    + AgendaOcupada.hora(mejor.desde()) + " (" + mejor.minutos() + " min libres)";
        }

        private static String tramos(List<Tramo> tramos) {
            return tramos.isEmpty() ? "ninguna" : tramos.stream().map(Tramo::texto).collect(Collectors.joining(", "));
        }
    }
}
