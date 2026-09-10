package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeRotacion.CambioDeMentor;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeRotacion.PlanDeRotacion;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qué mentor va a qué grupo en la próxima rotación. Puro y determinista: dos ejecuciones con
 * la misma entrada dan el mismo plan, que es lo que permite repetir un job caído sin abrir
 * intervalos distintos.
 */
class PlanificadorDeRotacionTest {

    private static CelulaId grupo(int n) {
        return CelulaId.of(UUID.fromString("00000000-0000-0000-0000-00000000000" + n));
    }

    private static UserId mentor(int n) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a" + n));
    }

    private static final String CLAVE = "rotacion:cohorte-x:2026-10";

    private static Map<CelulaId, UserId> mentores(Object... pares) {
        Map<CelulaId, UserId> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((CelulaId) pares[i], (UserId) pares[i + 1]);
        }
        return mapa;
    }

    private static UserId entranteDe(PlanDeRotacion plan, CelulaId grupo) {
        return plan.cambios().stream().filter(c -> c.grupo().equals(grupo))
                .map(CambioDeMentor::mentorEntrante).findFirst().orElse(null);
    }

    @Test
    @DisplayName("dos grupos con dos mentores: intercambio A↔B, el caso que estresa el UNIQUE")
    void intercambioEntreDos() {
        PlanDeRotacion plan = PlanificadorDeRotacion.planificar(
                List.of(grupo(1), grupo(2)),
                mentores(grupo(1), mentor(1), grupo(2), mentor(2)),
                List.of(), CLAVE);

        assertThat(plan.cambios()).hasSize(2);
        assertThat(entranteDe(plan, grupo(1))).isEqualTo(mentor(2));
        assertThat(entranteDe(plan, grupo(2))).isEqualTo(mentor(1));
        assertThat(plan.gruposSinSustituto()).isEmpty();
    }

    @Test
    @DisplayName("tres grupos rotan en ciclo, cada uno cambia de mentor")
    void cicloDeTres() {
        PlanDeRotacion plan = PlanificadorDeRotacion.planificar(
                List.of(grupo(1), grupo(2), grupo(3)),
                mentores(grupo(1), mentor(1), grupo(2), mentor(2), grupo(3), mentor(3)),
                List.of(), CLAVE);

        assertThat(entranteDe(plan, grupo(1))).isEqualTo(mentor(2));
        assertThat(entranteDe(plan, grupo(2))).isEqualTo(mentor(3));
        assertThat(entranteDe(plan, grupo(3))).isEqualTo(mentor(1));
        // Nadie se queda con el mismo: eso es lo que hace que rotar signifique algo.
        assertThat(plan.cambios()).allSatisfy(c ->
                assertThat(c.mentorEntrante()).isNotEqualTo(c.mentorSaliente()));
    }

    @Test
    @DisplayName("un mentor en banca entra y el saliente descansa")
    void bancaEntraEnLaRotacion() {
        PlanDeRotacion plan = PlanificadorDeRotacion.planificar(
                List.of(grupo(1), grupo(2)),
                mentores(grupo(1), mentor(1), grupo(2), mentor(2)),
                List.of(mentor(3)), CLAVE);

        assertThat(entranteDe(plan, grupo(1))).isEqualTo(mentor(2));
        assertThat(entranteDe(plan, grupo(2))).isEqualTo(mentor(3));
        assertThat(plan.mentoresQueDescansan()).containsExactly(mentor(1));
    }

    @Test
    @DisplayName("un solo grupo y un solo mentor: no hay a quien rotar, se conserva y queda pendiente (P-03)")
    void sinSustitutoConservaAlActual() {
        PlanDeRotacion plan = PlanificadorDeRotacion.planificar(
                List.of(grupo(1)), mentores(grupo(1), mentor(1)), List.of(), CLAVE);

        assertThat(plan.cambios()).isEmpty();
        assertThat(plan.gruposSinSustituto()).containsExactly(grupo(1));
    }

    @Test
    @DisplayName("menos mentores que grupos: no se rota, se cubre — mover el hueco no ayuda a nadie")
    void menosMentoresQueGruposSoloCubre() {
        PlanDeRotacion plan = PlanificadorDeRotacion.planificar(
                List.of(grupo(1), grupo(2), grupo(3)),
                mentores(grupo(1), mentor(1), grupo(2), mentor(2)),
                List.of(), CLAVE);

        assertThat(plan.cambios()).isEmpty();
        assertThat(plan.gruposSinCobertura()).containsExactly(grupo(3));
        assertThat(plan.modo()).isEqualTo(PlanificadorDeRotacion.ModoRotacion.SOLO_COBERTURA);
    }

    @Test
    @DisplayName("un grupo sin mentor se cubre desde la banca antes de rotar")
    void grupoDescubiertoSeCubreDesdeLaBanca() {
        PlanDeRotacion plan = PlanificadorDeRotacion.planificar(
                List.of(grupo(1), grupo(2)),
                mentores(grupo(2), mentor(2)),
                List.of(mentor(4)), CLAVE);

        // Los dos quedan con mentor y ninguno se queda con el que tenia.
        assertThat(entranteDe(plan, grupo(1))).isNotNull();
        assertThat(entranteDe(plan, grupo(2))).isNotNull();
        assertThat(entranteDe(plan, grupo(2))).isNotEqualTo(mentor(2));
        assertThat(plan.gruposSinCobertura()).isEmpty();
    }

    @Test
    @DisplayName("sin ningun mentor: no se finge uno, los grupos quedan marcados sin cobertura")
    void sinMentoresNoSeFingeNinguno() {
        PlanDeRotacion plan = PlanificadorDeRotacion.planificar(
                List.of(grupo(1), grupo(2)), Map.of(), List.of(), CLAVE);

        assertThat(plan.cambios()).isEmpty();
        assertThat(plan.gruposSinCobertura()).containsExactly(grupo(1), grupo(2));
    }

    @Test
    @DisplayName("el plan es determinista: el orden en que llegan los grupos no cambia el resultado")
    void planDeterministaAunqueCambieElOrdenDeEntrada() {
        PlanDeRotacion uno = PlanificadorDeRotacion.planificar(
                List.of(grupo(3), grupo(1), grupo(2)),
                mentores(grupo(1), mentor(1), grupo(2), mentor(2), grupo(3), mentor(3)),
                List.of(), CLAVE);
        PlanDeRotacion otro = PlanificadorDeRotacion.planificar(
                List.of(grupo(1), grupo(2), grupo(3)),
                mentores(grupo(3), mentor(3), grupo(2), mentor(2), grupo(1), mentor(1)),
                List.of(), CLAVE);

        assertThat(entranteDe(uno, grupo(1))).isEqualTo(entranteDe(otro, grupo(1)));
        assertThat(entranteDe(uno, grupo(2))).isEqualTo(entranteDe(otro, grupo(2)));
        assertThat(entranteDe(uno, grupo(3))).isEqualTo(entranteDe(otro, grupo(3)));
    }

    @Test
    @DisplayName("cada cambio lleva su propia clave de operacion, derivada de la del periodo")
    void clavesDeOperacionPorCambio() {
        PlanDeRotacion plan = PlanificadorDeRotacion.planificar(
                List.of(grupo(1), grupo(2)),
                mentores(grupo(1), mentor(1), grupo(2), mentor(2)),
                List.of(), CLAVE);

        assertThat(plan.cambios()).allSatisfy(c -> assertThat(c.claveOperacion()).startsWith(CLAVE));
        assertThat(plan.cambios()).extracting(CambioDeMentor::claveOperacion).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("sin grupos no hay nada que planificar")
    void sinGrupos() {
        PlanDeRotacion plan = PlanificadorDeRotacion.planificar(List.of(), Map.of(), List.of(mentor(1)), CLAVE);

        assertThat(plan.cambios()).isEmpty();
        assertThat(plan.gruposSinCobertura()).isEmpty();
    }
}
