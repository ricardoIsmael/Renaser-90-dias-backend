package com.renaser.os.onboarding.domain.model.pagos;

import com.renaser.os.shared.domain.UserId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * El cronograma de pagos de una persona (spec 032): una moneda y de 0 a {@value #MAXIMO_DE_CUOTAS} cuotas con fecha fija. No es una tabla propia: la moneda vive en
 * el estado de ingreso de la persona y las cuotas en {@code cuotas_de_pago}; esta clase solo reúne las reglas que las atraviesan.
 *
 * <p>Las reglas del dueño (2026-11-01): lo pagado no se toca (una cuota pagada no se edita ni se quita), Administración ajusta a mano lo pendiente y <b>nada se recalcula
 * solo</b>. Un cronograma <b>nuevo</b> puede nacer sin cuotas (D-315, decisión del dueño del 2026-11-02: los pagos son asíncronos, Administración las registra después
 * desde la ficha); pero una vez que tiene cuotas, quitar una nunca lo deja vacío —debe quedar al menos una—: para quitar el cronograma entero se elige «sin cronograma».
 */
public final class CronogramaDePagos {

    public static final int MAXIMO_DE_CUOTAS = 60;

    private static final Comparator<Cuota> EN_ORDEN = Comparator.comparing(Cuota::venceEl).thenComparing(Cuota::creadaEn).thenComparing(Cuota::id);
    private static final Comparator<Cuota> POR_CONTENIDO = Comparator.comparing(Cuota::venceEl).thenComparing(Cuota::monto);

    private final Moneda moneda;
    private final List<Cuota> cuotas;

    private CronogramaDePagos(Moneda moneda, List<Cuota> cuotas) {
        this.moneda = Objects.requireNonNull(moneda, "La moneda es obligatoria");
        this.cuotas = new ArrayList<>(cuotas);
        this.cuotas.sort(EN_ORDEN);
    }

    /**
     * Un cronograma nuevo. Puede nacer sin cuotas (D-315): la persona queda «con cronograma» y Administración las registra después.
     *
     * @throws IllegalArgumentException (400) si lleva más de {@value #MAXIMO_DE_CUOTAS} cuotas
     */
    public static CronogramaDePagos nuevo(Moneda moneda, List<Cuota> cuotas) {
        List<Cuota> lasCuotas = cuotas == null ? List.of() : cuotas;
        if (lasCuotas.size() > MAXIMO_DE_CUOTAS) {
            throw new IllegalArgumentException("Un cronograma lleva hasta " + MAXIMO_DE_CUOTAS + " cuotas");
        }
        return new CronogramaDePagos(moneda, lasCuotas);
    }

    /** Solo para el servicio: el cronograma que ya está guardado. */
    public static CronogramaDePagos guardado(Moneda moneda, List<Cuota> cuotas) {
        return new CronogramaDePagos(moneda, cuotas);
    }

    public Moneda moneda() {
        return moneda;
    }

    /** Las cuotas, de la fecha más cercana a la más lejana. */
    public List<Cuota> cuotas() {
        return List.copyOf(cuotas);
    }

    public boolean hayPagadas() {
        return cuotas.stream().anyMatch(Cuota::pagada);
    }

    public boolean todasPagadas() {
        return !cuotas.isEmpty() && cuotas.stream().allMatch(Cuota::pagada);
    }

    /**
     * Suma una cuota pendiente.
     *
     * @throws IllegalStateException (409) si ya tiene {@value #MAXIMO_DE_CUOTAS}
     */
    public Cuota agregar(Cuota nueva) {
        if (cuotas.size() >= MAXIMO_DE_CUOTAS) {
            throw new IllegalStateException("Un cronograma lleva como máximo " + MAXIMO_DE_CUOTAS + " cuotas");
        }
        cuotas.add(Objects.requireNonNull(nueva, "La cuota es obligatoria"));
        cuotas.sort(EN_ORDEN);
        return nueva;
    }

    /**
     * Cambia la fecha y el monto de una cuota pendiente.
     *
     * @throws NoSuchElementException (404) si no es una cuota de este cronograma
     * @throws IllegalStateException  (409) si ya está pagada
     */
    public Cuota editar(UUID id, LocalDate venceEl, BigDecimal monto) {
        int lugar = lugarDe(id);
        Cuota editada = cuotas.get(lugar).conFechaYMonto(venceEl, monto);
        cuotas.set(lugar, editada);
        cuotas.sort(EN_ORDEN);
        return editada;
    }

    /**
     * Quita una cuota pendiente.
     *
     * @return la cuota quitada
     * @throws NoSuchElementException (404) si no es una cuota de este cronograma
     * @throws IllegalStateException  (409) si ya está pagada o si es la última
     */
    public Cuota quitar(UUID id) {
        Cuota cuota = cuotas.get(lugarDe(id));
        if (cuota.pagada()) {
            throw new IllegalStateException("Una cuota pagada no se quita: devuélvela a «Pendiente» si hace falta corregirla");
        }
        if (cuotas.size() == 1) {
            throw new IllegalStateException("Debe quedar al menos una cuota. Para quitar el cronograma elige «Sin cronograma»");
        }
        cuotas.remove(cuota);
        return cuota;
    }

    /**
     * La marca de Administración: pagada o pendiente. Idempotente (ver {@link Cuota#marcada}).
     *
     * @throws NoSuchElementException (404) si no es una cuota de este cronograma
     */
    public Cuota marcar(UUID id, EstadoDeCuota estado, UserId quien, Instant cuando) {
        int lugar = lugarDe(id);
        Cuota marcada = cuotas.get(lugar).marcada(estado, quien, cuando);
        cuotas.set(lugar, marcada);
        return marcada;
    }

    /**
     * Una huella de lo que la persona confirma: la moneda y las fechas y montos de todas las cuotas, sin el estado de pago (marcar un pago no cambia lo acordado). Dos
     * cronogramas con las mismas fechas y montos dan la misma huella, así que la confirmación vale mientras nadie cambie lo acordado y se vuelve a pedir en cuanto
     * Administración agrega, edita o quita una cuota.
     */
    public String huella() {
        StringBuilder acordado = new StringBuilder(moneda.name());
        cuotas.stream().sorted(POR_CONTENIDO)
                .forEach(cuota -> acordado.append('|').append(cuota.venceEl()).append(':').append(cuota.monto().toPlainString()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(acordado.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }

    private int lugarDe(UUID id) {
        for (int i = 0; i < cuotas.size(); i++) {
            if (cuotas.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new NoSuchElementException("Esa cuota no es de este cronograma");
    }
}
