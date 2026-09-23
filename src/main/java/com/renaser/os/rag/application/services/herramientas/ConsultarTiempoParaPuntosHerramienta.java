package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.TramoPuntos;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code consultar_tiempo_para_puntos} (R1: calcula, nunca escribe). Responde "¿llego a tiempo?"
 * y "¿cuanto pierdo si lo hago a las 9?" con las cuentas ya hechas en codigo: el modelo es malo
 * con horas y fechas (plan del acompanante, §3.1), asi que solo parafrasea.
 *
 * <p>La escala de puntos la resuelve {@code habits} y llega como tramos; el reloj es el
 * {@link Clock} inyectado y las horas se dicen en la zona del aprendiz ({@link MomentoDelAprendiz},
 * regla 02).
 */
@Component
public class ConsultarTiempoParaPuntosHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_tiempo_para_puntos";
    public static final String ARGUMENTO_HORA = "hora";

    /** 24 h, con o sin cero adelante ("21:00", "09:30", "9:30"). Ni "9pm" ni segundos. */
    private static final Pattern FORMATO_HORA = Pattern.compile("([01]?\\d|2[0-3]):([0-5]\\d)");

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Calcula, para cada habito de hoy que todavia puede entregar, cuantos puntos paga si lo entrega "
                    + "ahora, hasta que hora mantiene ese puntaje y cuantos minutos le quedan, cuanto pagaria "
                    + "despues, a que hora vence y cual se le vence primero. Usala cuando pregunte si llega a "
                    + "tiempo, cuanto tiempo le queda, que le conviene hacer primero o cuanto pierde si lo hace "
                    + "mas tarde (\"¿cuanto pierdo si lo hago a las 9?\"). Las horas y los minutos ya vienen "
                    + "calculados en su hora local: repitelos, no hagas cuentas de horas por tu cuenta.",
            List.of(new ParametroHerramienta(ARGUMENTO_HORA, TipoParametroHerramienta.TEXTO,
                    "Opcional. La hora a la que piensa entregarlo, en SU hora local y en formato de 24 horas "
                            + "HH:mm (21:00 para las 9 de la noche). Omitila para calcular desde ahora.", false)));

    private final ConsultarAgendaHabitosPort agendaHabitosPort;
    private final Clock clock;

    public ConsultarTiempoParaPuntosHerramienta(ConsultarAgendaHabitosPort agendaHabitosPort, Clock clock) {
        this.agendaHabitosPort = agendaHabitosPort;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        String hora = invocacion.argumento(ARGUMENTO_HORA);
        if (hora == null || hora.isBlank()) {
            return responder(actorId, ConsultarTiempoParaPuntosHerramienta::desdeAhora);
        }
        return leerHora(hora)
                .map(horaLocal -> responder(actorId, momento -> aLaHora(momento, horaLocal)))
                .orElseGet(() -> ResultadoHerramienta.fallo("No entendi la hora. Pasala en formato de 24 horas "
                        + "HH:mm, por ejemplo 21:00 para las 9 de la noche."));
    }

    private static Optional<LocalTime> leerHora(String hora) {
        Matcher formato = FORMATO_HORA.matcher(hora.trim());
        return formato.matches()
                ? Optional.of(LocalTime.of(Integer.parseInt(formato.group(1)), Integer.parseInt(formato.group(2))))
                : Optional.empty();
    }

    /** Arma la respuesta: cabecera con la hora actual, una linea por habito y cual vence primero. */
    private ResultadoHerramienta responder(UserId actorId,
                                           Function<MomentoDelAprendiz, Function<PlazoDePuntos, String>> lineas) {
        List<PlazoDePuntos> plazos = entregablesDe(actorId);
        if (plazos.isEmpty()) {
            return ResultadoHerramienta.exito("No le queda ningun habito por entregar hoy: ya no tiene puntos "
                    + "en juego.");
        }
        MomentoDelAprendiz momento = new MomentoDelAprendiz(clock.now(), agendaHabitosPort.zonaDe(actorId));
        StringBuilder texto = new StringBuilder("Hora actual del aprendiz: ").append(momento.horaDe(momento.ahora()))
                .append('.');
        Function<PlazoDePuntos, String> lineaDe = lineas.apply(momento);
        plazos.forEach(plazo -> texto.append('\n').append(lineaDe.apply(plazo)));
        return ResultadoHerramienta.exito(texto.append('\n').append(primeroEnVencer(plazos, momento)).toString());
    }

    /**
     * Los que todavia pagan algo, del que vence antes al que vence despues (los que no vencen, al
     * final). Uno con 0 en juego ya paso su plazo aunque el barrido todavia no lo haya expirado:
     * no hay nada que calcularle.
     */
    private List<PlazoDePuntos> entregablesDe(UserId actorId) {
        return agendaHabitosPort.deHoyDe(actorId).stream()
                .filter(habito -> habito.sigueEnJuego() && habito.puntosEnJuego() > 0)
                .map(PlazoDePuntos::new)
                .sorted(Comparator.comparing(PlazoDePuntos::vence, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static Function<PlazoDePuntos, String> desdeAhora(MomentoDelAprendiz momento) {
        return plazo -> {
            if (plazo.vence() == null) {
                return encabezadoDe(plazo) + " | no vence: no tiene horario";
            }
            return encabezadoDe(plazo) + escalaDesdeAhora(plazo, momento) + venceEn(plazo, momento);
        };
    }

    /** "paga 10 hasta las 20:32 (faltan 25 min), despues 9 y va bajando hasta 6", si hay un tramo despues. */
    private static String escalaDesdeAhora(PlazoDePuntos plazo, MomentoDelAprendiz momento) {
        Optional<TramoPuntos> actual = plazo.tramoEn(momento.ahora());
        Optional<TramoPuntos> siguiente = actual.flatMap(plazo::siguienteA);
        if (siguiente.isEmpty()) {
            return "";
        }
        Instant cambio = actual.get().hasta();
        String bajando = plazo.puntosMinimos() < siguiente.get().puntos()
                ? " y va bajando hasta " + plazo.puntosMinimos() : "";
        return " | paga " + actual.get().puntos() + " hasta las " + momento.horaDe(cambio) + " (faltan "
                + momento.faltaPara(cambio) + "), despues " + siguiente.get().puntos() + bajando;
    }

    private static Function<PlazoDePuntos, String> aLaHora(MomentoDelAprendiz momento, LocalTime hora) {
        Instant instante = momento.proxima(hora);
        return plazo -> encabezadoDe(plazo) + " | a las " + momento.horaDe(instante) + resultadoA(plazo, instante)
                + (plazo.vence() == null ? " | no vence: no tiene horario" : venceEn(plazo, momento));
    }

    private static String resultadoA(PlazoDePuntos plazo, Instant instante) {
        int despues = plazo.puntosEn(instante);
        if (despues == 0) {
            return " ya estaria vencido: pierde los " + plazo.puntosAhora();
        }
        int pierde = Math.max(0, plazo.puntosAhora() - despues);
        return " pagaria " + despues + (pierde > 0 ? " (pierde " + pierde + ")" : " (no pierde nada)");
    }

    private static String encabezadoDe(PlazoDePuntos plazo) {
        HabitoDelDia habito = plazo.habito();
        return "id=" + habito.registroId() + " | " + habito.titulo() + " | paga_ahora=" + plazo.puntosAhora();
    }

    private static String venceEn(PlazoDePuntos plazo, MomentoDelAprendiz momento) {
        return " | vence a las " + momento.horaDe(plazo.vence()) + " (faltan " + momento.faltaPara(plazo.vence()) + ")";
    }

    private static String primeroEnVencer(List<PlazoDePuntos> plazos, MomentoDelAprendiz momento) {
        return plazos.stream().filter(plazo -> plazo.vence() != null).findFirst()
                .map(plazo -> "Primero en vencer: " + plazo.habito().titulo() + ", a las "
                        + momento.horaDe(plazo.vence()) + " (faltan " + momento.faltaPara(plazo.vence()) + ").")
                .orElse("Ninguno vence hoy: no tienen horario.");
    }
}
