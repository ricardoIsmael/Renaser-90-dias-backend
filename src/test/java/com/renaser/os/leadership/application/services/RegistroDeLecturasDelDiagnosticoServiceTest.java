package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.TipoDeConsulta;
import com.renaser.os.onboarding.api.DiagnosticoConsultadoEvent;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 026: la constancia de quién abrió el diagnóstico de una persona se anota con el rol real de quien lo abrió. */
class RegistroDeLecturasDelDiagnosticoServiceTest {

    private static final UserId CONSULTOR = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final UserId MENTORA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID GRUPO = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID CONSULTA = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final Instant CUANDO = Instant.parse("2026-11-01T15:30:00Z");

    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final GuardarConsultaDeFichaPort consultas = mock(GuardarConsultaDeFichaPort.class);
    private RegistroDeLecturasDelDiagnosticoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new RegistroDeLecturasDelDiagnosticoService(usuarios, consultas);
    }

    private void elConsultorEs(UserRole rol) {
        when(usuarios.findById(CONSULTOR)).thenReturn(Optional.of(new UserSummary(CONSULTOR, "Quien lee", null, rol, UserStatus.ACTIVE)));
    }

    @Test
    @DisplayName("anota quién, con qué rol (el de su cuenta), a quién, desde qué grupo, con qué mentor y el instante de la lectura")
    void anotaLaLectura() {
        elConsultorEs(UserRole.MENTOR_LEAD);

        servicio.registrar(new DiagnosticoConsultadoEvent(CONSULTA, CONSULTOR, ANA, GRUPO, MENTORA, CUANDO));

        ArgumentCaptor<ConsultaDeFicha> anotada = ArgumentCaptor.forClass(ConsultaDeFicha.class);
        verify(consultas).insertar(anotada.capture());
        assertThat(anotada.getValue()).satisfies(c -> {
            assertThat(c.id()).isEqualTo(CONSULTA);
            assertThat(c.tipo()).isEqualTo(TipoDeConsulta.DIAGNOSTICO);
            assertThat(c.consultorId()).isEqualTo(CONSULTOR);
            assertThat(c.rolConsultor()).isEqualTo(UserRole.MENTOR_LEAD);
            assertThat(c.personaId()).isEqualTo(ANA);
            assertThat(c.grupoId()).isEqualTo(GRUPO);
            assertThat(c.mentorId()).isEqualTo(MENTORA);
            assertThat(c.semanaDesde()).isNull();
            assertThat(c.consultadaEn()).isEqualTo(CUANDO);
        });
    }

    @Test
    @DisplayName("lo anotan igual el mentor, la guía, Administración y Alquimista; sin grupo ni mentor también")
    void cadaRolSeAnota() {
        for (UserRole rol : new UserRole[]{UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.ADMIN, UserRole.ALCHEMIST}) {
            elConsultorEs(rol);
            servicio.registrar(new DiagnosticoConsultadoEvent(UUID.randomUUID(), CONSULTOR, ANA, null, null, CUANDO));
        }

        ArgumentCaptor<ConsultaDeFicha> anotadas = ArgumentCaptor.forClass(ConsultaDeFicha.class);
        verify(consultas, org.mockito.Mockito.times(4)).insertar(anotadas.capture());
        assertThat(anotadas.getAllValues()).extracting(ConsultaDeFicha::rolConsultor)
                .containsExactly(UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.ADMIN, UserRole.ALCHEMIST);
        assertThat(anotadas.getAllValues()).allSatisfy(c -> assertThat(c.grupoId()).isNull());
    }

    @Test
    @DisplayName("una cuenta que ya no existe no tumba la entrega: se avisa en el registro y no se anota nada")
    void cuentaQueYaNoExiste() {
        when(usuarios.findById(CONSULTOR)).thenReturn(Optional.empty());

        servicio.registrar(new DiagnosticoConsultadoEvent(CONSULTA, CONSULTOR, ANA, GRUPO, MENTORA, CUANDO));

        verify(consultas, never()).insertar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("un aprendiz como consultor no es una lectura que anotar (ni se reintenta para siempre)")
    void unAprendizNoLee() {
        elConsultorEs(UserRole.TRAINEE);

        servicio.registrar(new DiagnosticoConsultadoEvent(CONSULTA, CONSULTOR, ANA, null, null, CUANDO));

        verify(consultas, never()).insertar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("la persona leyéndose a sí misma no deja fila")
    void nadieSeAnotaASiMismo() {
        elConsultorEs(UserRole.TRAINEE_GUIDE);

        servicio.registrar(new DiagnosticoConsultadoEvent(CONSULTA, CONSULTOR, CONSULTOR, null, null, CUANDO));

        verify(consultas, never()).insertar(org.mockito.ArgumentMatchers.any());
    }
}
