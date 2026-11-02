package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.in.pagos.CronogramaParaFirmarUseCase;
import com.renaser.os.onboarding.application.ports.in.pagos.CronogramaParaFirmarUseCase.CronogramaParaFirmar;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.AcuerdoDeLaPersona;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.TerminosAceptados;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.TerminosParaLaPersona;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort.Aceptacion;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort.LoMostrado;
import com.renaser.os.onboarding.application.ports.out.terminos.TerminosPort;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.onboarding.domain.model.terminos.ContenidoDeTerminos;
import com.renaser.os.onboarding.domain.model.terminos.QuePideLaAceptacion;
import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.FormacionesFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Cuándo la lectura de los términos trae «Mi aceptación» y cuándo trae además el cronograma para firmar (spec 039): el cronograma SOLO en el primer ingreso, y solo si no lo
 // spec 039 FR-006
 * tiene ya confirmado; quien ya estaba dentro y vuelve a aceptar un texto nuevo lo ve sin esa sección (se le sigue pidiendo en «Mi ingreso»).
 */
@ExtendWith(MockitoExtension.class)
class TerminosDeLaPersonaAcuerdoTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:00Z");

    @Mock
    private TerminosPort terminos;
    @Mock
    private AceptacionesDeTerminosPort aceptaciones;
    @Mock
    private AlcanceDeFormacionFinder formaciones;
    @Mock
    private FormacionesFinder catalogo;
    @Mock
    private ParticipacionProgramaFinder participaciones;
    @Mock
    private FirmaDeLaAceptacion firmas;
    @Mock
    private ArmadorDelAcuerdo acuerdos;
    @Mock
    private CronogramaParaFirmarUseCase cronogramas;

    private TerminosDeLaPersonaService servicio;
    private UserId persona;
    private VersionDeTerminos v1;
    private VersionDeTerminos v2;
    private final AcuerdoDeLaPersona base = new AcuerdoDeLaPersona("Ana Pérez", "Formación RENASER · 90 días", LocalDate.parse("2026-11-04"), LocalDate.parse("2027-02-01"), false,
            Optional.empty());

    @BeforeEach
    void preparar() {
        servicio = new TerminosDeLaPersonaService(terminos, aceptaciones, formaciones, catalogo, participaciones, firmas, acuerdos, cronogramas);
        persona = UserId.of(UUID.randomUUID());
        ContenidoDeTerminos contenido = new ContenidoDeTerminos("Términos y condiciones", "Intro", List.of("Una", "Dos"), "He leído y acepto");
        v1 = VersionDeTerminos.borrador(UUID.randomUUID(), Formacion.ESCUELA, contenido, null, AHORA).publicada(1, null, false, null, AHORA);
        v2 = VersionDeTerminos.borrador(UUID.randomUUID(), Formacion.ESCUELA, contenido, 1, AHORA).publicada(2, "Nuevo Acuerdo de transformación", true, null, AHORA);
        lenient().when(formaciones.formacionDe(persona)).thenReturn(Optional.of(Formacion.ESCUELA));
        lenient().when(catalogo.de(Formacion.ESCUELA)).thenReturn(new FormacionConfigurada(Formacion.ESCUELA, "Escuela Renaser", "", true, List.of(90), 1));
        lenient().when(participaciones.deParticipante(persona)).thenReturn(Optional.empty());
        lenient().when(acuerdos.armar(persona, Formacion.ESCUELA)).thenReturn(base);
    }

    private static CronogramaParaFirmar cronograma(boolean yaConfirmado) {
        return new CronogramaParaFirmar(Moneda.PEN, List.of(), "huella-1", yaConfirmado);
    }

    // spec 039 FR-003, FR-006
    @Test
    @DisplayName("primer ingreso con cronograma sin confirmar: «Mi aceptación» trae el cronograma para firmar")
    void primerIngresoConCronograma() {
        when(terminos.vigente(Formacion.ESCUELA)).thenReturn(Optional.of(v1));
        when(terminos.publicadas(Formacion.ESCUELA)).thenReturn(List.of(v1));
        when(aceptaciones.ultimaAceptada(persona, Formacion.ESCUELA)).thenReturn(Optional.empty());
        when(aceptaciones.aceptoAlgunaVez(persona)).thenReturn(false);
        when(cronogramas.de(persona)).thenReturn(Optional.of(cronograma(false)));

        TerminosParaLaPersona terminosDeAna = servicio.para(persona);

        assertThat(terminosDeAna.pide()).isEqualTo(QuePideLaAceptacion.PRIMERA_VEZ);
        AcuerdoDeLaPersona acuerdo = terminosDeAna.acuerdo().orElseThrow();
        assertThat(acuerdo.participante()).isEqualTo("Ana Pérez");
        assertThat(acuerdo.cronograma()).hasValueSatisfying(c -> assertThat(c.huella()).isEqualTo("huella-1"));
    }

    // spec 039 FR-006
    @Test
    @DisplayName("primer ingreso sin cronograma, con «pagó completo» o con el cronograma ya confirmado: «Mi aceptación» sin cronograma")
    void primerIngresoSinNadaQueFirmar() {
        when(terminos.vigente(Formacion.ESCUELA)).thenReturn(Optional.of(v1));
        when(terminos.publicadas(Formacion.ESCUELA)).thenReturn(List.of(v1));
        when(aceptaciones.ultimaAceptada(persona, Formacion.ESCUELA)).thenReturn(Optional.empty());
        when(aceptaciones.aceptoAlgunaVez(persona)).thenReturn(false);

        when(cronogramas.de(persona)).thenReturn(Optional.empty());
        assertThat(servicio.para(persona).acuerdo().orElseThrow().cronograma()).as("sin cronograma").isEmpty();

        when(cronogramas.de(persona)).thenReturn(Optional.of(cronograma(true)));
        assertThat(servicio.para(persona).acuerdo().orElseThrow().cronograma()).as("ya confirmado").isEmpty();
    }

    @Test
    @DisplayName("quien ya estaba dentro y vuelve a aceptar (versión nueva): «Mi aceptación» con su programa real y SIN la sección del cronograma, aunque tenga uno")
    void volverAAceptar() {
        when(terminos.vigente(Formacion.ESCUELA)).thenReturn(Optional.of(v2));
        when(terminos.publicadas(Formacion.ESCUELA)).thenReturn(List.of(v1, v2));
        when(aceptaciones.ultimaAceptada(persona, Formacion.ESCUELA)).thenReturn(Optional.of(1));
        when(aceptaciones.aceptoAlgunaVez(persona)).thenReturn(true);
        when(acuerdos.armar(persona, Formacion.ESCUELA)).thenReturn(new AcuerdoDeLaPersona("Ana Pérez", "Formación RENASER · 90 días", LocalDate.parse("2026-10-26"),
                LocalDate.parse("2027-01-23"), true, Optional.empty()));

        TerminosParaLaPersona terminosDeAna = servicio.para(persona);

        assertThat(terminosDeAna.pide()).isEqualTo(QuePideLaAceptacion.NUEVA_VERSION);
        assertThat(terminosDeAna.pide().bloquea()).isTrue();
        assertThat(terminosDeAna.nota()).contains("Nuevo Acuerdo de transformación");
        AcuerdoDeLaPersona acuerdo = terminosDeAna.acuerdo().orElseThrow();
        assertThat(acuerdo.empezado()).isTrue();
        assertThat(acuerdo.inicio()).isEqualTo(LocalDate.parse("2026-10-26"));
        assertThat(acuerdo.cronograma()).isEmpty();
    }

    @Test
    @DisplayName("una formación sin términos publicados no trae «Mi aceptación»")
    void sinTerminos() {
        when(terminos.vigente(Formacion.ESCUELA)).thenReturn(Optional.empty());
        when(terminos.publicadas(Formacion.ESCUELA)).thenReturn(List.of());
        when(aceptaciones.ultimaAceptada(persona, Formacion.ESCUELA)).thenReturn(Optional.empty());
        when(aceptaciones.aceptoAlgunaVez(persona)).thenReturn(false);

        assertThat(servicio.para(persona).acuerdo()).isEmpty();
    }

    @Test
    @DisplayName("releer lo aceptado trae lo que se le mostró (programa, fechas y si firmó el cronograma); en lo anterior a la spec 039, nada")
    void releerLoAceptado() {
        Aceptacion nueva = new Aceptacion(v2.id(), Formacion.ESCUELA, 2, AHORA, null,
                new LoMostrado("Formación RENASER · 90 días", LocalDate.parse("2026-11-04"), LocalDate.parse("2027-02-01"), "huella-1"));
        Aceptacion vieja = new Aceptacion(v1.id(), Formacion.ESCUELA, 1, AHORA.minusSeconds(86_400), null, LoMostrado.NADA);
        when(terminos.porId(v2.id())).thenReturn(Optional.of(v2));
        when(terminos.porId(v1.id())).thenReturn(Optional.of(v1));
        when(participaciones.deParticipante(persona)).thenReturn(Optional.empty());

        when(aceptaciones.delUsuario(persona)).thenReturn(List.of(nueva, vieja));
        TerminosAceptados aceptados = servicio.aceptados(persona).orElseThrow();
        assertThat(aceptados.acuerdo()).hasValueSatisfying(a -> {
            assertThat(a.programa()).isEqualTo("Formación RENASER · 90 días");
            assertThat(a.inicio()).isEqualTo(LocalDate.parse("2026-11-04"));
            assertThat(a.fin()).isEqualTo(LocalDate.parse("2027-02-01"));
            assertThat(a.cronogramaFirmado()).isTrue();
        });

        when(aceptaciones.delUsuario(persona)).thenReturn(List.of(vieja));
        assertThat(servicio.aceptados(persona).orElseThrow().acuerdo()).isEmpty();
    }
}
