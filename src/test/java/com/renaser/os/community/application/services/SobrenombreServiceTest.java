package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.sobrenombre.AdministrarSobrenombresUseCase;
import com.renaser.os.community.application.ports.out.sobrenombre.SobrenombresPort;
import com.renaser.os.community.application.ports.out.sobrenombre.SobrenombresPort.FilaDeLaLista;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * La administración de sobrenombres y de palabras prohibidas (spec 046 FR-005 y FR-006) exige ser moderador del Muro: ADMIN o
 * ALCHEMIST activos. Lo comprueba el servicio (spec 050, D19).
 */
@ExtendWith(MockitoExtension.class)
class SobrenombreServiceTest {

    private static final Instant AHORA = Instant.parse("2027-03-10T15:00:00Z");

    @Mock private SobrenombresPort sobrenombres;
    @Mock private AlcanceDeFormacionFinder formaciones;
    @Mock private UserSummaryFinder usuarios;
    @Mock private ApplicationEventPublisher eventos;

    private SobrenombreService servicio;
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final UserId persona = UserId.of(UUID.randomUUID());

    @BeforeEach
    void preparar() {
        servicio = new SobrenombreService(sobrenombres, formaciones, usuarios, eventos, FixedClock.at(AHORA));
    }

    private void actorCon(UserRole rol, UserStatus estado) {
        lenient().when(usuarios.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Actor", null, rol, estado)));
    }

    /** Las seis operaciones de administración, cada una con lo mínimo para llegar a lo suyo si el actor pasara. */
    private List<Runnable> lasSeisOperaciones() {
        return List.of(
                () -> servicio.de(actor, persona),
                () -> servicio.cambiar(actor, persona, "Puma", "Lo pidió por soporte"),
                () -> servicio.listar(actor, "puma"),
                () -> servicio.palabras(actor),
                () -> servicio.agregarPalabra(actor, "tigre"),
                () -> servicio.quitarPalabra(actor, "tigre"));
    }

    private void nadaSeTocoDeLaAdministracion() {
        verifyNoInteractions(sobrenombres, eventos, formaciones);
    }

    @ParameterizedTest(name = "{0} no puede administrar sobrenombres ni palabras: 403 y no se toca nada")
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE", "MENTOR_LEAD", "TRAINEE", "IT_SUPPORT"})
    @DisplayName("spec 046 FR-005, FR-006: quien no modera el Muro recibe 403 en las seis operaciones y no cambia nada")
    void quienNoModeraNoAdministra(UserRole rol) {
        actorCon(rol, UserStatus.ACTIVE);

        for (Runnable operacion : lasSeisOperaciones()) {
            assertThatThrownBy(operacion::run).isInstanceOf(NotAuthorizedException.class);
        }
        nadaSeTocoDeLaAdministracion();
    }

    @ParameterizedTest(name = "{0} suspendido no administra: 403")
    @EnumSource(value = UserRole.class, names = {"ADMIN", "ALCHEMIST"})
    @DisplayName("una cuenta de moderación suspendida recibe 403 en las seis operaciones")
    void moderadorSuspendidoNoAdministra(UserRole rol) {
        actorCon(rol, UserStatus.SUSPENDED);

        for (Runnable operacion : lasSeisOperaciones()) {
            assertThatThrownBy(operacion::run).isInstanceOf(NotAuthorizedException.class);
        }
        nadaSeTocoDeLaAdministracion();
    }

    @Test
    @DisplayName("un actor que no existe recibe el mismo 403, no un 404 que diga si existe")
    void actorInexistente() {
        when(usuarios.findById(actor)).thenReturn(Optional.empty());

        for (Runnable operacion : lasSeisOperaciones()) {
            assertThatThrownBy(operacion::run).isInstanceOf(NotAuthorizedException.class);
        }
        nadaSeTocoDeLaAdministracion();
    }

    @ParameterizedTest(name = "{0} activo ve, cambia, lista y edita las palabras")
    @EnumSource(value = UserRole.class, names = {"ADMIN", "ALCHEMIST"})
    @DisplayName("spec 046 FR-005, FR-006: ADMIN y ALCHEMIST activos siguen administrando")
    void moderadorAdministra(UserRole rol) {
        actorCon(rol, UserStatus.ACTIVE);
        when(sobrenombres.de(persona)).thenReturn(Optional.of(new SobrenombresPort.FilaDeSobrenombre("Lince", AHORA, AHORA, null)));
        when(sobrenombres.palabrasDeAdministracion()).thenReturn(List.of("tigre"));
        when(sobrenombres.listar(anyString(), anyInt())).thenReturn(List.of(new FilaDeLaLista(persona, "Lince", "Ana Pérez", AHORA)));
        when(formaciones.de(actor)).thenReturn(AlcanceDeFormacion.todas());
        when(formaciones.dentroDelAlcance(any(), any())).thenReturn(Set.of(persona));

        assertThat(servicio.de(actor, persona)).isEqualTo("Lince");
        assertThat(servicio.listar(actor, "lin")).extracting(AdministrarSobrenombresUseCase.PersonaConSobrenombre::sobrenombre)
                .containsExactly("Lince");
        assertThat(servicio.palabras(actor).deAdministracion()).containsExactly("tigre");
        assertThat(servicio.agregarPalabra(actor, "Tigre").deAdministracion()).containsExactly("tigre");
        assertThat(servicio.quitarPalabra(actor, "tigre").deAdministracion()).containsExactly("tigre");
        // y cambiar: pasa el guard y llega a la regla de la forma (aquí, motivo demasiado corto, que no es un 403)
        assertThatThrownBy(() -> servicio.cambiar(actor, persona, "Puma", "no"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(eventos);
    }
}
