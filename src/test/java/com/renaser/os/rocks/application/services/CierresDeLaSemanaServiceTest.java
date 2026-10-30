package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort;
import com.renaser.os.habits.api.EvaluacionDelDiaPort.MarcaDeCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.cierre.CierreDeAccion;
import com.renaser.os.rocks.domain.model.cierre.MotivoDeAccionPendiente;
import com.renaser.os.rocks.domain.model.cierre.PlanDeManana;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.ANA;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.conCierre;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.cumplida;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendiente;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.respuesta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Los cierres de una semana para las fichas (spec 017, FR-006). Miércoles 28 de octubre a las 22:00 en Lima (03:00 UTC del 29):
 * la semana en curso es la del 26, y «hoy» NO es el 29.
 */
class CierresDeLaSemanaServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 27);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 10, 12);
    private static final Instant NOCHE_EN_LIMA = Instant.parse("2026-10-29T03:00:00Z");

    private final ConsultarProgresoParticipanteRocksPort progreso = mock(ConsultarProgresoParticipanteRocksPort.class);
    private final LoadRocaDiariaPort cargar = mock(LoadRocaDiariaPort.class);
    private final EvaluacionDelDiaPort marcas = mock(EvaluacionDelDiaPort.class);
    private final CierresDeLaSemanaService servicio = new CierresDeLaSemanaService(progreso, cargar, marcas, FixedClock.at(NOCHE_EN_LIMA));

    private void persona(boolean suspendido, LocalDate diaUno) {
        when(progreso.deParticipante(ANA)).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(17, diaUno, LIMA, RolParticipante.TRAINEE, suspendido, true)));
    }

    @Test
    @DisplayName("la semana en curso trae los dias hasta hoy: el lunes cerrado, el martes sin cerrar y hoy todavia abierto")
    void semanaEnCurso() {
        persona(false, DIA_UNO);
        RocaDiaria lunesPendiente = conCierre(pendiente(LUNES, 1, "Revisar las facturas", LocalTime.of(20, 30)),
                new CierreDeAccion(respuesta(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA, "Se alargó la reunión"), NOCHE_EN_LIMA));
        RocaDiaria lunesCumplida = cumplida(LUNES, 2, "Enviar la propuesta", LocalTime.of(10, 0), 8);
        when(cargar.deParticipanteEntreFechas(ANA, LUNES, HOY)).thenReturn(List.of(lunesPendiente, lunesCumplida));
        when(marcas.marcasEntre(ANA, LUNES, HOY)).thenReturn(List.of(new MarcaDeCierre(LUNES, null, NOCHE_EN_LIMA)));

        CierresDeLaSemana semana = servicio.semanaDe(ANA, null);

        assertThat(semana.semana()).isEqualTo(LUNES);
        assertThat(semana.primeraSemana()).isEqualTo(DIA_UNO);
        assertThat(semana.dias()).extracting(d -> d.fecha(), d -> d.estado())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(LUNES, "CERRADO"),
                        org.assertj.core.groups.Tuple.tuple(MARTES, "SIN_CERRAR"),
                        org.assertj.core.groups.Tuple.tuple(HOY, "ABIERTO"));
        assertThat(semana.dias().get(0).acciones()).extracting(a -> a.titulo())
                .containsExactly("Enviar la propuesta", "Revisar las facturas");
        assertThat(semana.dias().get(0).acciones().get(1).cierre().motivo()).isEqualTo("SIN_TIEMPO");
        assertThat(semana.resumen().cerrados()).isEqualTo(1);
        assertThat(semana.resumen().sinCerrar()).isEqualTo(1);
        assertThat(semana.resumen().motivos()).extracting(m -> m.motivo(), m -> m.veces())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SIN_TIEMPO", 1));
    }

    @Test
    @DisplayName("una respuesta vieja de una accion que se cumplio despues de cerrar no se muestra ni cuenta")
    void laCumplidaNoMuestraSuRespuesta() {
        persona(false, DIA_UNO);
        RocaDiaria cumplidaDespues = conCierre(cumplida(LUNES, 1, "Llamar", null, 5),
                new CierreDeAccion(respuesta(MotivoDeAccionPendiente.PARA_DESPUES, PlanDeManana.DEJARLA, null), NOCHE_EN_LIMA));
        when(cargar.deParticipanteEntreFechas(ANA, LUNES, HOY)).thenReturn(List.of(cumplidaDespues));
        when(marcas.marcasEntre(ANA, LUNES, HOY)).thenReturn(List.of());

        CierresDeLaSemana semana = servicio.semanaDe(ANA, null);

        assertThat(semana.dias().get(0).acciones().get(0).cierre()).isNull();
        assertThat(semana.resumen().motivos()).isEmpty();
    }

    @Test
    @DisplayName("los motivos se cuentan de mas a menos: «no me alcanzo el tiempo» cuatro veces")
    void motivosQueSeRepiten() {
        persona(false, DIA_UNO);
        CierreDeAccion sinTiempo = new CierreDeAccion(respuesta(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA, null), NOCHE_EN_LIMA);
        CierreDeAccion sinEnergia = new CierreDeAccion(respuesta(MotivoDeAccionPendiente.SIN_ENERGIA, PlanDeManana.MAS_CORTA, null), NOCHE_EN_LIMA);
        when(cargar.deParticipanteEntreFechas(ANA, LUNES, HOY)).thenReturn(List.of(
                conCierre(pendiente(LUNES, 1, "a", null), sinTiempo), conCierre(pendiente(MARTES, 1, "b", null), sinTiempo),
                conCierre(pendiente(MARTES, 2, "c", null), sinEnergia), conCierre(pendiente(HOY, 1, "d", null), sinTiempo)));
        when(marcas.marcasEntre(ANA, LUNES, HOY)).thenReturn(List.of());

        assertThat(servicio.semanaDe(ANA, null).resumen().motivos()).extracting(m -> m.motivo(), m -> m.veces())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SIN_TIEMPO", 3),
                        org.assertj.core.groups.Tuple.tuple("SIN_ENERGIA", 1));
    }

    @Test
    @DisplayName("sin Dia 1 elegido no se inventa un programa: ni dias ni primera semana, y no se lee nada")
    void sinDiaUno() {
        persona(false, null);

        CierresDeLaSemana semana = servicio.semanaDe(ANA, null);

        assertThat(semana.dias()).isEmpty();
        assertThat(semana.primeraSemana()).isNull();
        assertThat(semana.resumen().cerrados()).isZero();
        verifyNoInteractions(marcas);
    }

    @Test
    @DisplayName("una semana pasada completa, pedida por su lunes")
    void semanaPasada() {
        persona(false, DIA_UNO);
        LocalDate lunesPasado = LocalDate.of(2026, 10, 19);
        when(cargar.deParticipanteEntreFechas(any(), any(), any())).thenReturn(List.of());
        when(marcas.marcasEntre(any(), any(), any())).thenReturn(List.of());

        CierresDeLaSemana semana = servicio.semanaDe(ANA, lunesPasado);

        assertThat(semana.semana()).isEqualTo(lunesPasado);
        assertThat(semana.dias()).hasSize(7);
        assertThat(semana.resumen().sinCerrar()).isEqualTo(7);
    }

    @Test
    @DisplayName("una semana futura, anterior a la primera o que no empieza en lunes es 400")
    void semanasInvalidas() {
        persona(false, DIA_UNO);
        assertThatThrownBy(() -> servicio.semanaDe(ANA, LocalDate.of(2026, 11, 2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.semanaDe(ANA, LocalDate.of(2026, 10, 5))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.semanaDe(ANA, HOY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una cuenta suspendida no se lee; una persona sin participacion es 404")
    void suspendidaOSinParticipacion() {
        persona(true, DIA_UNO);
        assertThatThrownBy(() -> servicio.semanaDe(ANA, null)).isInstanceOf(NotAuthorizedException.class);

        when(progreso.deParticipante(ANA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.semanaDe(ANA, null)).isInstanceOf(NoSuchElementException.class);
    }
}
