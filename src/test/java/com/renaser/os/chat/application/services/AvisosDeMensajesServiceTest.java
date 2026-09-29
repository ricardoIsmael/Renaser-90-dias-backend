package com.renaser.os.chat.application.services;

import com.renaser.os.chat.api.AvisosDeMensajesFinder.AvisoDeMensaje;
import com.renaser.os.chat.api.AvisosDeMensajesFinder.Contenido;
import com.renaser.os.chat.api.AvisosDeMensajesFinder.Destinatario;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.MentorDeLosGruposPort;
import com.renaser.os.chat.application.ports.out.conversacion.MentorDeLosGruposPort.GrupoConSuMentor;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.participante.ContarNoLeidosPort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.application.ports.out.presencia.ConversacionAbiertaPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** D-221: a quién se avisa de un mensaje y con qué nombre, sin base (la base la prueba {@code MensajeDeChatAvisoIT}). */
class AvisosDeMensajesServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-29T15:00:00Z");

    private final LoadMensajePort mensajes = mock(LoadMensajePort.class);
    private final LoadConversacionPort conversaciones = mock(LoadConversacionPort.class);
    private final ListarUsuariosDeConversacionPort participantes = mock(ListarUsuariosDeConversacionPort.class);
    private final PertenenciaVigentePort pertenencia = mock(PertenenciaVigentePort.class);
    private final ContarNoLeidosPort noLeidos = mock(ContarNoLeidosPort.class);
    private final Set<UserId> conElChatAbierto = new HashSet<>();
    private final Map<UserId, UserSummary> personas = new HashMap<>();
    private final Map<UUID, GrupoConSuMentor> grupos = new HashMap<>();
    private AvisosDeMensajesService service;

    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId beto = UserId.of(UUID.randomUUID());
    private final UserId luisa = UserId.of(UUID.randomUUID());
    private final UserId exMentor = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId exAdmin = UserId.of(UUID.randomUUID());

    @BeforeEach
    void preparar() {
        UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
        when(usuarios.findById(any())).thenAnswer(i -> Optional.ofNullable(personas.get(i.<UserId>getArgument(0))));
        when(usuarios.findByIds(anyCollection())).thenAnswer(i -> {
            Map<UserId, UserSummary> encontrados = new HashMap<>();
            for (UserId id : i.<Collection<UserId>>getArgument(0)) {
                if (personas.containsKey(id)) encontrados.put(id, personas.get(id));
            }
            return encontrados;
        });
        ConversacionAbiertaPort abiertas = mock(ConversacionAbiertaPort.class);
        when(abiertas.laTienenAbierta(any(), anyCollection())).thenAnswer(i -> {
            Set<UserId> resultado = new HashSet<>(i.<Collection<UserId>>getArgument(1));
            resultado.retainAll(conElChatAbierto);
            return resultado;
        });
        when(noLeidos.noLeidosPorParticipante(any(), anyCollection())).thenReturn(Map.of());
        MentorDeLosGruposPort mentores = ids -> grupos;
        service = new AvisosDeMensajesService(mensajes, conversaciones, participantes, pertenencia, noLeidos, abiertas,
                usuarios, new NombresDeLosChatsService(mentores, usuarios));
        persona(ana, "Ana Pérez", UserRole.TRAINEE);
        persona(beto, "Beto Ruiz", UserRole.TRAINEE);
        persona(luisa, "Luisa Fernanda Quispe", UserRole.MENTOR);
        persona(exMentor, "Pedro Ex", UserRole.MENTOR);
        persona(admin, "Kelin Admin", UserRole.ADMIN);
        persona(exAdmin, "Ex Admin", UserRole.MENTOR);
    }

    @Test
    @DisplayName("grupo: avisa a los integrantes de HOY menos el autor, con «Luisa y sus aprendices»; el ex mentor de la proyección no")
    void grupo() {
        UUID celula = UUID.randomUUID();
        Conversacion grupo = Conversacion.crearCelula(ConversacionId.of(UUID.randomUUID()), celula, AHORA);
        grupos.put(celula, new GrupoConSuMentor("Fenix", luisa));
        when(pertenencia.integrantesDelGrupo(celula)).thenReturn(List.of(ana, beto, luisa));
        Mensaje m = texto(grupo, ana, "hola");
        conParticipantes(grupo, ana, beto, luisa, exMentor);

        AvisoDeMensaje aviso = service.avisoDe(m.id().value()).orElseThrow();

        assertThat(destinatarios(aviso)).containsExactlyInAnyOrder(beto, luisa);
        assertThat(aviso.destinatarios()).allSatisfy(d -> assertThat(d.nombreDelChat()).isEqualTo("Luisa y sus aprendices"));
        assertThat(aviso.autor()).isEqualTo("Ana Pérez");
        assertThat(aviso.rutaApp()).isEqualTo("/chat/" + grupo.id().value());
        assertThat(aviso.unoAUno()).isFalse();
        assertThat(aviso.contenido()).isEqualTo(Contenido.TEXTO);
    }

    @Test
    @DisplayName("quien tiene el chat abierto no recibe aviso")
    void chatAbierto() {
        Conversacion global = Conversacion.crearGlobal(ConversacionId.of(UUID.randomUUID()), AHORA);
        Mensaje m = texto(global, ana, "hola");
        conParticipantes(global, ana, beto, luisa);
        conElChatAbierto.add(beto);

        assertThat(destinatarios(service.avisoDe(m.id().value()).orElseThrow())).containsExactly(luisa);
    }

    @Test
    @DisplayName("soporte: el aprendiz y el staff que lo es HOY; quien perdió el rol, no")
    void soporte() {
        Conversacion soporte = Conversacion.crearSoporte(ConversacionId.of(UUID.randomUUID()), beto, "Soporte - Beto Ruiz", AHORA);
        Mensaje m = texto(soporte, admin, "¿cómo vas?");
        conParticipantes(soporte, beto, admin, exAdmin);

        AvisoDeMensaje aviso = service.avisoDe(m.id().value()).orElseThrow();

        assertThat(destinatarios(aviso)).containsExactly(beto);
        assertThat(aviso.destinatarios().getFirst().nombreDelChat()).isEqualTo("Beto – Formación Renaser");
    }

    @Test
    @DisplayName("un mensaje del programa no tiene autor: avisa también a la persona de quien habla, firmado «Formación Renaser»")
    void delPrograma() {
        Conversacion soporte = Conversacion.crearSoporte(ConversacionId.of(UUID.randomUUID()), beto, "x", AHORA);
        Mensaje m = Mensaje.delPrograma(MensajeId.of(UUID.randomUUID()), soporte.id(), beto,
                ContenidoDelPrograma.imagen("chat/x/bienvenida.png", "image/png", 10), AHORA);
        guardado(soporte, m);
        conParticipantes(soporte, beto, admin);

        AvisoDeMensaje aviso = service.avisoDe(m.id().value()).orElseThrow();

        assertThat(destinatarios(aviso)).containsExactlyInAnyOrder(beto, admin);
        assertThat(aviso.autor()).isEqualTo("Formación Renaser");
        assertThat(aviso.contenido()).as("la tarjeta sola es una foto").isEqualTo(Contenido.FOTO);
    }

    @Test
    @DisplayName("1 a 1: el chat se llama como quien escribe; una nota de voz es NOTA_DE_VOZ")
    void unoAUno() {
        Conversacion directa = Conversacion.crearDirecta(ConversacionId.of(UUID.randomUUID()),
                Conversacion.claveDirectaDe(ana, luisa), AHORA);
        Mensaje m = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), directa.id(), luisa, TipoMensaje.AUDIO, null,
                "chat", "chat/" + directa.id().value() + "/a.m4a", "audio/mp4", 100, (short) 3, null, AHORA);
        guardado(directa, m);
        conParticipantes(directa, ana, luisa);
        when(noLeidos.noLeidosPorParticipante(any(), anyCollection())).thenReturn(Map.of(ana, 3L));

        AvisoDeMensaje aviso = service.avisoDe(m.id().value()).orElseThrow();

        assertThat(aviso.unoAUno()).isTrue();
        assertThat(aviso.destinatarios()).containsExactly(new Destinatario(ana, "Luisa Fernanda Quispe", 3));
        assertThat(aviso.contenido()).isEqualTo(Contenido.NOTA_DE_VOZ);
        assertThat(aviso.texto()).isNull();
    }

    @Test
    @DisplayName("un mensaje que ya no existe no avisa")
    void mensajeQueNoExiste() {
        assertThat(service.avisoDe(UUID.randomUUID())).isEmpty();
    }

    private Mensaje texto(Conversacion conversacion, UserId autor, String texto) {
        Mensaje m = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), conversacion.id(), autor, TipoMensaje.TEXTO, texto,
                null, null, null, null, null, null, AHORA);
        guardado(conversacion, m);
        return m;
    }

    private void guardado(Conversacion conversacion, Mensaje m) {
        when(mensajes.porId(m.id())).thenReturn(Optional.of(m));
        when(conversaciones.porId(conversacion.id())).thenReturn(Optional.of(conversacion));
    }

    private void conParticipantes(Conversacion conversacion, UserId... ids) {
        when(participantes.usuariosDe(conversacion.id())).thenReturn(List.of(ids));
    }

    private void persona(UserId id, String nombre, UserRole rol) {
        personas.put(id, new UserSummary(id, nombre, null, rol, UserStatus.ACTIVE));
    }

    private static List<UserId> destinatarios(AvisoDeMensaje aviso) {
        return aviso.destinatarios().stream().map(Destinatario::usuarioId).toList();
    }
}
