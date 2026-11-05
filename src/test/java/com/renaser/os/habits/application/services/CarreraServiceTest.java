package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.carrera.RegistrarCarreraUseCase.CarreraRegistrada;
import com.renaser.os.habits.application.ports.in.carrera.RegistrarCarreraUseCase.CarreraYaRegistradaException;
import com.renaser.os.habits.application.ports.in.carrera.RegistrarCarreraUseCase.RegistrarCarreraCommand;
import com.renaser.os.habits.application.ports.out.carrera.CarrerasPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.carrera.Carrera;
import com.renaser.os.habits.domain.model.carrera.CarrerasDePrueba;
import com.renaser.os.habits.domain.model.carrera.DatosDeCarrera;
import com.renaser.os.habits.domain.model.carrera.EfectoEnElHabito;
import com.renaser.os.habits.domain.model.carrera.EvaluacionDeCarrera;
import com.renaser.os.habits.domain.model.carrera.PolylineDeGoogle;
import com.renaser.os.habits.domain.model.carrera.ResultadoEnElHabito;
import com.renaser.os.habits.domain.model.carrera.RutaDeCarrera;
import com.renaser.os.habits.domain.model.carrera.RutaEnviada;
import com.renaser.os.habits.domain.model.carrera.ZonaDePrivacidadTest;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

