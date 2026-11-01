package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarDiagnosticoDePersonaUseCase.ConsultaDiagnosticoDePersona;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.PersonaLeida;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SIN_MENTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El diagnóstico de una persona para el líder, Administración y Alquimista (spec 026, 026-11): a quién deja ver y que lo que lee es lo que arma {@code onboarding}.
 * No deja constancia en {@code consultas_de_ficha}: el diagnóstico no es de una semana.
 */
class DiagnosticoDePersonaServiceTest {

    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId SOPORTE = id("d3");
    private static final UserId MENTORA = id("a1");
    private static final UserId ANA = id("b1");
    private static final UserId AJENA = id("b9");

    private final BancoDeCumplimiento banco = new BancoDeCumplimiento();
    private final DiagnosticoDeLaPersonaFinder lectura = mock(DiagnosticoDeLaPersonaFinder.class);
    private final LecturaDelDiagnostico deAna = LecturaDelDiagnostico.sinDiagnostico(new PersonaLeida(ANA.value().toString(), "Ana Pérez"),
            "CON_DIAGNOSTICO", "COMPLETADO", 1, null);
    private DiagnosticoDePersonaService servicio;

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    @BeforeEach
    void preparar() {
        servicio = new DiagnosticoDePersonaService(banco.acceso(), banco.personas(), banco.programa(), lectura, banco.reloj);
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        banco.usuario(SOPORTE, "Soporte TI", UserRole.IT_SUPPORT, UserStatus.ACTIVE);
        banco.grupoConMentor(FENIX, "Grupo Fénix", MENTORA, "Luisa Rojas");
        banco.aprendiz(FENIX, ANA, "Ana Pérez");
        when(lectura.lecturaDe(ANA)).thenReturn(deAna);
    }

    private LecturaDelDiagnostico leer(UserId actor, UserId persona) {
        return servicio.diagnosticoDe(new ConsultaDiagnosticoDePersona(actor, FENIX, persona.value()));
    }

    @Test
    @DisplayName("el líder, Administración y Alquimista leen el diagnóstico de una persona de un grupo con mentor; no se deja constancia")
    void losTresRolesLeen() {
        for (UserId actor : List.of(LIDER, ADMIN, ALQUIMISTA)) {
            assertThat(leer(actor, ANA)).isSameAs(deAna);
        }

        verify(lectura, org.mockito.Mockito.times(3)).lecturaDe(ANA);
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("autorización negativa: un mentor, la propia persona y Soporte TI reciben 403 y no se lee nada")
    void sinPermiso() {
        for (UserId actor : List.of(MENTORA, ANA, SOPORTE)) {
            assertThatThrownBy(() -> leer(actor, ANA)).isInstanceOf(NotAuthorizedException.class);
        }
        verifyNoInteractions(lectura);
    }

    @Test
    @DisplayName("autorización negativa: el líder o Administración con la cuenta suspendida reciben 403")
    void suspendidos() {
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> leer(LIDER, ANA)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> leer(ADMIN, ANA)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(lectura);
    }

    @Test
    @DisplayName("aislamiento: una persona que no es de ese grupo, una que no existe o un grupo sin mentor dan 404")
    void noEsDeEseGrupo() {
        banco.usuario(AJENA, "Ana de otro grupo", UserRole.TRAINEE, UserStatus.ACTIVE);

        assertThatThrownBy(() -> leer(LIDER, AJENA)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> leer(LIDER, id("ff"))).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.diagnosticoDe(new ConsultaDiagnosticoDePersona(LIDER, SIN_MENTOR, ANA.value())))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(lectura);
    }
}
