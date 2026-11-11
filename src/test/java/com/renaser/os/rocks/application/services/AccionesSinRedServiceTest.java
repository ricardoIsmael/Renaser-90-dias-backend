package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocadiaria.AccionesSinRedUseCase.AccionMarcada;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase.CompletarRocaDiariaCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.CrearPlanDiarioCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.ItemRocaDiaria;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.TipoEvidenciaRoca;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.application.EntregaSinRedDePrueba;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.MotivoSinPuntos;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.ANA;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.cumplida;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendiente;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendienteCreadaEn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agendar y marcar las acciones del día sin red (spec 048, fase 3, FR-019). La persona lo hace el lunes 9 a las 21:40 de Lima (02:40 UTC
 * del 10, el día local ANTERIOR en UTC) y la app lo envía el martes 10 a las 08:00 de Lima (regla 02).
 */
class AccionesSinRedServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate EL_9 = LocalDate.of(2026, 11, 9);
    private static final LocalDate EL_10 = LocalDate.of(2026, 11, 10);
    private static final Instant HECHO = Instant.parse("2026-11-10T02:40:00Z");
    private static final Instant LLEGA = Instant.parse("2026-11-10T13:00:00Z");
    private static final UUID ENVIO = UUID.fromString("5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10");
    private static final HechoSinRed SIN_RED = new HechoSinRed(HECHO, ENVIO, OrigenDeLaHora.RELOJ_DEL_SISTEMA);

    private final ConsultarProgresoParticipanteRocksPort progreso = mock(ConsultarProgresoParticipanteRocksPort.class);
    private final LoadRocaDiariaPort cargar = mock(LoadRocaDiariaPort.class);
    private final CrearPlanDiarioUseCase crear = mock(CrearPlanDiarioUseCase.class);
    private final CompletarRocaDiariaUseCase completar = mock(CompletarRocaDiariaUseCase.class);

    private EntregaSinRedDePrueba entrega;
    private AccionesSinRedService servicio;

    @BeforeEach
    void preparar() {
        FixedClock reloj = FixedClock.at(LLEGA);
        entrega = EntregaSinRedDePrueba.nueva(reloj);
        servicio = new AccionesSinRedService(progreso, cargar, crear, completar, entrega.puerto(), reloj);
        when(progreso.deParticipante(ANA)).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(17, LocalDate.of(2026, 10, 24), LIMA, RolParticipante.TRAINEE, false, true)));
        when(cargar.deParticipanteYFecha(any(), any())).thenReturn(List.of());
        when(crear.crear(any(), any())).thenReturn(List.of());
        when(completar.completarConHoraReal(any(), any(), anyBoolean())).thenAnswer(inv -> pendiente(EL_9, 1, "hecha", null));
    }

    @AfterEach
    void terminar() {
        entrega.terminar();
    }

    private CrearPlanDiarioCommand agenda(LocalDate fecha) {
        return new CrearPlanDiarioCommand(ANA, fecha,
                List.of(new ItemRocaDiaria(EjeObjetivo.CUERPO, 1, "Salir a correr", null, 5, false, LocalTime.of(6, 0), null, List.of())));
    }

    private static CompletarRocaDiariaCommand marca(RocaDiaria roca) {
        return new CompletarRocaDiariaCommand(ANA, roca.id(), TipoEvidenciaRoca.TEXTO, null, null, "Lo hice", null, null, null, true, false);
    }

    private AccionMarcada marcarLaDe(LocalDate fecha, HechoSinRed envio) {
        RocaDiaria roca = pendiente(fecha, 1, "Salir a correr", null);
        when(cargar.byId(roca.id())).thenReturn(Optional.of(roca));
        return servicio.marcar(marca(roca), envio);
    }

    // ─── agendar ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("agendar mañana el lunes por la noche y llegar el martes: se aplica con la hora real (la noche del lunes), no con la de llegada")
    void agendaConLaHoraReal() { // spec 048 FR-019
        servicio.agendar(agenda(EL_10), SIN_RED);

        verify(crear).crear(any(CrearPlanDiarioCommand.class), eq(HECHO));
        assertThat(entrega.registro(ENVIO).orElseThrow().tipo()).isEqualTo("PLAN_AGENDAR");
        assertThat(entrega.registro(ENVIO).orElseThrow().recurso()).isEqualTo("plan-diario:2026-11-10");
    }

    @Test
    @DisplayName("P2: agendar un día que ya pasó al llegar no se aplica (FUERA_DE_SU_DIA) y no se crea nada")
    void unDiaQueYaPasoNoSeAgenda() { // spec 048 FR-019
        assertThatThrownBy(() -> servicio.agendar(agenda(EL_9), SIN_RED)).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.FUERA_DE_SU_DIA));

        verify(crear, never()).crear(any(), any());
        assertThat(entrega.noAplicado(ENVIO)).contains(CodigoNoAplicado.FUERA_DE_SU_DIA);
    }

    @Test
    @DisplayName("un día futuro ya armado con acciones ANTERIORES a lo que hiciste se reemplaza, como con red; si alguna cambió DESPUÉS, gana el servidor")
    void reemplazoYChoque() { // spec 048 FR-019
        RocaDiaria vieja = pendiente(EL_10, 1, "Armada antes", null);
        when(cargar.deParticipanteYFecha(ANA, EL_10)).thenReturn(List.of(vieja));
        servicio.agendar(agenda(EL_10), SIN_RED);
        verify(crear).crear(any(), eq(HECHO));

        RocaDiaria cambiadaDespues = pendienteCreadaEn(EL_10, 1, "La armó mi mentora", null, HECHO.plusSeconds(600));
        when(cargar.deParticipanteYFecha(ANA, EL_10)).thenReturn(List.of(cambiadaDespues));
        assertThatThrownBy(() -> servicio.agendar(agenda(EL_10), new HechoSinRed(HECHO, UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
        verify(crear, times(1)).crear(any(), any());
    }

    @Test
    @DisplayName("el día en curso ya armado es YA_ESTABA_HECHO (con red era ALREADY_PLANNED)")
    void elDiaEnCursoYaArmado() { // spec 048 FR-019
        Instant estaManana = Instant.parse("2026-11-10T12:00:00Z");
        when(cargar.deParticipanteYFecha(ANA, EL_10)).thenReturn(List.of(pendiente(EL_10, 1, "Ya armada", null)));

        assertThatThrownBy(() -> servicio.agendar(agenda(EL_10), new HechoSinRed(estaManana, UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.YA_ESTABA_HECHO));
    }

    @Test
    @DisplayName("el mismo envío de agenda dos veces crea una sola vez y devuelve las acciones del día")
    void agendaRepetida() { // spec 048 FR-008
        servicio.agendar(agenda(EL_10), SIN_RED);
        RocaDiaria creada = pendiente(EL_10, 1, "Salir a correr", null);
        when(cargar.deParticipanteYFecha(ANA, EL_10)).thenReturn(List.of(creada));

        assertThat(servicio.agendar(agenda(EL_10), SIN_RED)).containsExactly(creada);
        verify(crear, times(1)).crear(any(), any());
    }

    // ─── marcar ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("marcar la acción de anoche en regla: se completa con la hora real y CON puntos, sin motivo")
    void marcarEnRegla() { // spec 048 FR-019
        AccionMarcada resultado = marcarLaDe(EL_9, SIN_RED);

        verify(completar).completarConHoraReal(any(), eq(HECHO), eq(true));
        assertThat(resultado.motivoSinPuntos()).isNull();
    }

    @Test
    @DisplayName("D14: llegar a más de 48 h la deja cumplida SIN puntos (LLEGO_DESPUES_DE_48_H); nunca rebota")
    void llegoTarde() { // spec 048 FR-019
        Instant haceTresDias = Instant.parse("2026-11-07T02:00:00Z");

        AccionMarcada resultado = marcarLaDe(LocalDate.of(2026, 11, 6), new HechoSinRed(haceTresDias, UUID.randomUUID(), null));

        verify(completar).completarConHoraReal(any(), eq(haceTresDias), eq(false));
        assertThat(resultado.motivoSinPuntos()).isEqualTo(MotivoSinPuntos.LLEGO_DESPUES_DE_48_H);
    }

    @Test
    @DisplayName("D14: una hora del futuro queda sin puntos (HORA_FUTURA) y se registra con la hora de llegada")
    void horaFutura() { // spec 048 FR-019
        Instant adelantada = LLEGA.plusSeconds(600);

        AccionMarcada resultado = marcarLaDe(EL_10, new HechoSinRed(adelantada, UUID.randomUUID(), null));

        verify(completar).completarConHoraReal(any(), eq(LLEGA), eq(false));
        assertThat(resultado.motivoSinPuntos()).isEqualTo(MotivoSinPuntos.HORA_FUTURA);
    }

    @Test
    @DisplayName("D14: una acción que no era del día de la hora real queda cumplida sin puntos (FUERA_DE_SU_DIA)")
    void fueraDeSuDia() { // spec 048 FR-019
        AccionMarcada resultado = marcarLaDe(EL_9.minusDays(1), SIN_RED);

        verify(completar).completarConHoraReal(any(), eq(HECHO), eq(false));
        assertThat(resultado.motivoSinPuntos()).isEqualTo(MotivoSinPuntos.FUERA_DE_SU_DIA);
    }

    @Test
    @DisplayName("ya cumplida (con red o desde otro teléfono): 200 con YA_ESTABA_HECHO y no se toca")
    void yaCumplida() { // spec 048 FR-019
        RocaDiaria hecha = cumplida(EL_9, 1, "Ya cumplida", null, 8);
        when(cargar.byId(hecha.id())).thenReturn(Optional.of(hecha));

        AccionMarcada resultado = servicio.marcar(marca(hecha), SIN_RED);

        assertThat(resultado.motivoSinPuntos()).isEqualTo(MotivoSinPuntos.YA_ESTABA_HECHO);
        verify(completar, never()).completarConHoraReal(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("una acción que ya no existe (se replanificó el día) es RECURSO_YA_NO_EXISTE, no un 404")
    void laAccionYaNoExiste() { // spec 048 FR-019
        RocaDiaria fantasma = pendiente(EL_9, 1, "Ya no existe", null);
        when(cargar.byId(fantasma.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcar(marca(fantasma), SIN_RED)).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));
    }

    @Test
    @DisplayName("la acción de otra persona es el 403 de siempre")
    void accionAjena() { // spec 048 FR-019
        RocaDiaria ajena = pendiente(EL_9, 1, "De otra", null);
        UUID otra = UUID.randomUUID();
        CompletarRocaDiariaCommand comandoDeOtra = new CompletarRocaDiariaCommand(
                com.renaser.os.shared.domain.UserId.of(otra), ajena.id(), TipoEvidenciaRoca.TEXTO, null, null, "x", null, null, null, true, false);
        when(progreso.deParticipante(com.renaser.os.shared.domain.UserId.of(otra))).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(17, LocalDate.of(2026, 10, 24), LIMA, RolParticipante.TRAINEE, false, true)));
        when(cargar.byId(ajena.id())).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> servicio.marcar(comandoDeOtra, SIN_RED)).isInstanceOf(NotAuthorizedException.class);
        verify(completar, never()).completarConHoraReal(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("el mismo envío de marca dos veces completa una sola vez y repite el mismo motivo sin puntos")
    void marcaRepetida() { // spec 048 FR-008
        Instant haceTresDias = Instant.parse("2026-11-07T02:00:00Z");
        RocaDiaria roca = pendiente(LocalDate.of(2026, 11, 6), 1, "Salir a correr", null);
        when(cargar.byId(roca.id())).thenReturn(Optional.of(roca));
        HechoSinRed envio = new HechoSinRed(haceTresDias, UUID.randomUUID(), null);
        servicio.marcar(marca(roca), envio);

        AccionMarcada otraVez = servicio.marcar(marca(roca), envio);

        assertThat(otraVez.motivoSinPuntos()).isEqualTo(MotivoSinPuntos.LLEGO_DESPUES_DE_48_H);
        verify(completar, times(1)).completarConHoraReal(any(), any(), anyBoolean());
    }
}