/** Spec 042: la idempotencia del envío único (FR-019), la transacción única (FR-020) y el día de la persona (FR-021). */
@ExtendWith(MockitoExtension.class)
class CarreraServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T04:30:00Z");

    @Mock
    private CarrerasPort carreras;
    @Mock
    private CarreraEnElHabito enElHabito;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private IdGenerator ids;

    private final UserId ana = UserId.of(UUID.randomUUID());
    private final DatosDeCarrera datos = CarrerasDePrueba.cincoKm();
    private CarreraService service;

    @BeforeEach
    void preparar() {
        service = new CarreraService(carreras, enElHabito, progresoPort, new PoliticaKilometros(), ids, FixedClock.at(AHORA));
        lenient().when(progresoPort.deParticipante(ana)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(12, "America/Lima", RolParticipante.TRAINEE, false, true)));
        lenient().when(ids.newId()).thenReturn(UUID.randomUUID());
    }

    private Carrera guardada(DatosDeCarrera de) {
        return Carrera.nueva(UUID.randomUUID(), ana, de, ZoneId.of("America/Lima"), EvaluacionDeCarrera.queCuenta(null), AHORA)
                .conEfecto(new EfectoEnElHabito(ResultadoEnElHabito.CERRADO, UUID.randomUUID(), null, null, 8));
    }

    // spec 042 FR-019, FR-020
    @Test
    @DisplayName("la primera vez guarda, aplica al hábito y anota el efecto, todo en la misma llamada")
    void laPrimeraVez() {
        when(carreras.de(ana, datos.carreraId())).thenReturn(Optional.empty());
        when(carreras.guardarSiEsNueva(any())).thenReturn(true);
        EfectoEnElHabito cerrado = new EfectoEnElHabito(ResultadoEnElHabito.CERRADO, UUID.randomUUID(), null, null, 8);
        when(enElHabito.aplicar(any(), any())).thenReturn(cerrado);

        CarreraRegistrada registrada = service.registrar(new RegistrarCarreraCommand(ana, datos));

        assertThat(registrada.repetida()).isFalse();
        assertThat(registrada.carrera().efecto()).isEqualTo(cerrado);
        assertThat(registrada.carrera().fecha()).isEqualTo(LocalDate.of(2026, 11, 5));
        verify(carreras).anotarEfecto(registrada.carrera().id(), cerrado);
    }

    // spec 042 FR-019, SC-002
    @Test
    @DisplayName("el mismo envío otra vez devuelve lo guardado, sin volver a tocar el hábito")
    void repetida() {
        Carrera antes = guardada(datos);
        when(carreras.de(ana, datos.carreraId())).thenReturn(Optional.of(antes));

        CarreraRegistrada registrada = service.registrar(new RegistrarCarreraCommand(ana, datos));

        assertThat(registrada.repetida()).isTrue();
        assertThat(registrada.carrera()).isEqualTo(antes);
        verifyNoInteractions(enElHabito);
        verify(carreras, never()).guardarSiEsNueva(any());
    }

    // spec 042 FR-019, SC-002
    @Test
    @DisplayName("si otro pedido igual la guardó mientras tanto (el índice único gana), se lee y se responde repetida")
    void carreraEnVuelo() {
        Carrera laOtra = guardada(datos);
        when(carreras.de(ana, datos.carreraId())).thenReturn(Optional.empty(), Optional.of(laOtra));
        when(carreras.guardarSiEsNueva(any())).thenReturn(false);

        CarreraRegistrada registrada = service.registrar(new RegistrarCarreraCommand(ana, datos));

        assertThat(registrada.repetida()).isTrue();
        verifyNoInteractions(enElHabito);
    }

    // spec 042 FR-019
    @Test
    @DisplayName("misma carreraId con otro contenido es un 409 y no pisa nada")
    void otraHuella() {
        when(carreras.de(ana, datos.carreraId())).thenReturn(Optional.of(guardada(CarrerasDePrueba.de(datos.carreraId(), "5200",
                1800))));

        assertThatThrownBy(() -> service.registrar(new RegistrarCarreraCommand(ana, datos)))
                .isInstanceOf(CarreraYaRegistradaException.class);
        verify(carreras, never()).guardarSiEsNueva(any());
        verifyNoInteractions(enElHabito);
    }

    @Test
    @DisplayName("una cuenta suspendida no guarda nada")
    void suspendida() {
        when(progresoPort.deParticipante(ana)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(12, "America/Lima", RolParticipante.TRAINEE, true, true)));

        assertThatThrownBy(() -> service.registrar(new RegistrarCarreraCommand(ana, datos)))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(carreras, enElHabito);
    }

    /** La ruta de ~1,83 km por Lima, completa como la manda el teléfono, en una carrera de 1830 m (FR-043). */
    private DatosDeCarrera carreraDeLaRuta() {
        return CarrerasDePrueba.de(UUID.randomUUID(), "1830", 660);
    }

    private RutaEnviada rutaCompleta() {
        return new RutaEnviada(ZonaDePrivacidadTest.rutaCompleta(), PolylineDeGoogle.decodificar(ZonaDePrivacidadTest.rutaCompleta()).size());
    }

    // spec 042 FR-043
    @Test
    @DisplayName("lo que se guarda es la ruta sin la zona de privacidad, y la regla de «por revisar» da lo mismo que antes")
    void guardaLaRutaRecortadaYEvaluaIgual() {
        DatosDeCarrera sinRuta = carreraDeLaRuta();
        when(carreras.de(ana, sinRuta.carreraId())).thenReturn(Optional.empty());
        when(carreras.guardarSiEsNueva(any())).thenReturn(true);
        when(enElHabito.aplicar(any(), any())).thenReturn(EfectoEnElHabito.sinRegistro(ResultadoEnElHabito.SIN_CAMBIO));
        ArgumentCaptor<Carrera> guardada = ArgumentCaptor.forClass(Carrera.class);

        service.registrar(new RegistrarCarreraCommand(ana, sinRuta, rutaCompleta()));

        verify(carreras).guardarSiEsNueva(guardada.capture());
        RutaDeCarrera ruta = guardada.getValue().datos().ruta();
        assertThat(ruta.polyline()).isEqualTo(ZonaDePrivacidadTest.recortadaPorLaApp());
        // Lo mismo que daba antes, cuando la ruta llegaba ya recortada desde el teléfono: no queda «por revisar».
        EvaluacionDeCarrera comoAntes = new PoliticaKilometros().evaluarCarrera(sinRuta.conRuta(ruta), AHORA);
        assertThat(guardada.getValue().evaluacion()).isEqualTo(comoAntes);
        assertThat(guardada.getValue().evaluacion().porRevisar()).isFalse();
    }

    // spec 042 FR-043, FR-019
    @Test
    @DisplayName("la carrera repetida con la ruta completa sigue siendo idempotente: la huella es la de lo que mandó el teléfono")
    void repetidaConRutaCompleta() {
        DatosDeCarrera sinRuta = carreraDeLaRuta();
        when(carreras.de(ana, sinRuta.carreraId())).thenReturn(Optional.empty());
        when(carreras.guardarSiEsNueva(any())).thenReturn(true);
        when(enElHabito.aplicar(any(), any())).thenReturn(EfectoEnElHabito.sinRegistro(ResultadoEnElHabito.SIN_CAMBIO));
        Carrera primera = service.registrar(new RegistrarCarreraCommand(ana, sinRuta, rutaCompleta())).carrera();
        when(carreras.de(ana, sinRuta.carreraId())).thenReturn(Optional.of(primera));

        CarreraRegistrada otraVez = service.registrar(new RegistrarCarreraCommand(ana, sinRuta, rutaCompleta()));

        assertThat(otraVez.repetida()).isTrue();
        assertThat(primera.huella()).isEqualTo(sinRuta.huellaDelEnvio(rutaCompleta()))
                .isNotEqualTo(primera.datos().huella());
        // Otra ruta completa con la misma clave es otro envío: 409.
        List<double[]> puntos = PolylineDeGoogle.decodificar(ZonaDePrivacidadTest.rutaCompleta());
        RutaEnviada otra = new RutaEnviada(PolylineDeGoogle.codificar(puntos.subList(0, 170)), 170);
        assertThatThrownBy(() -> service.registrar(new RegistrarCarreraCommand(ana, sinRuta, otra)))
                .isInstanceOf(CarreraYaRegistradaException.class);
    }

    // spec 042 FR-043
    @Test
    @DisplayName("una ruta de menos de 400 m se guarda sin ruta, y la carrera igual cuenta")
    void rutaCortaSinRuta() {
        DatosDeCarrera corta = CarrerasDePrueba.de(UUID.randomUUID(), "300", 180);
        List<double[]> puntos = PolylineDeGoogle.decodificar(ZonaDePrivacidadTest.rutaCompleta()).subList(0, 30);
        when(carreras.de(ana, corta.carreraId())).thenReturn(Optional.empty());
        when(carreras.guardarSiEsNueva(any())).thenReturn(true);
        when(enElHabito.aplicar(any(), any())).thenReturn(EfectoEnElHabito.sinRegistro(ResultadoEnElHabito.SIN_CAMBIO));
        ArgumentCaptor<Carrera> guardada = ArgumentCaptor.forClass(Carrera.class);

        service.registrar(new RegistrarCarreraCommand(ana, corta, new RutaEnviada(PolylineDeGoogle.codificar(puntos), 30)));

        verify(carreras).guardarSiEsNueva(guardada.capture());
        assertThat(guardada.getValue().datos().ruta()).isNull();
        assertThat(guardada.getValue().evaluacion().cuenta()).isTrue();
    }
}
