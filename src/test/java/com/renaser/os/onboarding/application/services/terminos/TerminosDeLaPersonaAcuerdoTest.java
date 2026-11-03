package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.AcuerdoAceptado;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.AcuerdoDeLaPersona;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.TerminosAceptados;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.TerminosParaLaPersona;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort.Aceptacion;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort.LoMostrado;
import com.renaser.os.onboarding.application.ports.out.terminos.TerminosPort;
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
 * Qué trae la lectura de los términos (spec 039): «Mi aceptación» con el nombre, el programa y las fechas —en el primer ingreso y al volver a aceptar un texto nuevo—.
 * <b>Nunca el cronograma de pagos</b> (D-315, decisión del dueño del 2026-11-02): no se muestra ni se firma con los términos; la persona lo ve y lo confirma en «Mi ingreso»
 * (spec 032), tenga o no cuotas. Sin Spring: los puertos son simulados.
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

    private TerminosDeLaPersonaService servicio;
    private UserId persona;
    private VersionDeTerminos v1;
    private VersionDeTerminos v2;
    private final AcuerdoDeLaPersona base = new AcuerdoDeLaPersona("Ana Pérez", "Formación RENASER · 90 días", LocalDate.parse("2026-11-04"), LocalDate.parse("2027-02-01"), false);

    @BeforeEach
    void preparar() {
        servicio = new TerminosDeLaPersonaService(terminos, aceptaciones, formaciones, catalogo, participaciones, firmas, acuerdos);
        persona = UserId.of(UUID.randomUUID());
        ContenidoDeTerminos contenido = new ContenidoDeTerminos("Términos y condiciones", "Intro", List.of("Una", "Dos"), "He leído y acepto");
        v1 = VersionDeTerminos.borrador(UUID.randomUUID(), Formacion.ESCUELA, contenido, null, AHORA).publicada(1, null, false, null, AHORA);
        v2 = VersionDeTerminos.borrador(UUID.randomUUID(), Formacion.ESCUELA, contenido, 1, AHORA).publicada(2, "Nuevo Acuerdo de transformación", true, null, AHORA);
        lenient().when(formaciones.formacionDe(persona)).thenReturn(Optional.of(Formacion.ESCUELA));
        lenient().when(catalogo.de(Formacion.ESCUELA)).thenReturn(new FormacionConfigurada(Formacion.ESCUELA, "Escuela Renaser", "", true, List.of(90), 1));
        lenient().when(participaciones.deParticipante(persona)).thenReturn(Optional.empty());
        lenient().when(acuerdos.armar(persona, Formacion.ESCUELA)).thenReturn(base);
    }

    // spec 039 FR-003; decisión 2026-11-02 pagos fuera de términos
    @Test
    @DisplayName("primer ingreso: «Mi aceptación» con el nombre, el programa y las fechas; sin ningún cronograma de pagos")
    void primerIngreso() {
        when(terminos.vigente(Formacion.ESCUELA)).thenReturn(Optional.of(v1));
        when(terminos.publicadas(Formacion.ESCUELA)).thenReturn(List.of(v1));
        when(aceptaciones.ultimaAceptada(persona, Formacion.ESCUELA)).thenReturn(Optional.empty());
        when(aceptaciones.aceptoAlgunaVez(persona)).thenReturn(false);

        TerminosParaLaPersona terminosDeAna = servicio.para(persona);

        assertThat(terminosDeAna.pide()).isEqualTo(QuePideLaAceptacion.PRIMERA_VEZ);
        AcuerdoDeLaPersona acuerdo = terminosDeAna.acuerdo().orElseThrow();
        assertThat(acuerdo.participante()).isEqualTo("Ana Pérez");
        assertThat(acuerdo.programa()).isEqualTo("Formación RENASER · 90 días");
        assertThat(acuerdo.inicio()).isEqualTo(LocalDate.parse("2026-11-04"));
        assertThat(acuerdo.fin()).isEqualTo(LocalDate.parse("2027-02-01"));
    }

    // decisión 2026-11-02 pagos fuera de términos
    @Test
    @DisplayName("el acuerdo que lee la firma no puede llevar cronograma: ni su componente existe, así que nadie lo puede volver a firmar con los términos")
    void elAcuerdoNoLlevaCronograma() {
        assertThat(AcuerdoDeLaPersona.class.getRecordComponents()).extracting(c -> c.getName().toLowerCase())
                .noneMatch(nombre -> nombre.contains("cronograma") || nombre.contains("schedule") || nombre.contains("huella"));
        assertThat(AcuerdoAceptado.class.getRecordComponents()).extracting(c -> c.getName().toLowerCase())
                .noneMatch(nombre -> nombre.contains("cronograma") || nombre.contains("schedule") || nombre.contains("huella"));
    }

    @Test
    @DisplayName("quien ya estaba dentro y vuelve a aceptar (versión nueva): «Mi aceptación» con su programa real")
    void volverAAceptar() {
        when(terminos.vigente(Formacion.ESCUELA)).thenReturn(Optional.of(v2));
        when(terminos.publicadas(Formacion.ESCUELA)).thenReturn(List.of(v1, v2));
        when(aceptaciones.ultimaAceptada(persona, Formacion.ESCUELA)).thenReturn(Optional.of(1));
        when(aceptaciones.aceptoAlgunaVez(persona)).thenReturn(true);
        when(acuerdos.armar(persona, Formacion.ESCUELA)).thenReturn(new AcuerdoDeLaPersona("Ana Pérez", "Formación RENASER · 90 días", LocalDate.parse("2026-10-26"),
                LocalDate.parse("2027-01-23"), true));

        TerminosParaLaPersona terminosDeAna = servicio.para(persona);

        assertThat(terminosDeAna.pide()).isEqualTo(QuePideLaAceptacion.NUEVA_VERSION);
        assertThat(terminosDeAna.pide().bloquea()).isTrue();
        assertThat(terminosDeAna.nota()).contains("Nuevo Acuerdo de transformación");
        AcuerdoDeLaPersona acuerdo = terminosDeAna.acuerdo().orElseThrow();
        assertThat(acuerdo.empezado()).isTrue();
        assertThat(acuerdo.inicio()).isEqualTo(LocalDate.parse("2026-10-26"));
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
    @DisplayName("releer lo aceptado trae lo que se le mostró (programa y fechas); en lo anterior a la spec 039, nada")
    void releerLoAceptado() {
        Aceptacion nueva = new Aceptacion(v2.id(), Formacion.ESCUELA, 2, AHORA, null,
                new LoMostrado("Formación RENASER · 90 días", LocalDate.parse("2026-11-04"), LocalDate.parse("2027-02-01")));
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
        });

        when(aceptaciones.delUsuario(persona)).thenReturn(List.of(vieja));
        assertThat(servicio.aceptados(persona).orElseThrow().acuerdo()).isEmpty();
    }
}
