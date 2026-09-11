package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.SincronizarParticipantesCelulaUseCase.ResultadoSincronizacion;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.application.ports.out.participante.QuitarParticipantePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La reconciliación del chat de un grupo.
 *
 * <p>Lo que se prueba acá es que reconcilie contra la lista COMPLETA y no aplique diferencias:
 * el outbox entrega at-least-once y sin orden garantizado, así que un evento viejo reentregado
 * después de una rotación no puede devolverle el acceso al mentor saliente.
 */
class ParticipantesCelulaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID CELULA = UUID.randomUUID();
    private static final ConversacionId CONVERSACION = ConversacionId.of(UUID.randomUUID());

    private static final UserId MENTOR_VIEJO = UserId.of(UUID.randomUUID());
    private static final UserId MENTOR_NUEVO = UserId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId SOPORTE = UserId.of(UUID.randomUUID());

    /** Composición REAL del grupo, la que devuelve community. */
    private final List<UserId> integrantesReales = new ArrayList<>();
    /** Proyección del chat: puede estar vieja, y de eso se trata. */
    private final Set<UserId> enLaConversacion = new LinkedHashSet<>();
    private final List<String> operaciones = new ArrayList<>();

    private boolean hayConversacion = true;

    private ParticipantesCelulaService servicio;

    @BeforeEach
    void preparar() {
        integrantesReales.clear();
        enLaConversacion.clear();
        operaciones.clear();
        hayConversacion = true;

        LoadConversacionPort conversaciones = new LoadConversacionPort() {
            @Override
            public Optional<Conversacion> porId(ConversacionId id) {
                return Optional.empty();
            }

            @Override
            public Optional<Conversacion> porCelulaId(UUID celulaId) {
                return hayConversacion
                        ? Optional.of(Conversacion.crearCelula(CONVERSACION, celulaId, AHORA))
                        : Optional.empty();
            }

            @Override
            public Optional<Conversacion> global() {
                return Optional.empty();
            }

            @Override
            public Optional<Conversacion> porClaveDirecta(String claveDirecta) {
                return Optional.empty();
            }

            @Override
            public List<Conversacion> misConversaciones(UserId usuarioId) {
                return List.of();
            }
        };

        PertenenciaVigentePort pertenencia = new PertenenciaVigentePort() {
            @Override
            public boolean perteneceAlGrupo(UUID celulaId, UserId usuarioId) {
                return integrantesReales.contains(usuarioId);
            }

            @Override
            public List<UserId> integrantesDelGrupo(UUID celulaId) {
                return List.copyOf(integrantesReales);
            }
        };

        /* Clase anonima y ya no un lambda: el puerto crecio un segundo metodo
           (`otroParticipanteDeDirectas`) y dejo de ser funcional. Esta prueba no usa ese metodo,
           asi que devuelve vacio en vez de lanzar: lo que mide es la sincronizacion de
           participantes al rotar, y un `UnsupportedOperationException` acá solo dejaria una mina
           para el dia que alguien amplie el escenario. */
        ListarUsuariosDeConversacionPort listar = new ListarUsuariosDeConversacionPort() {
            @Override
            public List<UserId> usuariosDe(ConversacionId conversacionId) {
                return List.copyOf(enLaConversacion);
            }

            @Override
            public Map<ConversacionId, UserId> otroParticipanteDeDirectas(List<ConversacionId> ids, UserId actorId) {
                return Map.of();
            }
        };

        AgregarParticipantePort agregar = participante -> {
            enLaConversacion.add(participante.usuarioId());
            operaciones.add("agregar:" + participante.usuarioId());
        };

        QuitarParticipantePort quitar = (conversacionId, usuarioId) -> {
            enLaConversacion.remove(usuarioId);
            operaciones.add("quitar:" + usuarioId);
        };

        servicio = new ParticipantesCelulaService(conversaciones, listar, pertenencia, agregar, quitar,
                FixedClock.at(AHORA));
    }

    @Test
    @DisplayName("tras la rotacion: entra el mentor nuevo y SALE el saliente")
    void rotacionReemplazaAlMentor() {
        integrantesReales.addAll(List.of(MENTOR_NUEVO, ANA));
        enLaConversacion.addAll(List.of(MENTOR_VIEJO, ANA));

        ResultadoSincronizacion resultado = servicio.sincronizar(CELULA);

        assertThat(resultado.agregados()).isEqualTo(1);
        assertThat(resultado.quitados()).isEqualTo(1);
        assertThat(enLaConversacion).containsExactlyInAnyOrder(MENTOR_NUEVO, ANA);
    }

    @Test
    @DisplayName("un evento VIEJO reentregado despues no reincorpora al exmentor")
    void eventoFueraDeOrdenNoRevive() {
        integrantesReales.addAll(List.of(MENTOR_NUEVO, ANA));
        enLaConversacion.addAll(List.of(MENTOR_VIEJO, ANA));

        servicio.sincronizar(CELULA);
        // Segunda entrega del MISMO evento, o de uno anterior: da igual, se vuelve a pedir la
        // lista real. Con un delta, el exmentor volveria a entrar.
        servicio.sincronizar(CELULA);

        assertThat(enLaConversacion).doesNotContain(MENTOR_VIEJO);
        assertThat(enLaConversacion).containsExactlyInAnyOrder(MENTOR_NUEVO, ANA);
    }

    @Test
    @DisplayName("correrla dos veces sin cambios no hace nada: es idempotente")
    void idempotente() {
        integrantesReales.addAll(List.of(MENTOR_NUEVO, ANA));
        enLaConversacion.addAll(List.of(MENTOR_NUEVO, ANA));

        ResultadoSincronizacion resultado = servicio.sincronizar(CELULA);

        assertThat(resultado.agregados()).isZero();
        assertThat(resultado.quitados()).isZero();
        assertThat(operaciones).isEmpty();
    }

    @Test
    @DisplayName("soporte permanece aunque el mentor rote (D-06)")
    void soportePermanece() {
        integrantesReales.addAll(List.of(MENTOR_NUEVO, ANA, SOPORTE));
        enLaConversacion.addAll(List.of(MENTOR_VIEJO, ANA, SOPORTE));

        servicio.sincronizar(CELULA);

        assertThat(enLaConversacion).contains(SOPORTE);
    }

    @Test
    @DisplayName("un aprendiz trasladado sale del chat del grupo anterior")
    void trasladoSacaDelGrupoAnterior() {
        integrantesReales.addAll(List.of(MENTOR_NUEVO));
        enLaConversacion.addAll(List.of(MENTOR_NUEVO, ANA));

        servicio.sincronizar(CELULA);

        assertThat(enLaConversacion).doesNotContain(ANA);
    }

    @Test
    @DisplayName("un grupo todavia sin chat no es un error")
    void grupoSinConversacion() {
        hayConversacion = false;
        integrantesReales.add(ANA);

        ResultadoSincronizacion resultado = servicio.sincronizar(CELULA);

        assertThat(resultado.sinConversacion()).isTrue();
        assertThat(operaciones).isEmpty();
    }

    @Test
    @DisplayName("la conversacion NO se recrea: se conserva su id y con el, sus mensajes")
    void conservaLaConversacion() {
        integrantesReales.addAll(List.of(MENTOR_NUEVO, ANA));
        enLaConversacion.addAll(List.of(MENTOR_VIEJO, ANA));

        servicio.sincronizar(CELULA);

        // Solo hay altas y bajas de participantes: nada que borre o cree la conversacion.
        assertThat(operaciones).allSatisfy(op ->
                assertThat(op).matches("^(agregar|quitar):.*"));
    }
}
