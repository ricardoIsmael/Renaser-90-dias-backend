package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.ConsultarMisFuncionalidadesUseCase.FuncionalidadApagada;
import com.renaser.os.plataforma.application.ports.in.ConsultarMisFuncionalidadesUseCase.MisFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.ActorEvaluado;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.ReglaDeDisponibilidad;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.FuncionalidadNoDisponibleException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolPersonalizadoResumen;
import com.renaser.os.users.api.RolesPersonalizadosFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La regla {@code @Order(20)} de los roles personalizados dentro de la evaluación de verdad (D-272): la copia en memoria,
 * el 404 de la API, {@code noDisponibles} de {@code /mias}, que el apagado manual gana y que un rol desactivado devuelve
 * todo lo de la base. Sin Spring ni base.
 */
@SuppressWarnings("unchecked")
class ReglaDeRolPersonalizadoTest {

    private static final CatalogoDeFuncionalidades CATALOGO = CatalogoDeFuncionalidades.inicial();

    private final UserId mentorJunior = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UUID rolId = UUID.randomUUID();
    private final RolesPersonalizadosFinder finder = mock(RolesPersonalizadosFinder.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final Dobles.Base base = new Dobles.Base();
    private final CopiaDeEstados estados = new CopiaDeEstados(base);
    private final CopiaDeRolesPersonalizados copia = new CopiaDeRolesPersonalizados(finder);
    private InterruptoresService interruptores;
    private MisFuncionalidadesService mias;

    @BeforeEach
    void armar() {
        ObjectProvider<EstadoAutomaticoDeFuncionalidades> sinAutomaticos = mock(ObjectProvider.class);
        when(sinAutomaticos.orderedStream()).thenReturn(Stream.empty());
        ObjectProvider<ReglaDeDisponibilidad> reglas = mock(ObjectProvider.class);
        when(reglas.orderedStream()).thenAnswer(i -> Stream.of(new ReglaDeRolPersonalizado(copia)));
        EstadoVigente estado = new EstadoVigente(estados, sinAutomaticos, reglas);
        interruptores = new InterruptoresService(estado, new RolDelActor(usuarios));
        mias = new MisFuncionalidadesService(estado, new RolDelActor(usuarios));
        asignar(true);
    }

    /** «Mentor junior»: todo lo que MENTOR usa menos eventos, asignado a {@link #mentorJunior}. */
    private void asignar(boolean activo) {
        Set<String> marcadas = new HashSet<>();
        CATALOGO.todas().stream().filter(d -> d.rolesQueLaUsan().contains(UserRole.MENTOR))
                .map(DefinicionDeFuncionalidad::clave).filter(c -> !c.startsWith("eventos")).forEach(marcadas::add);
        when(finder.todos()).thenReturn(List.of(new RolPersonalizadoResumen(rolId, "rp:" + rolId, "Mentor junior",
                null, UserRole.MENTOR, activo, marcadas, 1, 1, mentor, Instant.parse("2026-10-14T15:00:00Z"))));
        when(finder.asignaciones()).thenReturn(activo ? Map.of(mentorJunior, rolId) : Map.of());
        copia.recargar();
    }

    @Test
    @DisplayName("con el rol: eventos es 404 FUNCIONALIDAD_NO_DISPONIBLE; el mentor sin rol personalizado lo usa")
    void cuatrocientosCuatro() {
        assertThatThrownBy(() -> interruptores.exigirPrendidaPara("eventos", mentorJunior))
                .isInstanceOf(FuncionalidadNoDisponibleException.class)
                .extracting(e -> ((FuncionalidadNoDisponibleException) e).clave()).isEqualTo("eventos");
        assertThatCode(() -> interruptores.exigirPrendidaPara("muro.publicar", mentorJunior))
                .doesNotThrowAnyException();
        assertThatCode(() -> interruptores.exigirPrendidaPara("eventos", mentor)).doesNotThrowAnyException();
        assertThat(interruptores.prendidaPara("eventos", mentorJunior)).isFalse();
        assertThat(interruptores.prendida("eventos")).as("un proceso sin actor no ve las reglas").isTrue();
    }

    @Test
    @DisplayName("/mias: eventos y sus hijas en noDisponibles, nada en apagadas, y sin leer la cuenta")
    void noDisponiblesEnMias() {
        MisFuncionalidades suyas = mias.mias(mentorJunior);

        assertThat(suyas.noDisponibles()).containsExactlyInAnyOrder("eventos", "eventos.gestion",
                "eventos.asistencia");
        assertThat(suyas.apagadas()).isEmpty();
        assertThat(mias.mias(mentor).noDisponibles()).isEmpty();
        verify(usuarios, never()).findById(any());
    }

    @Test
    @DisplayName("el apagado manual gana: con muro apagado para MENTOR, el mentor junior ve el 503 de mantenimiento")
    void elApagadoManualGana() {
        base.filas.put("muro", new EstadoDeFuncionalidad("muro", true, Set.of(UserRole.MENTOR), "Muro en pausa", 3,
                mentor, Instant.parse("2026-10-14T15:00:00Z")));
        estados.recargar();
        when(usuarios.findById(mentorJunior)).thenReturn(Optional.of(new UserSummary(mentorJunior, "MJ", null,
                UserRole.MENTOR, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> interruptores.exigirPrendidaPara("muro", mentorJunior))
                .isInstanceOf(FuncionalidadApagadaException.class)
                .isNotInstanceOf(FuncionalidadNoDisponibleException.class).hasMessage("Muro en pausa");
        assertThat(mias.mias(mentorJunior).apagadas()).extracting(FuncionalidadApagada::clave).contains("muro");
    }

    @Test
    @DisplayName("desactivar el rol devuelve todo lo de la base: nada queda no disponible")
    void desactivarDevuelveLaBase() {
        asignar(false);

        assertThat(mias.mias(mentorJunior).noDisponibles()).isEmpty();
        assertThatCode(() -> interruptores.exigirPrendidaPara("eventos", mentorJunior)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("si la base falla al recargar, la copia sigue con lo último que leyó")
    void unaFallaNoVaciaLaCopia() {
        when(finder.todos()).thenThrow(new IllegalStateException("base caida"));

        copia.recargar();

        assertThat(mias.mias(mentorJunior).noDisponibles()).contains("eventos");
    }

    @Test
    @DisplayName("antes de la primera lectura nadie tiene rol personalizado: no se restringe nada")
    void sinLeerNoRestringe() {
        CopiaDeRolesPersonalizados nueva = new CopiaDeRolesPersonalizados(finder);

        assertThat(new ReglaDeRolPersonalizado(nueva).noDisponibleEn(List.of("eventos"), new ActorEvaluado() {
            @Override
            public UserId id() {
                return mentorJunior;
            }

            @Override
            public UserRole rol() {
                return UserRole.MENTOR;
            }
        })).isEmpty();
    }
}
