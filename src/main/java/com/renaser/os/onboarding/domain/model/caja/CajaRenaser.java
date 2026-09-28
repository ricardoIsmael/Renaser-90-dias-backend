package com.renaser.os.onboarding.domain.model.caja;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * La Caja Renaser de un aprendiz en un instante (D-219): sus pasos guardados más lo que se deriva de su
 * situación. Es una foto: se arma con el reloj y no cambia; cada operación ({@link AccionDeCaja}) produce un
 * paso nuevo que se guarda, y la próxima lectura arma otra foto.
 *
 * <p><b>El orden de la derivación</b> (spec §2), en un solo lugar:
 * <ol>
 *   <li>entregada es entregada, aunque después se suspenda la cuenta;</li>
 *   <li>si no es un aprendiz con el programa activado, no aplica;</li>
 *   <li>cuenta suspendida → en pausa; país distinto de Perú → fuera de la app;</li>
 *   <li>si el Admin ya hizo algo con el envío actual, manda ese paso;</li>
 *   <li>antes del Día 8, no aplica; después, en revisión si cumplió el requisito o el Admin la aprobó, y si
 *       no, en evaluación.</li>
 * </ol>
 *
 * <p><b>El Día 8 es el del aprendiz, no el del servidor</b> (regla 02, E-91): se calcula con la fecha local
 * en SU zona. A las 03:00 UTC del 8 de octubre, alguien de Lima que empezó el 1 sigue en su Día 7.
 */
public final class CajaRenaser {

    /** El flujo del motor de formularios donde vive la caja (V82). */
    public static final String FLUJO = "caja_renaser";
    public static final int DIA_DE_LA_CAJA = 8;
    private static final int ULTIMO_DIA = 90;

    private final UserId aprendizId;
    private final SituacionDelAprendiz situacion;
    private final List<PasoDeCaja> pasos;
    private final Instant ahora;
    private final int envioActual;
    private final int diaDelPrograma;
    private final BigDecimal cumplimiento;
    private final EstadoCaja estado;

    private CajaRenaser(UserId aprendizId, SituacionDelAprendiz situacion, List<PasoDeCaja> pasos, Instant ahora) {
        this.aprendizId = Objects.requireNonNull(aprendizId, "aprendizId");
        this.situacion = Objects.requireNonNull(situacion, "situacion");
        this.ahora = Objects.requireNonNull(ahora, "ahora");
        this.pasos = pasos.stream().sorted(Comparator.comparing(PasoDeCaja::en)
                .thenComparing(PasoDeCaja::envio).thenComparing(PasoDeCaja::tipo)).toList();
        this.envioActual = this.pasos.stream().filter(p -> p.tipo().estadoDelEnvio().isPresent())
                .mapToInt(PasoDeCaja::envio).max().orElse(1);
        this.diaDelPrograma = situacion.aplica() ? diaLocal(situacion, ahora) : 0;
        this.cumplimiento = diaDelPrograma >= DIA_DE_LA_CAJA
                ? CumplimientoFaseUno.de(situacion.primeraFecha(), situacion.habitos()).orElse(null)
                : null;
        this.estado = derivar();
    }

    public static CajaRenaser de(UserId aprendizId, SituacionDelAprendiz situacion, List<PasoDeCaja> pasos,
                                 Instant ahora) {
        return new CajaRenaser(aprendizId, situacion, List.copyOf(pasos), ahora);
    }

    public UserId aprendizId() {
        return aprendizId;
    }

    public Instant ahora() {
        return ahora;
    }

    public EstadoCaja estado() {
        return estado;
    }

    /** El día del programa en la fecha local del aprendiz, de 0 a 90 (0 si la caja no le aplica). */
    public int diaDelPrograma() {
        return diaDelPrograma;
    }

    /** El cumplimiento de los días 1 a 7; vacío antes del Día 8 o si no tuvo nada programado. */
    public Optional<BigDecimal> cumplimientoFaseUno() {
        return Optional.ofNullable(cumplimiento);
    }

    /** El número de envío en curso: 1, o el del último reenvío. */
    public int envioActual() {
        return envioActual;
    }

    /** El último paso de ese tipo en el envío actual. */
    public Optional<PasoDeCaja> ultimo(TipoPasoCaja tipo) {
        return pasos.stream().filter(p -> p.envio() == envioActual && p.tipo() == tipo)
                .reduce((primero, segundo) -> segundo);
    }

    /** Todos los pasos de todos los envíos, del más viejo al más nuevo. */
    public List<PasoDeCaja> pasos() {
        return pasos;
    }

    /** Qué falta para poder marcarla enviada. */
    public List<FaltaParaEnviar> faltaParaEnviar(boolean contenidoCompleto) {
        return Stream.of(
                        contenidoCompleto ? null : FaltaParaEnviar.CONTENIDO,
                        ultimo(TipoPasoCaja.FOTO).isPresent() ? null : FaltaParaEnviar.FOTO,
                        ultimo(TipoPasoCaja.COMPROBANTE).isPresent() ? null : FaltaParaEnviar.COMPROBANTE)
                .filter(Objects::nonNull).toList();
    }

    private EstadoCaja derivar() {
        Optional<EstadoCaja> delEnvio = pasos.stream()
                .filter(p -> p.envio() == envioActual)
                .map(p -> p.tipo().estadoDelEnvio())
                .flatMap(Optional::stream)
                .reduce((primero, segundo) -> segundo);
        if (delEnvio.filter(EstadoCaja.ENTREGADA::equals).isPresent()) {
            return EstadoCaja.ENTREGADA;
        }
        if (!situacion.aplica()) {
            return EstadoCaja.NO_APLICA;
        }
        if (situacion.suspendido()) {
            return EstadoCaja.EN_PAUSA;
        }
        if (!situacion.delPeru()) {
            return EstadoCaja.FUERA_DE_LA_APP;
        }
        return delEnvio.orElseGet(this::segunElRequisito);
    }

    private EstadoCaja segunElRequisito() {
        if (diaDelPrograma < DIA_DE_LA_CAJA) {
            return EstadoCaja.NO_APLICA;
        }
        boolean aprobada = pasos.stream().anyMatch(p -> p.tipo() == TipoPasoCaja.APROBADA);
        return aprobada || CumplimientoFaseUno.cumple(cumplimiento) ? EstadoCaja.POR_REVISAR
                : EstadoCaja.EN_EVALUACION;
    }

    private static int diaLocal(SituacionDelAprendiz situacion, Instant ahora) {
        LocalDate hoy = ahora.atZone(situacion.zona()).toLocalDate();
        long dia = ChronoUnit.DAYS.between(situacion.primeraFecha(), hoy) + 1;
        return (int) Math.max(0, Math.min(ULTIMO_DIA, dia));
    }
}
