package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.medicion.RegistrarMedicionAutomaticaUseCase.RegistrarMedicionAutomaticaCommand;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.medicion.MetaDeMedicionPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.medicion.DispositivoDeMedicion;
import com.renaser.os.habits.domain.model.medicion.LecturaAutomatica;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Spec 037 (D-310): el cierre de «Kilómetros diarios» con la lectura de Health Connect. El reloj se fija a las 04:30 UTC
 * —las 23:30 del día anterior en Lima—, la hora que esconde el bug del día local (regla 03).
 */
@ExtendWith(MockitoExtension.class)
class MedicionAutomaticaServiceTest {

    /** 23:30 del 3 de noviembre en Lima. */
    private static final Instant AHORA = Instant.parse("2026-11-04T04:30:00Z");
    private static final LocalDate DIA_DEL_REGISTRO = LocalDate.of(2026, 11, 3);

    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private MetaDeMedicionPort metaPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private CompletarRegistroUseCase completar;
    @Mock
    private SaveRegistroHabitoPort saveRegistroPort;

    private MedicionAutomaticaService service;
    private final UserId ana = UserId.of(UUID.randomUUID());
    private Habito km;
    private RegistroHabito registro;

    @BeforeEach
    void preparar() {
        service = new MedicionAutomaticaService(loadRegistroPort, loadHabitoPort, metaPort, progresoPort, completar,
                new PoliticaKilometros(), FixedClock.at(AHORA), saveRegistroPort);
        km = habito(PoliticaKilometros.CLAVE_SISTEMA);
        registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), ana, km.id(), DIA_DEL_REGISTRO, 12,
                TipoDia.TODOS, true, Instant.EPOCH);
        lenient().when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        lenient().when(loadHabitoPort.byId(km.id())).thenReturn(Optional.of(km));
        lenient().when(metaPort.metaDe(km.id())).thenReturn(Optional.empty());
        lenient().when(progresoPort.deParticipante(ana)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(12, "America/Lima", RolParticipante.TRAINEE, false, true)));
        lenient().when(completar.completar(any())).thenReturn(registro);
    }

    private static Habito habito(String clave) {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null, "H", null,
                TipoHabito.CHECKBOX, "CUERPO", null, clave, ExigenciaEvidencia.OBLIGATORIA, true, false, true, false,
                null, null, null, null, true, Instant.EPOCH, Instant.EPOCH);
    }

    private RegistrarMedicionAutomaticaCommand comando(String km, Instant leidoEn) {
        return new RegistrarMedicionAutomaticaCommand(ana, registro.id(), new BigDecimal(km), 7120, "Galaxy Watch",
                DispositivoDeMedicion.RELOJ, leidoEn);
    }

    private CompletarRegistroCommand comandoEnviado() {
        ArgumentCaptor<CompletarRegistroCommand> captura = ArgumentCaptor.forClass(CompletarRegistroCommand.class);
        verify(completar).completar(captura.capture());
        return captura.getValue();
    }

    @Test
    @DisplayName("con la meta alcanzada cierra el hábito por el gesto propio, con la lectura y sin captura ni texto")
    void cierraConLaLectura() {
        service.registrar(comando("5.40", AHORA.minusSeconds(300)));

        CompletarRegistroCommand enviado = comandoEnviado();
        assertThat(enviado.gesto()).isEqualTo(GestoCompletar.PROPIO_DEL_HABITO);
        assertThat(enviado.respuestaTexto()).isNull();
        assertThat(enviado.medicion().valor()).isEqualByComparingTo("5.40");
        assertThat(enviado.medicion().esAutomatica()).isTrue();
        assertThat(enviado.medicion().lectura().fuente()).isEqualTo("Galaxy Watch");
        assertThat(enviado.medicion().lectura().pasos()).isEqualTo(7120);
        assertThat(enviado.medicion().lectura().porRevisar()).isFalse();
    }

    // spec 037 FR-010
    @Test
    @DisplayName("una lectura dudosa se acepta y viaja marcada por revisar")
    void loDudosoSeMarca() {
        service.registrar(comando("35.00", AHORA.minusSeconds(60)));

        assertThat(comandoEnviado().medicion().lectura().porRevisar()).isTrue();
    }

    // spec 037 FR-008
    @Test
    @DisplayName("faltar para la meta es un 400 que dice cuál es, y no cierra nada")
    void faltaLaMeta() {
        assertThatThrownBy(() -> service.registrar(comando("3.20", AHORA.minusSeconds(60))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Todavía no llegas a tu meta de 5 km.");
        verifyNoInteractions(completar);
    }

    @Test
    @DisplayName("la meta que fijó Administración manda sobre la del programa")
    void rigeLaMetaDeAdministracion() {
        when(metaPort.metaDe(km.id())).thenReturn(Optional.of(new BigDecimal("3.00")));

        service.registrar(comando("3.20", AHORA.minusSeconds(60)));

        assertThat(comandoEnviado().medicion().valor()).isEqualByComparingTo("3.20");
    }

    // spec 037 FR-008
    @Test
    @DisplayName("más de 60 km no se registra solo")
    void elTope() {
        assertThatThrownBy(() -> service.registrar(comando("61", AHORA.minusSeconds(60))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Sube tu captura");
    }

    @Test
    @DisplayName("solo el dueño del registro registra, y no se lee nada más")
    void soloElDueno() {
        UserId otra = UserId.of(UUID.randomUUID());
        RegistrarMedicionAutomaticaCommand ajeno = new RegistrarMedicionAutomaticaCommand(otra, registro.id(),
                new BigDecimal("6"), 8000, "Reloj", DispositivoDeMedicion.RELOJ, AHORA);

        assertThatThrownBy(() -> service.registrar(ajeno)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(completar, loadHabitoPort, metaPort, progresoPort);
    }

    @Test
    @DisplayName("un registro que no existe es un 404")
    void noExiste() {
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(comando("6", AHORA))).isInstanceOf(NoSuchElementException.class);
    }

    // spec 037 FR-008
    @Test
    @DisplayName("un hábito que no es el de kilómetros no se registra con pasos")
    void soloElDeKilometros() {
        Habito otro = habito("DAILY_CLASS");
        when(loadHabitoPort.byId(km.id())).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> service.registrar(comando("6", AHORA)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no se registra con tus pasos");
        verifyNoInteractions(completar);
    }

    // spec 037 FR-009
    @Test
    @DisplayName("idempotente: lo que ya cerró Health Connect se devuelve igual, sin completar otra vez")
    void idempotente() {
        registro.completar(8, null, null, null, MedicionDiaria.automatica(new BigDecimal("5.40"),
                new LecturaAutomatica(7120, "Galaxy Watch", DispositivoDeMedicion.RELOJ, AHORA, false)), AHORA);

        RegistroHabito devuelto = service.registrar(comando("6.00", AHORA));

        assertThat(devuelto).isSameAs(registro);
        verify(completar, never()).completar(any());
    }

    // spec 037 FR-009
    @Test
    @DisplayName("un hábito cumplido con la captura es un 409 que lo dice, y no se pisa")
    void cumplidoConLaCaptura() {
        registro.completar(8, null, null, null, MedicionDiaria.manualSiHay(new BigDecimal("4")), AHORA);

        assertThatThrownBy(() -> service.registrar(comando("6", AHORA)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Ya registraste este hábito con tu captura.");
        verify(completar, never()).completar(any());
    }

    // spec 037 FR-008
    @Test
    @DisplayName("una lectura del futuro es inválida; el reloj corrido unos minutos se tolera")
    void laLecturaNoEsDelFuturo() {
        assertThatThrownBy(() -> service.registrar(comando("6", AHORA.plusSeconds(6 * 60))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hora de la lectura");

        service.registrar(comando("6", AHORA.plusSeconds(4 * 60)));
        verify(completar).completar(any());
    }

    // spec 037 FR-008
    @Test
    @DisplayName("con el reloj en la madrugada UTC, una lectura de las 23:59 del día anterior en Lima es de otro día")
    void laLecturaEsDeEseDiaEnLaZonaDeLaPersona() {
        // El día del registro (3 de noviembre en Lima) empezó a las 05:00 UTC del 3.
        assertThatThrownBy(() -> service.registrar(comando("6", Instant.parse("2026-11-03T04:59:00Z"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("otro día");
        verifyNoInteractions(completar);

        service.registrar(comando("6", Instant.parse("2026-11-03T05:00:00Z")));
        verify(completar).completar(any());
    }

    // spec 042 FR-022
    @Test
    @DisplayName("sobre un día cerrado por las carreras con GPS, Health Connect con más km sube el número sin pagar de nuevo")
    void healthConnectSubeSobreLasCarreras() {
        registro.completar(8, null, null, null, MedicionDiaria.gps(new BigDecimal("6.00")), AHORA.minusSeconds(3600));
        when(saveRegistroPort.save(registro)).thenReturn(registro);

        RegistroHabito resultado = service.registrar(comando("9.00", AHORA.minusSeconds(60)));

        assertThat(resultado.medicion().valor()).isEqualByComparingTo("9.00");
        assertThat(resultado.medicion().esAutomatica()).isTrue();
        assertThat(resultado.puntosOtorgados()).isEqualTo(8);
        verify(completar, never()).completar(any());
    }

    // spec 042 FR-022, SC-008
    @Test
    @DisplayName("sobre un día cerrado por las carreras con más km, Health Connect no baja nada ni lo suma")
    void healthConnectMenorNoCambiaNada() {
        registro.completar(8, null, null, null, MedicionDiaria.gps(new BigDecimal("7.00")), AHORA.minusSeconds(3600));

        RegistroHabito resultado = service.registrar(comando("3.00", AHORA.minusSeconds(60)));

        assertThat(resultado.medicion().valor()).isEqualByComparingTo("7.00");
        assertThat(resultado.medicion().esDelTelefono()).isTrue();
        verifyNoInteractions(saveRegistroPort);
        verify(completar, never()).completar(any());
    }
}
