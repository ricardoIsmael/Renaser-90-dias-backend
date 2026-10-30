package com.renaser.os.rag.domain.model.agenda;

import com.renaser.os.shared.domain.TramoDelDia;
import com.renaser.os.shared.domain.TramosUnidos;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Las horas en que la persona dijo estar ocupada un dia, y los huecos que le quedan. Es solo
 * aritmetica de horas, a proposito: los modelos se equivocan justo en esto (razonamiento temporal y
 * restricciones), asi que el modelo entiende "trabajo de 9 a 6" y el codigo calcula (D-160).
 *
 * <p>Los minutos van de 0 a 1440 (1440 = fin del dia) y los tramos son {@code [desde, hasta)},
 * ordenados y sin pisarse. Leida para UN dia ({@link #leer}), un tramo que cruza la medianoche
 * ({@code 22:00-06:00}) se parte en dos dentro de ese mismo dia: es la foto que la persona describe.
 * Para una agenda de la semana, la madrugada va al dia siguiente ({@link #leerConMedianoche}).
 */
public record AgendaOcupada(List<Tramo> ocupados) {

    public AgendaOcupada {
        ocupados = unir(Objects.requireNonNull(ocupados, "ocupados es obligatorio"));
        if (ocupados.size() > MAXIMO_TRAMOS) {
            throw new IllegalArgumentException("Son demasiados tramos: junta los contiguos (maximo " + MAXIMO_TRAMOS + ").");
        }
    }

    public static final int FIN_DEL_DIA = 24 * 60;
    public static final int MAXIMO_TRAMOS = 12;

    public static AgendaOcupada libre() {
        return new AgendaOcupada(List.of());
    }

    /** Un tramo {@code [desde, hasta)} en minutos del dia. */
    public record Tramo(int desde, int hasta) {

        public Tramo {
            if (desde < 0 || hasta > FIN_DEL_DIA || desde >= hasta) {
                throw new IllegalArgumentException("Tramo invalido: " + desde + "-" + hasta);
            }
        }

        public int minutos() {
            return hasta - desde;
        }

        public String texto() {
            return hora(desde) + "-" + hora(hasta);
        }
    }

    /**
     * {@code "09:00-13:00, 14:00-18:00"}. {@code IllegalArgumentException} con un mensaje para el
     * modelo si no se entiende: mejor pedirle que lo reformule que adivinar.
     */
    public static AgendaOcupada leer(String texto) {
        Lectura lectura = leerConMedianoche(texto);
        List<Tramo> todos = new ArrayList<>(lectura.delDia().ocupados());
        todos.addAll(lectura.delDiaSiguiente().ocupados());
        return new AgendaOcupada(todos);
    }

    /**
     * Lo que cae en el dia y, aparte, la madrugada que un tramo nocturno deja en el dia siguiente:
     * "lunes 23:00-06:00" es lunes de 23:00 a 24:00 y martes de 00:00 a 06:00.
     */
    public static Lectura leerConMedianoche(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Faltan los tramos en que esta ocupada, como 09:00-18:00.");
        }
        String[] partes = texto.split("[,;]");
        if (partes.length > MAXIMO_TRAMOS) {
            throw new IllegalArgumentException("Son demasiados tramos: junta los contiguos (maximo " + MAXIMO_TRAMOS + ").");
        }
        List<Tramo> delDia = new ArrayList<>();
        List<Tramo> siguiente = new ArrayList<>();
        for (String parte : partes) {
            agregarTramo(parte.strip(), delDia, siguiente);
        }
        return new Lectura(new AgendaOcupada(delDia), new AgendaOcupada(siguiente));
    }

    public record Lectura(AgendaOcupada delDia, AgendaOcupada delDiaSiguiente) {
    }

    /** Suma los tramos de otra agenda del mismo dia. */
    public AgendaOcupada mas(AgendaOcupada otra) {
        List<Tramo> todos = new ArrayList<>(ocupados);
        todos.addAll(otra.ocupados());
        return new AgendaOcupada(todos);
    }

    public boolean estaLibre() {
        return ocupados.isEmpty();
    }

    /** {@code 09:00-13:00, 14:00-18:00}, o "nada" si esta libre. */
    public String texto() {
        return ocupados.isEmpty() ? "nada" : String.join(", ", ocupados.stream().map(Tramo::texto).toList());
    }

    /** Lo libre dentro de {@code [desde, hasta)}, en orden. */
    public List<Tramo> libresEntre(int desde, int hasta) {
        return aTramos(new TramosUnidos(aTramosDelDia(ocupados)).libresEntre(desde, hasta));
    }

    public static int minutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    public static String hora(int minutos) {
        return "%02d:%02d".formatted(minutos / 60, minutos % 60);
    }

    private static void agregarTramo(String parte, List<Tramo> delDia, List<Tramo> siguiente) {
        String[] extremos = parte.split("-");
        if (extremos.length != 2) {
            throw new IllegalArgumentException("No entendi el tramo '" + parte + "': usa HH:mm-HH:mm.");
        }
        int desde = minutosDe(extremos[0].strip());
        int hasta = minutosDe(extremos[1].strip());
        if (desde == hasta) {
            throw new IllegalArgumentException("El tramo '" + parte + "' empieza y termina a la misma hora.");
        }
        if (desde < hasta) {
            delDia.add(new Tramo(desde, hasta));
            return;
        }
        if (desde < FIN_DEL_DIA) {
            delDia.add(new Tramo(desde, FIN_DEL_DIA));
        }
        if (hasta > 0) {
            siguiente.add(new Tramo(0, hasta));
        }
    }

    private static int minutosDe(String hora) {
        if ("24:00".equals(hora)) {
            return FIN_DEL_DIA;
        }
        try {
            return minutos(LocalTime.parse(hora.length() == 4 ? "0" + hora : hora));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("No entendi la hora '" + hora + "': usa HH:mm, por ejemplo 09:00.");
        }
    }

    /** Ordena y junta los que se pisan o se tocan: 09:00-12:00 y 11:00-13:00 son 09:00-13:00. */
    private static List<Tramo> unir(List<Tramo> tramos) {
        return List.copyOf(aTramos(new TramosUnidos(aTramosDelDia(tramos)).tramos()));
    }

    /** El algoritmo vive en {@code shared} (spec 015); el {@link Tramo} de acá sigue siendo el de esta agenda. */
    private static List<TramoDelDia> aTramosDelDia(List<Tramo> tramos) {
        return tramos.stream().map(tramo -> new TramoDelDia(tramo.desde(), tramo.hasta())).toList();
    }

    private static List<Tramo> aTramos(List<TramoDelDia> tramos) {
        return tramos.stream().map(tramo -> new Tramo(tramo.desde(), tramo.hasta())).toList();
    }
}
