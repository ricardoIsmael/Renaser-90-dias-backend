package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.participante.GruposEnCursoPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-225: el Admin ve el chat de todo grupo en curso sin estar asignado, y ninguna otra regla se afloja.
 * Contra el código viejo (solo la pertenencia vigente), el primer caso falla: el Admin no asignado
 * quedaba afuera.
 */
@ExtendWith(MockitoExtension.class)
class AccesoAChatsDeGrupoTest {

    private static final UUID GRUPO = UUID.randomUUID();
    private static final UUID OTRO_GRUPO = UUID.randomUUID();

    @Mock
    private PertenenciaVigentePort pertenencia;
    @Mock
    private GruposEnCursoPort gruposEnCurso;
    @Mock
    private UserSummaryFinder usuarios;

    private AccesoAChatsDeGrupo acceso;
    private final UserId usuario = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        acceso = new AccesoAChatsDeGrupo(pertenencia, gruposEnCurso, usuarios);
        lenient().when(pertenencia.perteneceAlGrupo(any(), any())).thenReturn(false);
        lenient().when(gruposEnCurso.estaEnCurso(GRUPO)).thenReturn(true);
    }

    private void esUn(UserRole rol, UserStatus estado) {
        when(usuarios.findById(usuario)).thenReturn(Optional.of(new UserSummary(usuario, "X", null, rol, estado)));
    }

    @Test
    @DisplayName("D-225: un Admin activo ve el chat de un grupo en curso donde no está asignado")
    void elAdminVeUnGrupoAjenoEnCurso() {
        esUn(UserRole.ADMIN, UserStatus.ACTIVE);

        assertThat(acceso.puedeVer(GRUPO, usuario)).isTrue();
    }

    @Test
    @DisplayName("D-225: un grupo vencido o cerrado queda oculto también para el Admin")
    void elAdminNoVeUnGrupoQueNoEstaEnCurso() {
        esUn(UserRole.ADMIN, UserStatus.ACTIVE);
        when(gruposEnCurso.estaEnCurso(OTRO_GRUPO)).thenReturn(false);

        assertThat(acceso.puedeVer(OTRO_GRUPO, usuario)).isFalse();
    }

    @Test
    @DisplayName("D-225: un Admin suspendido no ve grupos ajenos")
    void unAdminSuspendidoNoVe() {
        esUn(UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThat(acceso.puedeVer(GRUPO, usuario)).isFalse();
        assertThat(acceso.gruposQueVePorSuRol(usuario)).isEmpty();
    }

    @ParameterizedTest(name = "{0} no asignado no ve el grupo")
    @EnumSource(value = UserRole.class, names = {"MENTOR", "MENTOR_LEAD", "TRAINEE", "ALCHEMIST"})
    @DisplayName("D-225: mentor, líder, aprendiz y (por ahora) alquimista ajenos siguen sin ver el grupo")
    void losDemasRolesSiguenNecesitandoPertenecer(UserRole rol) {
        esUn(rol, UserStatus.ACTIVE);

        assertThat(acceso.puedeVer(GRUPO, usuario)).isFalse();
        assertThat(acceso.gruposQueVePorSuRol(usuario)).isEmpty();
        verify(gruposEnCurso, never()).gruposEnCurso();
    }

    @Test
    @DisplayName("Quien pertenece al grupo lo ve sin que se consulte su rol")
    void laPertenenciaAlcanzaSinMirarElRol() {
        when(pertenencia.perteneceAlGrupo(GRUPO, usuario)).thenReturn(true);

        assertThat(acceso.puedeVer(GRUPO, usuario)).isTrue();
        verify(usuarios, never()).findById(any());
    }

    @Test
    @DisplayName("Un usuario que no existe no ve nada: falla cerrado")
    void unUsuarioInexistenteNoVe() {
        when(usuarios.findById(usuario)).thenReturn(Optional.empty());

        assertThat(acceso.puedeVer(GRUPO, usuario)).isFalse();
    }

    @Test
    @DisplayName("D-225: los grupos que el Admin ve por su rol son todos los que están en curso")
    void elAdminVeTodosLosGruposEnCurso() {
        esUn(UserRole.ADMIN, UserStatus.ACTIVE);
        when(gruposEnCurso.gruposEnCurso()).thenReturn(List.of(GRUPO, OTRO_GRUPO));

        assertThat(acceso.gruposQueVePorSuRol(usuario)).containsExactly(GRUPO, OTRO_GRUPO);
    }
}
