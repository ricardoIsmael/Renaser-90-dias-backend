package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerParticipantesDeConversacionUseCase.PaginaDeParticipantes;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.MentorDeLosGruposPort;
import com.renaser.os.chat.application.ports.out.conversacion.MentorDeLosGruposPort.GrupoConSuMentor;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.RolEnElChat;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Los integrantes de cada tipo de chat y quién puede pedirlos. */
@ExtendWith(MockitoExtension.class)
class ParticipantesDeConversacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-29T15:00:00Z");
    private static final UUID CELULA = UUID.randomUUID();
    private static final ConversacionId GRUPO = ConversacionId.of(UUID.randomUUID());
    private static final ConversacionId GLOBAL = ConversacionId.of(UUID.randomUUID());
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.randomUUID());
    private static final ConversacionId DIRECTA = ConversacionId.of(UUID.randomUUID());

    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private AutorizarAccesoAConversacionUseCase autorizarAcceso;
    @Mock
    private ListarUsuariosDeConversacionPort listarUsuariosPort;
    @Mock
    private PertenenciaVigentePort pertenenciaVigentePort;
    @Mock
    private MentorDeLosGruposPort mentorDeLosGrupos;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private VerFotosDelChatUseCase fotos;

    private ParticipantesDeConversacionService servicio;
    private final Map<UserId, UserSummary> cuentas = new HashMap<>();

    private UserId ana;
    private UserId beto;
    private UserId ricardo;
    private UserId kelin;
    private UserId alquimista;

    @BeforeEach
    void preparar() {
        servicio = new ParticipantesDeConversacionService(loadConversacionPort, autorizarAcceso, listarUsuariosPort,
                pertenenciaVigentePort, mentorDeLosGrupos, userSummaryFinder, fotos);
        ana = cuenta("Ana Pérez", UserRole.TRAINEE, UserStatus.ACTIVE);
        beto = cuenta("Beto Díaz", UserRole.TRAINEE, UserStatus.ACTIVE);
        ricardo = cuenta("Ricardo Palomino", UserRole.MENTOR, UserStatus.ACTIVE);
        kelin = cuenta("Kelin Rojas", UserRole.ADMIN, UserStatus.ACTIVE);
        alquimista = cuenta("Zoe Alquimia", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        conversacion(Conversacion.crearCelula(GRUPO, CELULA, AHORA));
        conversacion(Conversacion.crearGlobal(GLOBAL, AHORA));
        conversacion(Conversacion.crearSoporte(SOPORTE, ana, "Ana – Formación Renaser", AHORA));
        conversacion(Conversacion.crearDirecta(DIRECTA, Conversacion.claveDirectaDe(ana, beto), AHORA));
        lenient().when(autorizarAcceso.puedeVer(any(), any())).thenReturn(true);
        lenient().when(fotos.conTarjetaEn(any(), any())).thenReturn(Set.of());
        lenient().when(userSummaryFinder.findByIds(any())).thenAnswer(inv -> {
            Map<UserId, UserSummary> resueltas = new HashMap<>();
            for (UserId id : (Collection<UserId>) inv.getArgument(0)) {
                if (cuentas.containsKey(id)) {
                    resueltas.put(id, cuentas.get(id));
                }
            }
            return resueltas;
        });
        lenient().when(mentorDeLosGrupos.deLosGrupos(any())).thenReturn(Map.of(CELULA, new GrupoConSuMentor("Fénix", ricardo)));
    }

    @Test
    @DisplayName("grupo: el mentor primero, luego el staff y luego los aprendices por nombre, con su rol; solo quien pertenece hoy")
    void unGrupo() {
        UserId exMentor = cuenta("Ex Mentor", UserRole.MENTOR, UserStatus.ACTIVE);
        when(pertenenciaVigentePort.integrantesDelGrupo(CELULA)).thenReturn(List.of(beto, ana, kelin, ricardo));

        PaginaDeParticipantes pagina = servicio.ver(ana, GRUPO, null, 0, 50);

        assertThat(pagina.participantes()).extracting(p -> p.userId(), p -> p.rol(), p -> p.esUnoMismo())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ricardo, RolEnElChat.MENTOR, false),
                        org.assertj.core.groups.Tuple.tuple(kelin, RolEnElChat.ADMIN, false),
                        org.assertj.core.groups.Tuple.tuple(ana, RolEnElChat.APRENDIZ, true),
                        org.assertj.core.groups.Tuple.tuple(beto, RolEnElChat.APRENDIZ, false));
        assertThat(pagina.total()).isEqualTo(4);
        assertThat(pagina.participantes()).extracting(p -> p.userId()).doesNotContain(exMentor);
        verifyNoInteractions(listarUsuariosPort);
    }

    @Test
    @DisplayName("grupo: el ADMIN/ALCHEMIST que pertenece al grupo también lo ve, y una cuenta suspendida no figura")
    void unGrupoVistoPorElStaff() {
        UserId suspendida = cuenta("Sara Suspendida", UserRole.TRAINEE, UserStatus.SUSPENDED);
        when(pertenenciaVigentePort.integrantesDelGrupo(CELULA)).thenReturn(List.of(ana, ricardo, alquimista, suspendida));

        PaginaDeParticipantes pagina = servicio.ver(alquimista, GRUPO, null, 0, 50);

        assertThat(pagina.participantes()).extracting(p -> p.userId()).containsExactly(ricardo, alquimista, ana);
        assertThat(pagina.participantes().get(1).rol()).isEqualTo(RolEnElChat.ALQUIMISTA);
        assertThat(pagina.participantes().get(1).esUnoMismo()).isTrue();
    }

    @Test
    @DisplayName("soporte: el aprendiz primero y luego el staff de AHORA; una fila vieja de quien ya no es staff no aparece")
    void unSoporte() {
        UserId degradado = cuenta("Ex Admin", UserRole.MENTOR, UserStatus.ACTIVE);
        when(listarUsuariosPort.usuariosDe(SOPORTE)).thenReturn(List.of(alquimista, kelin, degradado, ana));

        PaginaDeParticipantes pagina = servicio.ver(kelin, SOPORTE, null, 0, 50);

        assertThat(pagina.participantes()).extracting(p -> p.userId()).containsExactly(ana, kelin, alquimista);
        assertThat(pagina.participantes()).extracting(p -> p.rol())
                .containsExactly(RolEnElChat.APRENDIZ, RolEnElChat.ADMIN, RolEnElChat.ALQUIMISTA);
    }

    @Test
    @DisplayName("soporte: el aprendiz figura aunque su fila de participante falte")
    void elAprendizSiempreEsta() {
        when(listarUsuariosPort.usuariosDe(SOPORTE)).thenReturn(List.of(kelin));

        assertThat(servicio.ver(kelin, SOPORTE, null, 0, 50).participantes())
                .extracting(p -> p.userId()).containsExactly(ana, kelin);
    }

    @Test
    @DisplayName("comunidad: por nombre, la búsqueda ignora mayúsculas y tildes, y el total cuenta la búsqueda entera")
    void laComunidadYSuBusqueda() {
        when(listarUsuariosPort.usuariosDe(GLOBAL)).thenReturn(List.of(kelin, ricardo, beto, ana));

        assertThat(servicio.ver(ana, GLOBAL, null, 0, 50).participantes()).extracting(p -> p.nombre())
                .containsExactly("Ana Pérez", "Beto Díaz", "Kelin Rojas", "Ricardo Palomino");
        PaginaDeParticipantes buscada = servicio.ver(ana, GLOBAL, "  PEREZ ", 0, 50);
        assertThat(buscada.participantes()).extracting(p -> p.userId()).containsExactly(ana);
        assertThat(buscada.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("comunidad: pagina, y el tamaño se acota a [1, 200] y la página a 0 como mínimo")
    void laPaginacion() {
        List<UserId> muchos = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            muchos.add(cuenta(String.format("Persona %03d", i), UserRole.TRAINEE, UserStatus.ACTIVE));
        }
        when(listarUsuariosPort.usuariosDe(GLOBAL)).thenReturn(muchos);

        PaginaDeParticipantes primera = servicio.ver(ana, GLOBAL, null, 0, 10_000);
        PaginaDeParticipantes segunda = servicio.ver(ana, GLOBAL, null, 1, 200);
        PaginaDeParticipantes negativa = servicio.ver(ana, GLOBAL, null, -3, 0);

        assertThat(primera.participantes()).hasSize(200);
        assertThat(primera.tamano()).isEqualTo(200);
        assertThat(primera.total()).isEqualTo(250);
        assertThat(segunda.participantes()).hasSize(50);
        assertThat(segunda.participantes().get(0).nombre()).isEqualTo("Persona 200");
        assertThat(negativa.pagina()).isZero();
        assertThat(negativa.participantes()).hasSize(1);
    }

    @Test
    @DisplayName("1 a 1: las dos personas")
    void unoAUno() {
        when(listarUsuariosPort.usuariosDe(DIRECTA)).thenReturn(List.of(beto, ana));

        assertThat(servicio.ver(ana, DIRECTA, null, 0, 50).participantes()).extracting(p -> p.userId())
                .containsExactly(ana, beto);
    }

    @Test
    @DisplayName("la tarjeta: fotoPath solo para quien la lleva según el modo; el resto trae la foto que subió")
    void lasTarjetas() {
        cuentas.put(beto, new UserSummary(beto, "Beto Díaz", "https://foto/beto.jpg", UserRole.TRAINEE, UserStatus.ACTIVE));
        when(pertenenciaVigentePort.integrantesDelGrupo(CELULA)).thenReturn(List.of(ana, beto));
        when(fotos.conTarjetaEn(TipoConversacion.CELULA, List.of(ana, beto))).thenReturn(Set.of(ana));

        var filas = servicio.ver(ana, GRUPO, null, 0, 50).participantes();

        assertThat(filas.get(0).llevaTarjeta()).isTrue();
        assertThat(filas.get(1).llevaTarjeta()).isFalse();
        assertThat(filas.get(1).avatarUrl()).isEqualTo("https://foto/beto.jpg");
    }

    @Test
    @DisplayName("quien no puede ver la conversación: 403 y no se consulta a nadie; cuenta suspendida: 403; no existe: 404")
    void losRechazos() {
        when(autorizarAcceso.puedeVer(SOPORTE, beto)).thenReturn(false);
        UserId suspendido = cuenta("Sam", UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.ver(beto, SOPORTE, null, 0, 50)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.ver(suspendido, GLOBAL, null, 0, 50)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.ver(ana, ConversacionId.of(UUID.randomUUID()), null, 0, 50))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(listarUsuariosPort, pertenenciaVigentePort, fotos);
        verify(autorizarAcceso).puedeVer(SOPORTE, beto);
    }

    private UserId cuenta(String nombre, UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        cuentas.put(id, new UserSummary(id, nombre, null, rol, estado));
        lenient().when(userSummaryFinder.findById(id)).thenAnswer(inv -> Optional.ofNullable(cuentas.get(id)));
        return id;
    }

    private void conversacion(Conversacion conversacion) {
        lenient().when(loadConversacionPort.porId(conversacion.id())).thenReturn(Optional.of(conversacion));
    }
}
