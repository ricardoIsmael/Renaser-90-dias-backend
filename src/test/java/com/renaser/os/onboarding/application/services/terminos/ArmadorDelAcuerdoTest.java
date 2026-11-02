package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.AcuerdoDeLaPersona;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FechasDelProgramaFinder;
import com.renaser.os.users.api.FechasDelProgramaFinder.FechasDelPrograma;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** «Mi aceptación» (spec 039): el nombre completo del perfil, el programa con su duración y las fechas que dice el servidor. Las fechas no se calculan aquí. */
@ExtendWith(MockitoExtension.class)
class ArmadorDelAcuerdoTest {

    @Mock
    private ConsultarActorPort actores;
    @Mock
    private FechasDelProgramaFinder programas;

    private ArmadorDelAcuerdo armador;
    private UserId persona;

    @BeforeEach
    void preparar() {
        armador = new ArmadorDelAcuerdo(actores, programas);
        persona = UserId.of(UUID.randomUUID());
        when(actores.deActor(persona)).thenReturn(Optional.of(new ActorOnboarding(persona, false, "  Ana María Pérez Soto ", false)));
    }

    @Test
    @DisplayName("sin activar: nombre completo del perfil, «Formación RENASER · 90 días», inicio mañana y fin 89 días después; todavía no empezó")
    void aprendizDeLaEscuela() {
        when(programas.de(persona)).thenReturn(Optional.of(new FechasDelPrograma(Formacion.ESCUELA, 90, false, LocalDate.parse("2026-11-03"), LocalDate.parse("2027-01-31"))));

        AcuerdoDeLaPersona acuerdo = armador.armar(persona, Formacion.ESCUELA);

        assertThat(acuerdo.participante()).isEqualTo("Ana María Pérez Soto");
        assertThat(acuerdo.programa()).isEqualTo("Formación RENASER · 90 días");
        assertThat(acuerdo.inicio()).isEqualTo(LocalDate.parse("2026-11-03"));
        assertThat(acuerdo.fin()).isEqualTo(LocalDate.parse("2027-01-31"));
        assertThat(acuerdo.empezado()).isFalse();
        assertThat(acuerdo.cronograma()).as("el cronograma lo agrega quien sabe si es el primer ingreso").isEmpty();
    }

    @Test
    @DisplayName("el Diplomado dice sus 3 meses o su año, y la Certificación sus 90 días")
    void duracionesPorFormacion() {
        when(programas.de(persona)).thenReturn(Optional.of(new FechasDelPrograma(Formacion.DIPLOMADO, 90, false, LocalDate.parse("2026-11-03"), LocalDate.parse("2027-01-31"))));
        assertThat(armador.armar(persona, Formacion.DIPLOMADO).programa()).isEqualTo("Diplomado RENASER · 3 meses");

        when(programas.de(persona)).thenReturn(Optional.of(new FechasDelPrograma(Formacion.DIPLOMADO, 365, false, LocalDate.parse("2026-11-03"), LocalDate.parse("2027-11-02"))));
        AcuerdoDeLaPersona deUnAnio = armador.armar(persona, Formacion.DIPLOMADO);
        assertThat(deUnAnio.programa()).isEqualTo("Diplomado RENASER · 1 año");
        assertThat(deUnAnio.fin()).isEqualTo(LocalDate.parse("2027-11-02"));

        when(programas.de(persona)).thenReturn(Optional.of(new FechasDelPrograma(Formacion.CERTIFICACION, 90, false, LocalDate.parse("2026-11-03"), LocalDate.parse("2027-01-31"))));
        assertThat(armador.armar(persona, Formacion.CERTIFICACION).programa()).isEqualTo("Certificación RENASER · 90 días");
    }

    @Test
    @DisplayName("quien ya empezó (vuelve a aceptar): sus fechas reales y empezado verdadero")
    void yaEmpezo() {
        when(programas.de(persona)).thenReturn(Optional.of(new FechasDelPrograma(Formacion.ESCUELA, 90, true, LocalDate.parse("2026-10-26"), LocalDate.parse("2027-01-23"))));

        AcuerdoDeLaPersona acuerdo = armador.armar(persona, Formacion.ESCUELA);

        assertThat(acuerdo.empezado()).isTrue();
        assertThat(acuerdo.inicio()).isEqualTo(LocalDate.parse("2026-10-26"));
        assertThat(acuerdo.fin()).isEqualTo(LocalDate.parse("2027-01-23"));
    }

    @Test
    @DisplayName("sin programa que mostrar (personal sin fila o sin activar): solo el nombre del programa, sin duración ni fechas")
    void sinPrograma() {
        when(programas.de(persona)).thenReturn(Optional.empty());
        assertThat(armador.armar(persona, Formacion.ESCUELA).programa()).isEqualTo("Formación RENASER");
        assertThat(armador.armar(persona, Formacion.ESCUELA).inicio()).isNull();
        assertThat(armador.armar(persona, Formacion.ESCUELA).fin()).isNull();

        when(programas.de(persona)).thenReturn(Optional.of(new FechasDelPrograma(Formacion.ESCUELA, 90, false, null, null)));
        AcuerdoDeLaPersona sinActivar = armador.armar(persona, Formacion.ESCUELA);
        assertThat(sinActivar.programa()).isEqualTo("Formación RENASER");
        assertThat(sinActivar.inicio()).isNull();
        assertThat(sinActivar.empezado()).isFalse();
    }
}
