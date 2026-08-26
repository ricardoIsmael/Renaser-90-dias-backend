package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.out.espejosombra.LeerEntradasDiarioPort;
import com.renaser.os.rag.application.ports.out.espejosombra.LeerEntradasDiarioPort.EntradaDiario;
import com.renaser.os.rag.application.ports.out.espejosombra.LoadInformeEspejoSombraPort;
import com.renaser.os.rag.application.ports.out.espejosombra.SaveInformeEspejoSombraPort;
import com.renaser.os.rag.application.ports.out.ia.GenerarInsightSemanalPort;
import com.renaser.os.rag.application.ports.out.ia.GenerarInsightSemanalPort.InsightSemanal;
import com.renaser.os.rag.domain.model.espejosombra.DistribucionTemporal;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombraId;
import com.renaser.os.rag.domain.model.espejosombra.PreguntaConfrontacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EspejoSombraServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final LocalDate SEMANA_INICIO = LocalDate.of(2026, 8, 17);

    @Mock
    private LoadInformeEspejoSombraPort loadInformePort;
    @Mock
    private SaveInformeEspejoSombraPort saveInformePort;
    @Mock
    private LeerEntradasDiarioPort leerEntradasPort;
    @Mock
    private GenerarInsightSemanalPort generarInsightPort;

    private FakeUserSummaryFinder actores;
    private FakeParticipacionProgramaFinder participaciones;
    private EspejoSombraService service;

    private UserId trainee;
    private UserId mentorAsignado;
    private UserId otroMentor;
    private UserId admin;

    @BeforeEach
    void setUp() {
        actores = new FakeUserSummaryFinder();
        participaciones = new FakeParticipacionProgramaFinder();
        service = new EspejoSombraService(loadInformePort, saveInformePort, leerEntradasPort, generarInsightPort,
                actores, participaciones, CLOCK);

        trainee = UserId.of(UUID.randomUUID());
        mentorAsignado = UserId.of(UUID.randomUUID());
        otroMentor = UserId.of(UUID.randomUUID());
        admin = UserId.of(UUID.randomUUID());
        actores.conActor(trainee, UserRole.TRAINEE).conActor(mentorAsignado, UserRole.MENTOR)
                .conActor(otroMentor, UserRole.MENTOR).conActor(admin, UserRole.ADMIN);
        participaciones.conMentorAsignado(trainee, mentorAsignado);
    }

    // ---- generar(): idempotencia, semana vacia, IA no disponible ----

    @Test
    void siYaExisteInformeParaLaSemanaNoHaceNadaMasNiConsultaEntradas() {
        when(loadInformePort.porParticipanteYSemana(trainee, SEMANA_INICIO))
                .thenReturn(Optional.of(informeDePrueba()));

        service.generar(trainee, SEMANA_INICIO);

        verify(leerEntradasPort, never()).deLaSemana(any(), any(), any());
        verify(generarInsightPort, never()).analizar(anyList());
        verify(saveInformePort, never()).save(any());
    }

    @Test
    void semanaSinEntradasDeContenidoNoGeneraInforme() {
        when(loadInformePort.porParticipanteYSemana(trainee, SEMANA_INICIO)).thenReturn(Optional.empty());
        when(leerEntradasPort.deLaSemana(trainee, SEMANA_INICIO, SEMANA_INICIO.plusDays(6)))
                .thenReturn(List.of());

        service.generar(trainee, SEMANA_INICIO);

        verify(generarInsightPort, never()).analizar(anyList());
        verify(saveInformePort, never()).save(any());
    }

    @Test
    void iaNoDisponibleNoPersisteNadaAunqueHayaEntradas() {
        when(loadInformePort.porParticipanteYSemana(trainee, SEMANA_INICIO)).thenReturn(Optional.empty());
        when(leerEntradasPort.deLaSemana(trainee, SEMANA_INICIO, SEMANA_INICIO.plusDays(6)))
                .thenReturn(List.of(new EntradaDiario(SEMANA_INICIO, "hoy fue un dia dificil")));
        when(generarInsightPort.analizar(anyList())).thenReturn(Optional.empty());

        service.generar(trainee, SEMANA_INICIO);

        verify(saveInformePort, never()).save(any());
    }

    @Test
    void conEntradasEInsightDisponibleGeneraYPersisteElInforme() {
        when(loadInformePort.porParticipanteYSemana(trainee, SEMANA_INICIO)).thenReturn(Optional.empty());
        List<EntradaDiario> entradas = List.of(new EntradaDiario(SEMANA_INICIO, "entrada uno"),
                new EntradaDiario(SEMANA_INICIO.plusDays(2), "entrada dos"));
        when(leerEntradasPort.deLaSemana(trainee, SEMANA_INICIO, SEMANA_INICIO.plusDays(6))).thenReturn(entradas);
        InsightSemanal insight = new InsightSemanal("Evitacion", 30, 50, 20, "insight de la semana",
                List.of("Que evitaste?", "Que repetiste?"));
        when(generarInsightPort.analizar(anyList())).thenReturn(Optional.of(insight));
        when(saveInformePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generar(trainee, SEMANA_INICIO);

        verify(saveInformePort).save(argThatInforme(informe -> {
            assertThat(informe.participanteId()).isEqualTo(trainee);
            assertThat(informe.cantidadEntradas()).isEqualTo(2);
            assertThat(informe.patronDominante()).isEqualTo("Evitacion");
            assertThat(informe.preguntas()).hasSize(2);
        }));
    }

    private static InformeEspejoSombra argThatInforme(Consumer<InformeEspejoSombra> asserts) {
        return org.mockito.ArgumentMatchers.argThat(informe -> {
            asserts.accept(informe);
            return true;
        });
    }

    // ---- seguridad D-47: quien puede ver un informe ----

    @Test
    void elPropioAprendizVeSuInforme() {
        InformeEspejoSombra informe = informeDePrueba();
        when(loadInformePort.byId(informe.id())).thenReturn(Optional.of(informe));

        assertThat(service.porId(trainee, informe.id())).isEqualTo(informe);
    }

    @Test
    void elMentorAsignadoVeElInforme() {
        InformeEspejoSombra informe = informeDePrueba();
        when(loadInformePort.byId(informe.id())).thenReturn(Optional.of(informe));

        assertThat(service.porId(mentorAsignado, informe.id())).isEqualTo(informe);
    }

    @Test
    void unMentorQueNoEsElAsignadoNoVeElInforme() {
        InformeEspejoSombra informe = informeDePrueba();
        when(loadInformePort.byId(informe.id())).thenReturn(Optional.of(informe));

        assertThatThrownBy(() -> service.porId(otroMentor, informe.id()))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void unTraineeAjenoNoVeElInformeDeOtro() {
        InformeEspejoSombra informe = informeDePrueba();
        when(loadInformePort.byId(informe.id())).thenReturn(Optional.of(informe));
        UserId otroTrainee = UserId.of(UUID.randomUUID());
        actores.conActor(otroTrainee, UserRole.TRAINEE);

        assertThatThrownBy(() -> service.porId(otroTrainee, informe.id()))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void unAdminVeCualquierInforme() {
        InformeEspejoSombra informe = informeDePrueba();
        when(loadInformePort.byId(informe.id())).thenReturn(Optional.of(informe));

        assertThat(service.porId(admin, informe.id())).isEqualTo(informe);
    }

    @Test
    void unActorSuspendidoNoVeNingunInforme() {
        InformeEspejoSombra informe = informeDePrueba();
        when(loadInformePort.byId(informe.id())).thenReturn(Optional.of(informe));
        UserId suspendido = UserId.of(UUID.randomUUID());
        actores.conActor(suspendido, UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> service.porId(suspendido, informe.id()))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void unInformeInexistenteLanzaNoSuchElement() {
        InformeEspejoSombraId id = InformeEspejoSombraId.newId();
        when(loadInformePort.byId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.porId(admin, id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deParticipanteAplicaLaMismaVisibilidadQuePorId() {
        when(loadInformePort.deParticipante(trainee)).thenReturn(List.of(informeDePrueba()));

        assertThat(service.deParticipante(mentorAsignado, trainee)).hasSize(1);
        assertThatThrownBy(() -> service.deParticipante(otroMentor, trainee))
                .isInstanceOf(NotAuthorizedException.class);
    }

    private InformeEspejoSombra informeDePrueba() {
        return InformeEspejoSombra.generar(trainee, SEMANA_INICIO, 3, "Evitacion",
                new DistribucionTemporal(30, 50, 20), "insight de prueba",
                List.of(new PreguntaConfrontacion(1, "pregunta uno")), CLOCK);
    }
}
