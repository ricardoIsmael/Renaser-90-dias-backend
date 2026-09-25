package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MedicionDeLaPersonaTest {

    /** Viernes: la ventana vigente va del viernes 18 al jueves 24. */
    private static final LocalDate HOY = LocalDate.of(2026, 9, 25);
    private static final LocalDate VIERNES_25 = LocalDate.of(2026, 9, 25);
    private static final Instant AHORA = Instant.parse("2026-09-26T05:25:00Z");
    private static final CalendarioDeMedicion PROGRAMA_LARGO =
            new CalendarioDeMedicion(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 29), List.of());

    private static CumplimientoDelDia dia(LocalDate fecha, int programados, int cumplidos) {
        return new CumplimientoDelDia(fecha, programados, cumplidos, 0, 0);
    }

    @Test
    void laVentanaVigenteSonLosSieteDiasCerradosQueTerminanAyer() {
        VentanaDelSemaforo vigente = new MedicionDeLaPersona(PROGRAMA_LARGO, Map.of(), HOY).vigente();

        assertThat(vigente.desde()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(vigente.hasta()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(vigente.dias()).hasSize(7);
    }

    @Test
    void promediaSoloLosDiasConAlgoProgramado() {
        Map<LocalDate, CumplimientoDelDia> filas = new HashMap<>();
        filas.put(LocalDate.of(2026, 9, 18), dia(LocalDate.of(2026, 9, 18), 4, 4));   // 100
        filas.put(LocalDate.of(2026, 9, 19), dia(LocalDate.of(2026, 9, 19), 3, 2));   // 67
        filas.put(LocalDate.of(2026, 9, 20), dia(LocalDate.of(2026, 9, 20), 0, 0));   // sin datos

        VentanaDelSemaforo vigente = new MedicionDeLaPersona(PROGRAMA_LARGO, filas, HOY).vigente();

        assertThat(vigente.porcentaje()).isEqualByComparingTo("83.5");
        assertThat(vigente.color()).isEqualTo(ColorSemaforo.VERDE);
        assertThat(vigente.diasConDatos()).isEqualTo(2);
        assertThat(estados(vigente)).containsExactly(EstadoDiaSemaforo.MEDIDO, EstadoDiaSemaforo.MEDIDO,
                EstadoDiaSemaforo.SIN_DATOS, EstadoDiaSemaforo.PENDIENTE, EstadoDiaSemaforo.PENDIENTE,
                EstadoDiaSemaforo.PENDIENTE, EstadoDiaSemaforo.PENDIENTE);
    }

    @Test
    void sinNingunDiaConDatosEsSinDatosYNoVerde() {
        VentanaDelSemaforo vigente = new MedicionDeLaPersona(PROGRAMA_LARGO, Map.of(), HOY).vigente();

        assertThat(vigente.porcentaje()).isNull();
        assertThat(vigente.color()).isEqualTo(ColorSemaforo.SIN_DATOS);
    }

    @Test
    void losDiasFueraDelProgramaYLosPausadosNoCuentan() {
        UserId staff = UserId.of(UUID.randomUUID());
        PausaDeMedicion pausa = PausaDeMedicion.iniciar(PausaId.of(UUID.randomUUID()), staff,
                LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 24), AHORA);
        CalendarioDeMedicion calendario =
                new CalendarioDeMedicion(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 12, 18), List.of(pausa));
        Map<LocalDate, CumplimientoDelDia> filas = new HashMap<>();
        for (LocalDate f = LocalDate.of(2026, 9, 18); f.isBefore(HOY); f = f.plusDays(1)) {
            filas.put(f, dia(f, 2, 0));   // una fila vieja no se muestra si el día no se mide
        }

        VentanaDelSemaforo vigente = new MedicionDeLaPersona(calendario, filas, HOY).vigente();

        assertThat(estados(vigente)).containsExactly(EstadoDiaSemaforo.FUERA_DEL_PROGRAMA,
                EstadoDiaSemaforo.FUERA_DEL_PROGRAMA, EstadoDiaSemaforo.MEDIDO, EstadoDiaSemaforo.MEDIDO,
                EstadoDiaSemaforo.MEDIDO, EstadoDiaSemaforo.PAUSADO, EstadoDiaSemaforo.PAUSADO);
        assertThat(vigente.diasConDatos()).isEqualTo(3);
        assertThat(vigente.color()).isEqualTo(ColorSemaforo.ROJO);
    }

    @Test
    void laFotoDelCierreTomaLosSieteDiasDeLaSemana() {
        Map<LocalDate, CumplimientoDelDia> filas = new HashMap<>();
        for (LocalDate f = LocalDate.of(2026, 9, 19); !f.isAfter(VIERNES_25); f = f.plusDays(1)) {
            filas.put(f, dia(f, 5, 4));   // 80 % cada día
        }
        filas.put(LocalDate.of(2026, 9, 20), dia(LocalDate.of(2026, 9, 20), 0, 0));   // domingo sin nada

        FotoSemanal foto = new MedicionDeLaPersona(PROGRAMA_LARGO, filas, LocalDate.of(2026, 9, 26))
                .fotoDe(VIERNES_25, AHORA);

        assertThat(foto.semanaDesde()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(foto.diasMedidos()).isEqualTo(7);
        assertThat(foto.diasConDatos()).isEqualTo(6);
        assertThat(foto.porcentaje()).isEqualByComparingTo(new BigDecimal("80.0"));
        assertThat(foto.color()).isEqualTo(ColorSemaforo.VERDE);
        assertThat(foto.tuvoDiasMedidos()).isTrue();
    }

    @Test
    void unaSemanaEnteraPausadaSeCierraSinDiasMedidos() {
        UserId staff = UserId.of(UUID.randomUUID());
        PausaDeMedicion pausa = PausaDeMedicion.iniciar(PausaId.of(UUID.randomUUID()), staff,
                LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 30), AHORA);
        CalendarioDeMedicion calendario =
                new CalendarioDeMedicion(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 29), List.of(pausa));

        FotoSemanal foto = new MedicionDeLaPersona(calendario, Map.of(), LocalDate.of(2026, 9, 26))
                .fotoDe(VIERNES_25, AHORA);

        assertThat(foto.tuvoDiasMedidos()).isFalse();
        assertThat(foto.porcentaje()).isNull();
        assertThat(foto.color()).isEqualTo(ColorSemaforo.SIN_DATOS);
    }

    @Test
    void unaSemanaCerradaMuestraElResultadoDeLaFoto() {
        Map<LocalDate, CumplimientoDelDia> filas = Map.of(VIERNES_25, dia(VIERNES_25, 1, 0));
        FotoSemanal foto = new FotoSemanal(VIERNES_25, LocalDate.of(2026, 9, 19), new BigDecimal("91.0"), 5, 7,
                ReglaDelSemaforo.VERSION_FORMULA, AHORA);

        VentanaDelSemaforo semana = new MedicionDeLaPersona(PROGRAMA_LARGO, filas, LocalDate.of(2026, 9, 28))
                .semana(VIERNES_25, foto);

        assertThat(semana.cerrada()).isTrue();
        assertThat(semana.porcentaje()).isEqualByComparingTo("91.0");
        assertThat(semana.color()).isEqualTo(ColorSemaforo.VERDE);
        assertThat(semana.dias().getLast().porcentaje()).isZero();
    }

    private static List<EstadoDiaSemaforo> estados(VentanaDelSemaforo ventana) {
        return ventana.dias().stream().map(DiaDelSemaforo::estado).toList();
    }
}
