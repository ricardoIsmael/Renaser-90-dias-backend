package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.ComunidadDeLaFormacionFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * La regla única de quién ve cada conversación (spec 024): su formación está en el alcance de quien mira, su tipo de comunidad está prendido y, en un directo,
 * la otra persona también pertenece a esa formación.
 */
@ExtendWith(MockitoExtension.class)
class VisibilidadPorFormacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-31T15:00:00Z");

    @Mock
    private AlcanceDeFormacionFinder formaciones;
    @Mock
    private ComunidadDeLaFormacionFinder comunidades;
    @Mock
    private ListarUsuariosDeConversacionPort usuariosDeConversacion;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private VisibilidadPorFormacion visibilidad;

    private final UserId deLaEscuela = UserId.of(UUID.randomUUID());
    private final UserId delDiplomado = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        visibilidad = new VisibilidadPorFormacion(formaciones, comunidades, usuariosDeConversacion, userSummaryFinder);
        lenient().when(formaciones.de(deLaEscuela)).thenReturn(AlcanceDeFormacion.de(Formacion.ESCUELA));
        lenient().when(formaciones.de(delDiplomado)).thenReturn(AlcanceDeFormacion.de(Formacion.DIPLOMADO));
        lenient().when(formaciones.de(admin)).thenReturn(AlcanceDeFormacion.todas());
        for (Formacion formacion : Formacion.values()) {
            lenient().when(comunidades.prendidosDe(formacion)).thenReturn(EnumSet.allOf(TipoDeComunidad.class));
        }
    }

    @Test
    @DisplayName("Una conversación de otra formación es invisible para quien no la ve, y da el mismo 404 que una inexistente")
    void laDeOtraFormacionEsInvisible() {
        Conversacion global = general(Formacion.DIPLOMADO);

        assertThat(visibilidad.puedeVer(global, delDiplomado)).isTrue();
        assertThat(visibilidad.puedeVer(global, deLaEscuela)).isFalse();
        assertThat(visibilidad.puedeVer(global, admin)).isTrue();
        assertThatThrownBy(() -> visibilidad.exigirQueLaVea(global, deLaEscuela)).isInstanceOf(NoSuchElementException.class)
                .hasMessage("Conversacion no encontrada: " + global.id());
    }

    @Test
    @DisplayName("Un tipo de comunidad apagado en la formación esconde sus conversaciones (a todos) sin tocar las otras formaciones")
    void unTipoApagadoSeEsconde() {
        Conversacion globalEscuela = general(Formacion.ESCUELA);
        Conversacion globalDiplomado = general(Formacion.DIPLOMADO);
        when(comunidades.prendidosDe(Formacion.DIPLOMADO)).thenReturn(EnumSet.of(TipoDeComunidad.GRUPO, TipoDeComunidad.SOPORTE, TipoDeComunidad.DIRECTO));

        assertThat(visibilidad.puedeVer(globalDiplomado, delDiplomado)).isFalse();
        assertThat(visibilidad.puedeVer(globalDiplomado, admin)).isFalse();
        assertThat(visibilidad.puedeVer(globalEscuela, deLaEscuela)).isTrue();
    }

    @Test
    @DisplayName("Un directo se ve solo si la otra persona sigue en la formación del directo; Administración cuenta como de cualquiera")
    void unDirectoNecesitaALosDosEnLaFormacion() {
        Conversacion conOtro = directa(Formacion.ESCUELA);
        Conversacion conAdmin = directa(Formacion.ESCUELA);
        UserId otro = UserId.of(UUID.randomUUID());
        when(usuariosDeConversacion.otroParticipanteDeDirectas(any(), any())).thenReturn(
                Map.of(conOtro.id(), otro, conAdmin.id(), admin));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(
                otro, new UserSummary(otro, "Otro", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                admin, new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(formaciones.dentroDelAlcance(any(), any())).thenReturn(Set.of()); // el otro ya no pertenece a la Escuela

        List<Conversacion> visibles = visibilidad.visiblesPara(List.of(conOtro, conAdmin), deLaEscuela, AlcanceDeFormacion.de(Formacion.ESCUELA));

        assertThat(visibles).containsExactly(conAdmin);
    }

    @Test
    @DisplayName("Una lista se filtra por el alcance pedido: Todas deja pasar las tres; una formación, solo la suya")
    void filtrarUnaLista() {
        Conversacion escuela = general(Formacion.ESCUELA);
        Conversacion diplomado = general(Formacion.DIPLOMADO);
        Conversacion certificacion = general(Formacion.CERTIFICACION);
        List<Conversacion> todas = List.of(escuela, diplomado, certificacion);

        assertThat(visibilidad.visiblesPara(todas, admin, AlcanceDeFormacion.todas())).containsExactly(escuela, diplomado, certificacion);
        assertThat(visibilidad.visiblesPara(todas, admin, visibilidad.alcanceDe(admin, Formacion.DIPLOMADO))).containsExactly(diplomado);
        assertThat(visibilidad.visiblesPara(todas, deLaEscuela, visibilidad.alcanceDe(deLaEscuela, Formacion.DIPLOMADO))).isEmpty();
        assertThat(visibilidad.visiblesPara(List.of(), admin, AlcanceDeFormacion.todas())).isEmpty();
    }

    @Test
    @DisplayName("Cada tipo de conversación pertenece a un tipo de comunidad")
    void tiposDeComunidad() {
        assertThat(VisibilidadPorFormacion.tipoDeComunidadDe(com.renaser.os.chat.domain.model.conversacion.TipoConversacion.GLOBAL))
                .isEqualTo(TipoDeComunidad.GENERAL);
        assertThat(VisibilidadPorFormacion.tipoDeComunidadDe(com.renaser.os.chat.domain.model.conversacion.TipoConversacion.CELULA))
                .isEqualTo(TipoDeComunidad.GRUPO);
        assertThat(VisibilidadPorFormacion.tipoDeComunidadDe(com.renaser.os.chat.domain.model.conversacion.TipoConversacion.SOPORTE))
                .isEqualTo(TipoDeComunidad.SOPORTE);
        assertThat(VisibilidadPorFormacion.tipoDeComunidadDe(com.renaser.os.chat.domain.model.conversacion.TipoConversacion.DIRECTA))
                .isEqualTo(TipoDeComunidad.DIRECTO);
    }

    private static Conversacion general(Formacion formacion) {
        return Conversacion.crearGlobal(ConversacionId.of(UUID.randomUUID()), "General de " + formacion, AHORA, formacion);
    }

    private static Conversacion directa(Formacion formacion) {
        return Conversacion.crearDirecta(ConversacionId.of(UUID.randomUUID()), "a_" + UUID.randomUUID(), AHORA, formacion);
    }
}
