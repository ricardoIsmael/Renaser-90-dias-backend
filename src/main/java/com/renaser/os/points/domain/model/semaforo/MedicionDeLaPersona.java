package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Lo que se sabe del semáforo de una persona en un momento: qué días se miden, los días ya
 * calculados y su "hoy" local. Con eso arma, sin consultar nada, la ventana vigente (ayer y los seis
 * días anteriores), una semana sábado→viernes y la foto del cierre.
 *
 * @param filas    días calculados (fila de {@code semaforo_dias}) por fecha
 * @param hoyLocal hoy en la zona de la persona (regla 02 §1), nunca la fecha del servidor
 */
public record MedicionDeLaPersona(CalendarioDeMedicion calendario, Map<LocalDate, CumplimientoDelDia> filas,
                                  LocalDate hoyLocal) {

    public MedicionDeLaPersona {
        Objects.requireNonNull(calendario, "calendario es obligatorio");
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
        filas = filas == null ? Map.of() : Map.copyOf(filas);
    }

    /** Primer día de la ventana vigente: siete días cerrados terminando ayer. */
    public static LocalDate desdeVigente(LocalDate hoyLocal) {
        return hoyLocal.minusDays(SemanaDelSemaforo.DIAS);
    }

    public VentanaDelSemaforo vigente() {
        return armar(desdeVigente(hoyLocal), hoyLocal.minusDays(1));
    }

    /** Una semana sábado→viernes. Si ya se cerró, el promedio es el de la foto: lo que se informó. */
    public VentanaDelSemaforo semana(LocalDate semanaHasta, FotoSemanal foto) {
        VentanaDelSemaforo calculada = armar(SemanaDelSemaforo.desde(semanaHasta), semanaHasta);
        if (foto == null) {
            return calculada;
        }
        return new VentanaDelSemaforo(calculada.desde(), calculada.hasta(), foto.porcentaje(), foto.color(),
                foto.diasConDatos(), true, calculada.dias());
    }

    /**
     * La foto del cierre con los días ya calculados. Un día medido sin fila no debería existir al
     * cerrar (el barrido calcula los días antes); si faltara, cuenta como medido sin datos, nunca
     * como cumplido.
     */
    public FotoSemanal fotoDe(LocalDate semanaHasta, Instant ahora) {
        LocalDate semanaDesde = SemanaDelSemaforo.desde(semanaHasta);
        int diasMedidos = 0;
        List<Integer> porcentajes = new ArrayList<>();
        for (LocalDate fecha = semanaDesde; !fecha.isAfter(semanaHasta); fecha = fecha.plusDays(1)) {
            if (!calendario.seMide(fecha)) {
                continue;
            }
            diasMedidos++;
            porcentajeGuardado(fecha).ifPresent(porcentajes::add);
        }
        BigDecimal porcentaje = ReglaDelSemaforo.promedio(porcentajes).orElse(null);
        return new FotoSemanal(semanaHasta, semanaDesde, porcentaje, porcentajes.size(), diasMedidos,
                ReglaDelSemaforo.VERSION_FORMULA, ahora);
    }

    private Optional<Integer> porcentajeGuardado(LocalDate fecha) {
        CumplimientoDelDia dia = filas.get(fecha);
        return dia == null ? Optional.empty() : dia.porcentaje();
    }

    private VentanaDelSemaforo armar(LocalDate desde, LocalDate hasta) {
        List<DiaDelSemaforo> dias = new ArrayList<>(SemanaDelSemaforo.DIAS);
        List<Integer> porcentajes = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            DiaDelSemaforo dia = diaDe(fecha);
            if (dia.estado() == EstadoDiaSemaforo.MEDIDO) {
                porcentajes.add(dia.porcentaje());
            }
            dias.add(dia);
        }
        BigDecimal promedio = ReglaDelSemaforo.promedio(porcentajes).orElse(null);
        return new VentanaDelSemaforo(desde, hasta, promedio, ReglaDelSemaforo.colorDe(promedio),
                porcentajes.size(), false, dias);
    }

    /**
     * Un día sin medir (fuera del programa o pausado) nunca muestra porcentaje, aunque haya una fila
     * vieja; uno que todavía no cerró o que el barrido no calculó es PENDIENTE.
     */
    private DiaDelSemaforo diaDe(LocalDate fecha) {
        if (!calendario.seMide(fecha)) {
            return DiaDelSemaforo.sinPorcentaje(fecha, calendario.estadoSinCalculo(fecha));
        }
        CumplimientoDelDia fila = filas.get(fecha);
        if (fila == null || !fecha.isBefore(hoyLocal)) {
            return DiaDelSemaforo.sinPorcentaje(fecha, EstadoDiaSemaforo.PENDIENTE);
        }
        return fila.aDia();
    }
}
