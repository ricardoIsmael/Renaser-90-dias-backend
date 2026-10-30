package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort;
import com.renaser.os.habits.api.EvaluacionDelDiaPort.MarcaDeCierre;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.CerrarElDia;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.RespuestaDeAccion;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.AvanceDeLaSemana;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.VistaDelCierre;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.domain.model.cierre.CierreDeOtroDiaException;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.ANA;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.cumplida;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendiente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * «Cierra tu día» de la propia persona (spec 017): cerrar el de hoy, leer uno anterior y, sobre todo, que cerrar NUNCA toca lo
 * cumplido ni los puntos. El reloj está en la madrugada UTC: el 28 a las 22:00 en Lima ya es el 29 en UTC.
 */
class CierreDelDiaServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final Instant NOCHE_EN_LIMA = Instant.parse("2026-10-29T03:00:00Z");
    private static final Instant MADRUGADA_SIGUIENTE_EN_LIMA = Instant.parse("2026-10-29T06:00:00Z");
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 10, 12);

    private final ConsultarProgresoParticipanteRocksPort progreso = mock(ConsultarProgresoParticipanteRocksPort.class);
    private final LoadRocaDiariaPort cargar = mock(LoadRocaDiariaPort.class);
    private final SaveRocaDiariaPort guardar = mock(SaveRocaDiariaPort.class);
    private final EvaluacionDelDiaPort marcas = mock(EvaluacionDelDiaPort.class);

    private final RocaDiaria propuesta = cumplida(HOY, 1, "Enviar la propuesta", LocalTime.of(10, 0), 8);
    private final RocaDiaria llamada = pendiente(HOY, 2, "Llamar a mi mentor", LocalTime.of(17, 30));
    private final RocaDiaria facturas = pendiente(HOY, 3, "Revisar las facturas", LocalTime.of(20, 30));

    private CierreDelDiaService servicio(Instant ahora) {
        return new CierreDelDiaService(progreso, cargar, guardar, marcas, FixedClock.at(ahora));
    }

    private void persona(RolParticipante rol, boolean suspendido, boolean programaActivado, LocalDate diaUno) {
        when(progreso.deParticipante(ANA)).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(17, diaUno, LIMA, rol, suspendido, programaActivado)));
    }

    private void unAprendizEnSuDia17() {
        persona(RolParticipante.TRAINEE, false, true, DIA_UNO);
        when(cargar.deParticipanteYFecha(ANA, HOY)).thenReturn(List.of(propuesta, llamada, facturas));
    }

    private static RespuestaDeAccion responde(RocaDiaria roca, String motivo, String manana) {
        return new RespuestaDeAccion(roca.id().value(), motivo, manana, null);
    }

    private CerrarElDia cierreDeHoy(String notaDelDia) {
        return new CerrarElDia(ANA, HOY, List.of(responde(llamada, "SIN_ENERGIA", "MAS_CORTA"),
                responde(facturas, "SIN_TIEMPO", "OTRA_HORA")), notaDelDia);
    }

    @Test
    @DisplayName("cerrar guarda SOLO las columnas de cierre y la marca del dia, y nunca reescribe una roca entera")
    void cerrarNoReescribeLaRoca() {
        unAprendizEnSuDia17();
        when(marcas.marcasEntre(ANA, HOY, HOY)).thenReturn(List.of(new MarcaDeCierre(HOY, "Buen día", NOCHE_EN_LIMA)));
        when(cargar.deParticipanteEntreFechas(ANA, LocalDate.of(2026, 10, 26), HOY)).thenReturn(List.of(propuesta, llamada, facturas));

        VistaDelCierre vista = servicio(NOCHE_EN_LIMA).cerrar(cierreDeHoy("Buen día"));

        @SuppressWarnings("unchecked") ArgumentCaptor<List<RocaDiaria>> guardadas = ArgumentCaptor.forClass(List.class);
        verify(guardar).guardarSoloElCierre(guardadas.capture());
        assertThat(guardadas.getValue()).containsExactlyInAnyOrder(llamada, facturas);
        verify(guardar, never()).save(any());
        verify(guardar, never()).saveAll(any());
        verify(marcas).dejarCerrado(ANA, HOY, "Buen día");
        assertThat(propuesta.completada()).isTrue();
        assertThat(llamada.completada()).isFalse();
        assertThat(facturas.completada()).isFalse();
        assertThat(vista.dia().estado()).isEqualTo("CERRADO");
        assertThat(vista.puedeCerrar()).isTrue();
        assertThat(vista.semana()).isEqualTo(new AvanceDeLaSemana(1, 3));
    }

    @Test
    @DisplayName("la nota del dia se recorta y en blanco no se guarda")
    void notaEnBlanco() {
        unAprendizEnSuDia17();
        servicio(NOCHE_EN_LIMA).cerrar(cierreDeHoy("   "));
        verify(marcas).dejarCerrado(ANA, HOY, null);
    }

    @Test
    @DisplayName("«hoy» es el de la zona de la persona: el 29 de UTC todavia es el 28 en Lima, asi que es futuro (400)")
    void hoyEsElDeSuZona() {
        unAprendizEnSuDia17();
        CerrarElDia delDiaUtc = new CerrarElDia(ANA, LocalDate.of(2026, 10, 29), List.of(), null);

        assertThatThrownBy(() -> servicio(NOCHE_EN_LIMA).cerrar(delDiaUtc)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(guardar, marcas);
    }

    @Test
    @DisplayName("pasada la medianoche de Lima el 28 ya cerró: 409 y no se guarda NADA (no se reasigna el cierre al 29)")
    void cerrarTarde() {
        persona(RolParticipante.TRAINEE, false, true, DIA_UNO);

        assertThatThrownBy(() -> servicio(MADRUGADA_SIGUIENTE_EN_LIMA).cerrar(cierreDeHoy(null)))
                .isInstanceOf(CierreDeOtroDiaException.class).hasMessageContaining("Ese día ya cerró");
        verifyNoInteractions(guardar, marcas);
        verify(cargar, never()).deParticipanteYFecha(any(), any());
    }

    @Test
    @DisplayName("falta la respuesta de una pendiente: 400, sin marca y sin guardar nada")
    void faltaUnaRespuesta() {
        unAprendizEnSuDia17();
        CerrarElDia incompleto = new CerrarElDia(ANA, HOY, List.of(responde(llamada, "SIN_TIEMPO", "DEJARLA")), null);

        assertThatThrownBy(() -> servicio(NOCHE_EN_LIMA).cerrar(incompleto))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Revisar las facturas");
        verifyNoInteractions(guardar, marcas);
    }

    @Test
    @DisplayName("un motivo o un plan desconocido es 400")
    void valoresDesconocidos() {
        unAprendizEnSuDia17();
        CerrarElDia raro = new CerrarElDia(ANA, HOY, List.of(responde(llamada, "NO_SE", "DEJARLA"),
                responde(facturas, "SIN_TIEMPO", "DEJARLA")), null);

        assertThatThrownBy(() -> servicio(NOCHE_EN_LIMA).cerrar(raro)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(guardar, marcas);
    }

    @Test
    @DisplayName("un dia sin acciones se cierra igual: la marca existe aunque no haya nada que contar")
    void diaSinAcciones() {
        persona(RolParticipante.TRAINEE, false, true, DIA_UNO);
        when(cargar.deParticipanteYFecha(ANA, HOY)).thenReturn(List.of());

        servicio(NOCHE_EN_LIMA).cerrar(new CerrarElDia(ANA, HOY, List.of(), "Descansé"));

        verify(guardar).guardarSoloElCierre(List.of());
        verify(marcas).dejarCerrado(ANA, HOY, "Descansé");
    }

    @Test
    @DisplayName("cuenta suspendida, personal sin su programa activado o sin participacion: no cierra nada")
    void quienNoPuede() {
        persona(RolParticipante.TRAINEE, true, true, DIA_UNO);
        assertThatThrownBy(() -> servicio(NOCHE_EN_LIMA).cerrar(cierreDeHoy(null))).isInstanceOf(NotAuthorizedException.class);

        persona(RolParticipante.MENTOR, false, false, DIA_UNO);
        assertThatThrownBy(() -> servicio(NOCHE_EN_LIMA).cerrar(cierreDeHoy(null))).isInstanceOf(NotAuthorizedException.class);

        when(progreso.deParticipante(ANA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio(NOCHE_EN_LIMA).cerrar(cierreDeHoy(null))).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(guardar, marcas);
    }

    @Test
    @DisplayName("el personal con su programa activado cierra su dia como cualquier aprendiz")
    void personalConProgramaCierra() {
        persona(RolParticipante.MENTOR, false, true, DIA_UNO);
        when(cargar.deParticipanteYFecha(ANA, HOY)).thenReturn(List.of());

        servicio(NOCHE_EN_LIMA).cerrar(new CerrarElDia(ANA, HOY, List.of(), null));

        verify(marcas).dejarCerrado(ANA, HOY, null);
    }

    @Test
    @DisplayName("consultar sin fecha es hoy: abierto si no hay marca, y se puede cerrar")
    void consultarHoyAbierto() {
        unAprendizEnSuDia17();
        when(marcas.marcasEntre(ANA, HOY, HOY)).thenReturn(List.of());
        when(cargar.deParticipanteEntreFechas(ANA, LocalDate.of(2026, 10, 26), HOY)).thenReturn(List.of(propuesta, llamada));

        VistaDelCierre vista = servicio(NOCHE_EN_LIMA).consultar(ANA, null);

        assertThat(vista.dia().fecha()).isEqualTo(HOY);
        assertThat(vista.dia().estado()).isEqualTo("ABIERTO");
        assertThat(vista.dia().acciones()).extracting(a -> a.titulo())
                .containsExactly("Enviar la propuesta", "Llamar a mi mentor", "Revisar las facturas");
        assertThat(vista.puedeCerrar()).isTrue();
        assertThat(vista.semana()).isEqualTo(new AvanceDeLaSemana(1, 2));
    }

    @Test
    @DisplayName("un dia anterior solo se lee: «sin cerrar» si nadie lo cerro, sin semana y sin poder cerrarlo")
    void consultarAyer() {
        persona(RolParticipante.TRAINEE, false, true, DIA_UNO);
        LocalDate ayer = HOY.minusDays(1);
        when(cargar.deParticipanteYFecha(ANA, ayer)).thenReturn(List.of());
        when(marcas.marcasEntre(ANA, ayer, ayer)).thenReturn(List.of());

        VistaDelCierre vista = servicio(NOCHE_EN_LIMA).consultar(ANA, ayer);

        assertThat(vista.dia().estado()).isEqualTo("SIN_CERRAR");
        assertThat(vista.puedeCerrar()).isFalse();
        assertThat(vista.semana()).isNull();
    }

    @Test
    @DisplayName("un dia que todavia no llega es 400")
    void consultarManana() {
        persona(RolParticipante.TRAINEE, false, true, DIA_UNO);
        assertThatThrownBy(() -> servicio(NOCHE_EN_LIMA).consultar(ANA, HOY.plusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sin acciones planificadas esta semana, o sin Dia 1 elegido, no hay «tu semana» (nunca «0 de 0»)")
    void sinAvance() {
        persona(RolParticipante.TRAINEE, false, true, DIA_UNO);
        when(cargar.deParticipanteYFecha(ANA, HOY)).thenReturn(List.of());
        when(marcas.marcasEntre(ANA, HOY, HOY)).thenReturn(List.of());
        when(cargar.deParticipanteEntreFechas(eq(ANA), any(), any())).thenReturn(List.of());
        assertThat(servicio(NOCHE_EN_LIMA).consultar(ANA, HOY).semana()).isNull();

        persona(RolParticipante.TRAINEE, false, true, null);
        assertThat(servicio(NOCHE_EN_LIMA).consultar(ANA, HOY).semana()).isNull();
    }
}
