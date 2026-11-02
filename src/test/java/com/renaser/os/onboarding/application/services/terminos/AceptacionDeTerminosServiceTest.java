package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.in.pagos.CronogramaParaFirmarUseCase;
import com.renaser.os.onboarding.application.ports.in.pagos.CronogramaParaFirmarUseCase.CronogramaParaFirmar;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.AcuerdoDeLaPersona;
import com.renaser.os.onboarding.application.ports.in.terminos.RegistrarAceptacionDeTerminosUseCase.PedidoDeAceptacion;
import com.renaser.os.onboarding.application.ports.in.terminos.RegistrarAceptacionDeTerminosUseCase.ResultadoDeLaAceptacion;
import com.renaser.os.onboarding.application.ports.out.media.LoadMediaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort.LoMostrado;
import com.renaser.os.onboarding.application.ports.out.terminos.TerminosPort;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.onboarding.domain.model.terminos.ContenidoDeTerminos;
import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La constancia de la aceptación con lo que se le mostró (spec 039): guarda el programa, el inicio y el fin; rechaza un inicio vencido; y confirma el cronograma si la
 * persona firmó el vigente (y solo ese). Sin Spring: los puertos son simulados y las reglas son las del servicio.
 */
@ExtendWith(MockitoExtension.class)
class AceptacionDeTerminosServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-03T04:30:00Z"));
    private static final LocalDate INICIO = LocalDate.parse("2026-11-03");
    private static final LocalDate FIN = LocalDate.parse("2027-01-31");

    @Mock
    private TerminosPort terminos;
    @Mock
    private AceptacionesDeTerminosPort aceptaciones;
    @Mock
    private AlcanceDeFormacionFinder formaciones;
    @Mock
    private LeerRespuestasPorClavePort respuestas;
    @Mock
    private LoadMediaPort medias;
    @Mock
    private ArmadorDelAcuerdo acuerdos;
    @Mock
    private CronogramaParaFirmarUseCase cronogramas;
    @Mock
    private IdGenerator ids;

    private AceptacionDeTerminosService servicio;
    private UserId persona;
    private VersionDeTerminos vigente;

    @BeforeEach
    void preparar() {
        servicio = new AceptacionDeTerminosService(terminos, aceptaciones, formaciones, respuestas, medias, acuerdos, cronogramas, ids, CLOCK);
        persona = UserId.of(UUID.randomUUID());
        vigente = VersionDeTerminos.borrador(UUID.randomUUID(), Formacion.ESCUELA, new ContenidoDeTerminos("Términos", "Intro", List.of("Uno"), "Acepto"), null,
                CLOCK.now()).publicada(2, "Nuevo Acuerdo de transformación", true, null, CLOCK.now());
        lenient().when(formaciones.formacionDe(persona)).thenReturn(Optional.of(Formacion.ESCUELA));
        lenient().when(terminos.vigente(Formacion.ESCUELA)).thenReturn(Optional.of(vigente));
        lenient().when(respuestas.mediaDe(any(), any())).thenReturn(Optional.empty());
        lenient().when(ids.newId()).thenReturn(UUID.randomUUID());
    }

    private void conAcuerdo(String participante, LocalDate inicio, LocalDate fin) {
        when(acuerdos.armar(persona, Formacion.ESCUELA)).thenReturn(new AcuerdoDeLaPersona(participante, "Formación RENASER · 90 días", inicio, fin, false, Optional.empty()));
    }

    private LoMostrado loGuardado() {
        ArgumentCaptor<LoMostrado> mostrado = ArgumentCaptor.forClass(LoMostrado.class);
        verify(aceptaciones).registrar(any(), eq(persona), eq(vigente.id()), eq(CLOCK.now()), any(), mostrado.capture());
        return mostrado.getValue();
    }

    private static CronogramaParaFirmar cronograma(String huella, boolean yaConfirmado) {
        return new CronogramaParaFirmar(Moneda.PEN, List.of(), huella, yaConfirmado);
    }

    @Test
    @DisplayName("la constancia guarda lo que se le mostró: programa, inicio y fin; sin cronograma firmado no hay huella ni confirmación")
    void guardaLoMostrado() {
        conAcuerdo("Ana María Pérez Soto", INICIO, FIN);

        ResultadoDeLaAceptacion resultado = servicio.registrar(persona, new PedidoDeAceptacion(vigente.id(), INICIO, null));

        assertThat(loGuardado()).isEqualTo(new LoMostrado("Formación RENASER · 90 días", INICIO, FIN, null));
        assertThat(resultado.cronograma()).isEmpty();
        verify(cronogramas, never()).de(any());
    }

    @Test
    @DisplayName("una app anterior (sin versión ni inicio en el pedido) acepta igual y la constancia guarda lo de ahora")
    void unaAppAnterior() {
        conAcuerdo("Ana María Pérez Soto", INICIO, FIN);

        servicio.registrar(persona, PedidoDeAceptacion.deLaVersion(null));

        assertThat(loGuardado()).isEqualTo(new LoMostrado("Formación RENASER · 90 días", INICIO, FIN, null));
    }

    @Test
    @DisplayName("pasó la medianoche: el inicio que vio ya no es el de ahora → 409 y no queda constancia")
    void inicioVencido() {
        conAcuerdo("Ana María Pérez Soto", LocalDate.parse("2026-11-04"), LocalDate.parse("2027-02-01"));

        assertThatThrownBy(() -> servicio.registrar(persona, new PedidoDeAceptacion(vigente.id(), INICIO, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Pasó la medianoche");

        verify(aceptaciones, never()).registrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("una versión que dejó de ser la vigente sigue siendo 409 (spec 027)")
    void versionVencida() {
        assertThatThrownBy(() -> servicio.registrar(persona, new PedidoDeAceptacion(UUID.randomUUID(), null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Los términos cambiaron");

        verify(aceptaciones, never()).registrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("con la huella del cronograma vigente: queda la huella en la constancia y se devuelve qué confirmar, con el nombre completo del perfil")
    void firmaElCronograma() {
        conAcuerdo("Ana María Pérez Soto", INICIO, FIN);
        when(cronogramas.de(persona)).thenReturn(Optional.of(cronograma("huella-1", false)));

        ResultadoDeLaAceptacion resultado = servicio.registrar(persona, new PedidoDeAceptacion(vigente.id(), INICIO, "huella-1"));

        assertThat(loGuardado().cronogramaHuella()).isEqualTo("huella-1");
        assertThat(resultado.cronograma()).hasValueSatisfying(c -> {
            assertThat(c.nombre()).isEqualTo("Ana María Pérez Soto");
            assertThat(c.huella()).isEqualTo("huella-1");
        });
    }

    @Test
    @DisplayName("Administración cambió el cronograma mientras leía: 409, no queda constancia ni confirmación")
    void cronogramaCambiado() {
        conAcuerdo("Ana María Pérez Soto", INICIO, FIN);
        when(cronogramas.de(persona)).thenReturn(Optional.of(cronograma("huella-nueva", false)));

        assertThatThrownBy(() -> servicio.registrar(persona, new PedidoDeAceptacion(vigente.id(), INICIO, "huella-1")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Tu cronograma cambió");

        verify(aceptaciones, never()).registrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("le quitaron el cronograma mientras leía (ahora «pagó completo»): 409")
    void cronogramaQuitado() {
        conAcuerdo("Ana María Pérez Soto", INICIO, FIN);
        when(cronogramas.de(persona)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(persona, new PedidoDeAceptacion(vigente.id(), INICIO, "huella-1"))).isInstanceOf(IllegalStateException.class);

        verify(aceptaciones, never()).registrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("si ya lo había confirmado con esa misma huella (otro teléfono, «Mi ingreso») la constancia lo anota y no se confirma de nuevo")
    void yaConfirmado() {
        conAcuerdo("Ana María Pérez Soto", INICIO, FIN);
        when(cronogramas.de(persona)).thenReturn(Optional.of(cronograma("huella-1", true)));

        ResultadoDeLaAceptacion resultado = servicio.registrar(persona, new PedidoDeAceptacion(vigente.id(), INICIO, "huella-1"));

        assertThat(loGuardado().cronogramaHuella()).isEqualTo("huella-1");
        assertThat(resultado.cronograma()).isEmpty();
    }

    @Test
    @DisplayName("el nombre de la confirmación respeta el tope de 120 letras de la spec 032")
    void nombreLargo() {
        conAcuerdo("Ana ".repeat(60).trim(), INICIO, FIN);
        when(cronogramas.de(persona)).thenReturn(Optional.of(cronograma("huella-1", false)));

        ResultadoDeLaAceptacion resultado = servicio.registrar(persona, new PedidoDeAceptacion(vigente.id(), INICIO, "huella-1"));

        assertThat(resultado.cronograma().orElseThrow().nombre().length()).isLessThanOrEqualTo(120);
    }

    @Test
    @DisplayName("una formación sin términos publicados no deja constancia ni confirma nada")
    void sinTerminosPublicados() {
        when(terminos.vigente(Formacion.ESCUELA)).thenReturn(Optional.empty());

        ResultadoDeLaAceptacion resultado = servicio.registrar(persona, new PedidoDeAceptacion(null, INICIO, "huella-1"));

        assertThat(resultado.cronograma()).isEmpty();
        verify(aceptaciones, never()).registrar(any(), any(), any(), any(), any(), any());
    }
}
