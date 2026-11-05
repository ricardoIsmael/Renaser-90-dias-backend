package com.renaser.os.habits.domain.model.carrera;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lo que el teléfono manda al terminar una carrera con GPS (spec 042, FR-016): la clave de idempotencia, el resumen, los tramos y,
 * si la persona la guarda, la ruta YA SIN la zona de privacidad (la que se guarda: el teléfono la manda completa en una
 * {@link RutaEnviada} y el servidor la recorta antes de construir este objeto, FR-043). Este objeto garantiza los TOPES DE ENTRADA (FR-026): fuera de ellos el pedido es inválido y
 * es un 400 con un texto que la persona lee. Si la carrera CUENTA para el hábito (velocidad, más de 60 km, hora del teléfono) no
 * lo decide este objeto sino {@code PoliticaKilometros}: esa carrera se guarda igual.
 *
 * <p>{@link #huella()} es el SHA-256 del cuerpo canónico: los campos en un orden fijo y los números normalizados (la distancia con
 * un decimal, los instantes en milisegundos). Dos envíos con la misma {@code carreraId} y el mismo contenido tienen la misma huella
 * aunque el JSON venga con otro orden o con otros espacios (FR-019). La ruta que entra en la huella es la que MANDÓ el teléfono
 * ({@link #huellaDelEnvio(RutaEnviada)}), no la recortada: se compara lo que se envió.
 */
public record DatosDeCarrera(UUID carreraId, Instant iniciadaEn, Instant terminadaEn, int duracionActivaSeg,
                             BigDecimal distanciaMetros, Integer subidaMetros, Integer pasos, Integer caloriasAprox,
                             List<Tramo> tramos, Integer metrosSinGps, RutaDeCarrera ruta, String appVersion) {

    public static final int DURACION_MINIMA_SEG = 60;
    public static final int DURACION_MAXIMA_SEG = 12 * 3600;
    public static final BigDecimal DISTANCIA_MINIMA_M = new BigDecimal("100");
    public static final BigDecimal DISTANCIA_MAXIMA_M = new BigDecimal("100000");
    public static final int SUBIDA_MAXIMA_M = 5000;
    public static final int PASOS_MAXIMOS = 150_000;
    public static final int CALORIAS_MAXIMAS = 6000;
    public static final int TRAMOS_MAXIMOS = 61;
    public static final int LARGO_MAXIMO_DE_LA_VERSION = 20;

    public DatosDeCarrera {
        Objects.requireNonNull(carreraId, "Falta la clave de la carrera.");
        Objects.requireNonNull(iniciadaEn, "Falta cuándo empezó la carrera.");
        Objects.requireNonNull(terminadaEn, "Falta cuándo terminó la carrera.");
        Objects.requireNonNull(distanciaMetros, "Falta la distancia de la carrera.");
        distanciaMetros = distanciaMetros.setScale(1, RoundingMode.HALF_UP);
        tramos = tramos == null ? List.of() : List.copyOf(tramos);
        appVersion = appVersion == null || appVersion.isBlank() ? null : appVersion.strip();
        requireTiempos(iniciadaEn, terminadaEn, duracionActivaSeg);
        requireDistancia(distanciaMetros, metrosSinGps);
        requireEntre(subidaMetros, SUBIDA_MAXIMA_M, "La subida acumulada tiene");
        requireEntre(pasos, PASOS_MAXIMOS, "Los pasos tienen");
        requireEntre(caloriasAprox, CALORIAS_MAXIMAS, "Las calorías tienen");
        requireTramos(tramos);
        if (appVersion != null && appVersion.length() > LARGO_MAXIMO_DE_LA_VERSION) {
            throw new IllegalArgumentException("La versión de la app es demasiado larga.");
        }
    }

    /** El día de la carrera en la zona de la persona: el de su inicio, aunque termine pasada la medianoche (FR-021). */
    public LocalDate fechaEn(ZoneId zona) {
        return iniciadaEn.atZone(zona).toLocalDate();
    }

    /** Los km con dos decimales, como se guardan en el hábito. */
    public BigDecimal kilometros() {
        return distanciaMetros.divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);
    }

    /** Velocidad media en km/h: distancia sobre tiempo ACTIVO (las pausas no cuentan). */
    public double velocidadMediaKmH() {
        return distanciaMetros.doubleValue() / duracionActivaSeg * 3.6;
    }

    /** Ritmo medio en segundos por km, redondeado. */
    public int ritmoSegPorKm() {
        return (int) Math.round(duracionActivaSeg / (distanciaMetros.doubleValue() / 1000));
    }

    /** La misma carrera con otra ruta: la que queda después de quitar la zona de privacidad (FR-043). */
    public DatosDeCarrera conRuta(RutaDeCarrera otra) {
        return new DatosDeCarrera(carreraId, iniciadaEn, terminadaEn, duracionActivaSeg, distanciaMetros, subidaMetros, pasos,
                caloriasAprox, tramos, metrosSinGps, otra, appVersion);
    }

    /** SHA-256 (hex en minúsculas) del cuerpo canónico (FR-019), con la ruta de este objeto. */
    public String huella() {
        return digerir(canonico());
    }

    /**
     * La huella del envío: el cuerpo canónico con la ruta tal como la mandó el teléfono (completa). Sin ruta enviada, la de este
     * objeto ({@link #huella()}). Repetir el mismo pedido da la misma huella (FR-019).
     */
    public String huellaDelEnvio(RutaEnviada enviada) {
        return enviada == null ? huella() : digerir(canonico(enviada.polyline(), String.valueOf(enviada.puntos())));
    }

    private static String digerir(String canonico) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("La JVM no trae SHA-256", imposible);
        }
    }

    String canonico() {
        return ruta == null ? canonico("", "") : canonico(ruta.polyline(), String.valueOf(ruta.puntos()));
    }

    private String canonico(String polyline, String puntos) {
        String tramosCanonicos = tramos.stream().map(t -> t.km() + ":" + t.segundos()).collect(Collectors.joining(";"));
        return String.join("|", "v1", carreraId.toString(), String.valueOf(iniciadaEn.toEpochMilli()),
                String.valueOf(terminadaEn.toEpochMilli()), String.valueOf(duracionActivaSeg),
                distanciaMetros.stripTrailingZeros().toPlainString(), texto(subidaMetros), texto(pasos), texto(caloriasAprox),
                texto(metrosSinGps), tramosCanonicos, polyline, puntos, appVersion == null ? "" : appVersion);
    }

    private static String texto(Integer valor) {
        return valor == null ? "" : valor.toString();
    }

    private static void requireTiempos(Instant inicio, Instant fin, int activa) {
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("La carrera tiene que terminar después de empezar.");
        }
        long total = Duration.between(inicio, fin).toSeconds();
        if (total > DURACION_MAXIMA_SEG) {
            throw new IllegalArgumentException("Una carrera no puede durar más de 12 horas.");
        }
        if (activa < DURACION_MINIMA_SEG) {
            throw new IllegalArgumentException("La carrera es muy corta para guardarla: menos de 1 minuto.");
        }
        if (activa > total + 1) {
            throw new IllegalArgumentException("El tiempo activo no puede ser mayor que lo que duró la carrera.");
        }
    }

    private static void requireDistancia(BigDecimal metros, Integer sinGps) {
        if (metros.compareTo(DISTANCIA_MINIMA_M) < 0) {
            throw new IllegalArgumentException("La carrera es muy corta para guardarla: menos de 100 m.");
        }
        if (metros.compareTo(DISTANCIA_MAXIMA_M) > 0) {
            throw new IllegalArgumentException("Revisa la distancia: más de 100 km en una carrera no se puede guardar.");
        }
        if (sinGps != null && (sinGps < 0 || new BigDecimal(sinGps).compareTo(metros) > 0)) {
            throw new IllegalArgumentException("Los metros sin GPS tienen que estar entre cero y la distancia de la carrera.");
        }
    }

    private static void requireEntre(Integer valor, int maximo, String que) {
        if (valor != null && (valor < 0 || valor > maximo)) {
            throw new IllegalArgumentException(que + " que estar entre 0 y " + maximo + ".");
        }
    }

    private static void requireTramos(List<Tramo> tramos) {
        if (tramos.size() > TRAMOS_MAXIMOS) {
            throw new IllegalArgumentException("Una carrera no puede tener más de " + TRAMOS_MAXIMOS + " tramos.");
        }
        for (int i = 0; i < tramos.size(); i++) {
            Tramo tramo = Objects.requireNonNull(tramos.get(i), "Un tramo viene vacío.");
            if (tramo.km() != i + 1) {
                throw new IllegalArgumentException("Los tramos tienen que ir seguidos desde el km 1.");
            }
        }
    }
}
