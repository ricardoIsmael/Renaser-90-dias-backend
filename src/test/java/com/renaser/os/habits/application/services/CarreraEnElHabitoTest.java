package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.carrera.CarrerasPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.carrera.Carrera;
import com.renaser.os.habits.domain.model.carrera.CarrerasDePrueba;
import com.renaser.os.habits.domain.model.carrera.EfectoEnElHabito;
import com.renaser.os.habits.domain.model.carrera.EvaluacionDeCarrera;
import com.renaser.os.habits.domain.model.carrera.ResultadoEnElHabito;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.medicion.DispositivoDeMedicion;
import com.renaser.os.habits.domain.model.medicion.LecturaAutomatica;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.medicion.OrigenMedicion;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Spec 042: cómo entra una carrera al hábito KILÓMETROS DIARIOS (FR-022, FR-023). El reloj está a las 04:30 UTC, que en Lima son
 * las 23:30 del día anterior (regla 03): la carrera de las 06:00 de Lima del 5 sigue siendo de un día abierto.
 */
@ExtendWith(MockitoExtension.class)
class CarreraEnElHabitoTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T04:30:00Z");
    private static final LocalDate DIA = LocalDate.of(2026, 11, 5);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Mock
    private LoadHabitoPort habitos;
    @Mock
    private LoadRegistroHabitoPort registros;
    @Mock
    private SaveRegistroHabitoPort guardar;
    @Mock
    private CompletarRegistroUseCase completar;
    @Mock
    private CarrerasPort carreras;

    private final UserId ana = UserId.of(UUID.randomUUID());
    private final Habito km = Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null, "KM", null,
            TipoHabito.CHECKBOX, "CUERPO", null, PoliticaKilometros.CLAVE_SISTEMA, ExigenciaEvidencia.OBLIGATORIA, true, false, true,
            false, null, null, null, null, true, Instant.EPOCH, Instant.EPOCH);
    private final Optional<ProgresoParticipanteHabits> progreso = Optional.of(
            new ProgresoParticipanteHabits(12, "America/Lima", RolParticipante.TRAINEE, false, true));
    private CarreraEnElHabito enElHabito;
    private RegistroHabito registro;

    @BeforeEach
    void preparar() {
        enElHabito = new CarreraEnElHabito(habitos, registros, guardar, completar, carreras, FixedClock.at(AHORA));
        registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), ana, km.id(), DIA, 12, TipoDia.TODOS, true,
                Instant.EPOCH);
        lenient().when(habitos.porClaveSistema(PoliticaKilometros.CLAVE_SISTEMA, progreso.get().formacion()))
                .thenReturn(Optional.of(km));
        lenient().when(registros.porParticipanteHabitoYFechaParaEscritura(ana, km.id(), DIA)).thenReturn(Optional.of(registro));
        lenient().when(guardar.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Carrera carrera(EvaluacionDeCarrera evaluacion) {
        var datos = CarrerasDePrueba.de(UUID.randomUUID(), "3000", 1200);
        return Carrera.nueva(UUID.randomUUID(), ana, datos, LIMA, evaluacion, AHORA).conEfecto(
                EfectoEnElHabito.sinRegistro(evaluacion.cuenta() ? ResultadoEnElHabito.SIN_CAMBIO : ResultadoEnElHabito.NO_CUENTA));
    }

    private Carrera queCuenta() {
        return carrera(EvaluacionDeCarrera.queCuenta(null));
    }

    // spec 042 FR-023, FR-027
    @Test
    @DisplayName("un registro pendiente lo cierra la carrera por el gesto propio, con origen GPS y la suma de las carreras del día")
    void cierraElPendiente() {
        when(carreras.kilometrosQueCuentan(ana, DIA)).thenReturn(new BigDecimal("3.00"));
        RegistroHabito cerrado = RegistroHabito.generar(registro.id(), ana, km.id(), DIA, 12, TipoDia.TODOS, true, Instant.EPOCH);
        cerrado.completar(8, null, null, null, MedicionDiaria.gps(new BigDecimal("3.00")), AHORA);
        when(completar.completar(any())).thenReturn(cerrado);

        EfectoEnElHabito efecto = enElHabito.aplicar(queCuenta(), progreso);

        ArgumentCaptor<CompletarRegistroCommand> enviado = ArgumentCaptor.forClass(CompletarRegistroCommand.class);
        verify(completar).completar(enviado.capture());
        assertThat(enviado.getValue().gesto()).isEqualTo(GestoCompletar.PROPIO_DEL_HABITO);
        assertThat(enviado.getValue().medicion().origen()).isEqualTo(OrigenMedicion.GPS);
        assertThat(enviado.getValue().medicion().valor()).isEqualByComparingTo("3.00");
        assertThat(efecto.resultado()).isEqualTo(ResultadoEnElHabito.CERRADO);
        assertThat(efecto.puntos()).isEqualTo(8);
        assertThat(efecto.kmDelDia()).isEqualByComparingTo("3.00");
    }

    // spec 042 FR-022
    @Test
    @DisplayName("ya cerrado por carreras: la segunda carrera sube los km del día a la suma, sin pagar de nuevo")
    void dosCarrerasSeSuman() {
        registro.completar(8, null, null, null, MedicionDiaria.gps(new BigDecimal("3.00")), AHORA);
        when(carreras.kilometrosQueCuentan(ana, DIA)).thenReturn(new BigDecimal("7.00"));

        EfectoEnElHabito efecto = enElHabito.aplicar(queCuenta(), progreso);

        assertThat(efecto.resultado()).isEqualTo(ResultadoEnElHabito.ACTUALIZADO);
        assertThat(efecto.puntos()).isZero();
        assertThat(efecto.kmDelDia()).isEqualByComparingTo("7.00");
        assertThat(registro.puntosOtorgados()).isEqualTo(8);
        verify(completar, never()).completar(any());
    }

    // spec 042 FR-022, SC-008
    @Test
    @DisplayName("Health Connect con 7 km y una carrera de 3: queda en 7, el mayor, nunca la suma")
    void elMayorConHealthConnect() {
        registro.completar(8, null, null, null, MedicionDiaria.automatica(new BigDecimal("7.00"),
                new LecturaAutomatica(9000, "Tu teléfono", DispositivoDeMedicion.TELEFONO, AHORA, false)), AHORA);
        when(carreras.kilometrosQueCuentan(ana, DIA)).thenReturn(new BigDecimal("3.00"));

        EfectoEnElHabito efecto = enElHabito.aplicar(queCuenta(), progreso);

        assertThat(efecto.resultado()).isEqualTo(ResultadoEnElHabito.SIN_CAMBIO);
        assertThat(efecto.kmDelDia()).isEqualByComparingTo("7.00");
        assertThat(efecto.origenDelDia()).isEqualTo(OrigenMedicion.HEALTH_CONNECT);
        verifyNoInteractions(guardar);
    }

    // spec 042 FR-023
    @Test
    @DisplayName("cumplido con la captura: no se toca")
    void conLaCaptura() {
        registro.completar(8, null, null, null, MedicionDiaria.manualSiHay(new BigDecimal("4")), AHORA);

        EfectoEnElHabito efecto = enElHabito.aplicar(queCuenta(), progreso);

        assertThat(efecto.resultado()).isEqualTo(ResultadoEnElHabito.YA_CON_CAPTURA);
        assertThat(registro.medicion().origen()).isEqualTo(OrigenMedicion.MANUAL);
        verifyNoInteractions(guardar, completar);
    }

    // spec 042 FR-023, SC-009
    @Test
    @DisplayName("una carrera de un día que ya terminó no se aplica a ningún registro (regla del 17-oct, D-259)")
    void diaCerrado() {
        var datos = CarrerasDePrueba.de(UUID.randomUUID(), "3000", 1200);
        Carrera deAyer = new Carrera(UUID.randomUUID(), ana, datos, datos.huella(), DIA.minusDays(1),
                EvaluacionDeCarrera.queCuenta(null), EfectoEnElHabito.sinRegistro(ResultadoEnElHabito.SIN_CAMBIO), AHORA);

        EfectoEnElHabito efecto = enElHabito.aplicar(deAyer, progreso);

        assertThat(efecto.resultado()).isEqualTo(ResultadoEnElHabito.DIA_CERRADO);
        verifyNoInteractions(registros, completar, guardar);
    }

    // spec 042 FR-023, FR-024
    @Test
    @DisplayName("sin programa, sin hábito de km o si la carrera no cuenta: no se aplica")
    void sinHabitoONoCuenta() {
        assertThat(enElHabito.aplicar(queCuenta(), Optional.empty()).resultado()).isEqualTo(ResultadoEnElHabito.SIN_HABITO);
        assertThat(enElHabito.aplicar(carrera(EvaluacionDeCarrera.queNoCuenta("rápida", null)), progreso).resultado())
                .isEqualTo(ResultadoEnElHabito.NO_CUENTA);
        when(registros.porParticipanteHabitoYFechaParaEscritura(ana, km.id(), DIA)).thenReturn(Optional.empty());
        assertThat(enElHabito.aplicar(queCuenta(), progreso).resultado()).isEqualTo(ResultadoEnElHabito.SIN_HABITO);
        verifyNoInteractions(completar, guardar);
    }
}
