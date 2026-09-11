package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeTraslado.DestinoTraslado;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeTraslado.GrupoCandidato;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeTraslado.SituacionAprendiz;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quién entra a recepción, quién pasa al grupo estable y quién no se mueve. Es una decisión
 * pura: recibe el día del programa ya calculado en la zona del participante y devuelve qué
 * hacer, sin tocar base ni reloj.
 */
class PlanificadorDeTrasladoTest {

    private static final CohorteId COHORTE = CohorteId.of(UUID.randomUUID());
    private static final CelulaId RECEPCION = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
    private static final CelulaId GRUPO_A = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final CelulaId GRUPO_B = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private static final PoliticaMentoria POLITICA = PoliticaMentoria.porDefecto(COHORTE);

    private static SituacionAprendiz enNingunLado(int dia) {
        return new SituacionAprendiz(dia, true, null, null);
    }

    private static List<GrupoCandidato> grupos(int ocupacionA, int ocupacionB) {
        return List.of(new GrupoCandidato(GRUPO_A, ocupacionA, 10), new GrupoCandidato(GRUPO_B, ocupacionB, 10));
    }

    @Test
    @DisplayName("dia 0: cuenta aprobada sin programa activado, el reloj no arranca")
    void diaCeroNoEntra() {
        var decision = PlanificadorDeTraslado.decidir(
                new SituacionAprendiz(0, false, null, null), POLITICA, RECEPCION, grupos(0, 0));

        assertThat(decision.destino()).isEqualTo(DestinoTraslado.SIN_CAMBIO);
    }

    @Test
    @DisplayName("dias 1 a 7: recepcion (el default es dia_traslado = 8)")
    void diasUnoASieteVanARecepcion() {
        for (int dia = 1; dia <= 7; dia++) {
            var decision = PlanificadorDeTraslado.decidir(enNingunLado(dia), POLITICA, RECEPCION, grupos(0, 0));
            assertThat(decision.destino()).as("dia %d", dia).isEqualTo(DestinoTraslado.RECEPCION);
            assertThat(decision.grupoDestino()).isEqualTo(RECEPCION);
        }
    }

    @Test
    @DisplayName("dia 8: pasa al grupo estable")
    void diaOchoPasaAGrupoEstable() {
        var decision = PlanificadorDeTraslado.decidir(
                new SituacionAprendiz(8, true, RECEPCION, TipoCelula.RECEPCION), POLITICA, RECEPCION, grupos(0, 0));

        assertThat(decision.destino()).isEqualTo(DestinoTraslado.GRUPO_ESTABLE);
    }

    @Test
    @DisplayName("repetir la evaluacion cuando ya esta en recepcion no hace nada")
    void yaEnRecepcionNoSeMueve() {
        var decision = PlanificadorDeTraslado.decidir(
                new SituacionAprendiz(2, true, RECEPCION, TipoCelula.RECEPCION), POLITICA, RECEPCION, grupos(0, 0));

        assertThat(decision.destino()).isEqualTo(DestinoTraslado.SIN_CAMBIO);
    }

    @Test
    @DisplayName("ya en grupo estable: repetir tampoco lo mueve")
    void yaEnGrupoEstableNoSeMueve() {
        var decision = PlanificadorDeTraslado.decidir(
                new SituacionAprendiz(30, true, GRUPO_A, TipoCelula.REGULAR), POLITICA, RECEPCION, grupos(1, 0));

        assertThat(decision.destino()).isEqualTo(DestinoTraslado.SIN_CAMBIO);
    }

    @Test
    @DisplayName("corregir una fecha hacia atras NO devuelve a recepcion a quien ya tiene grupo")
    void correccionDeFechaNoRetrocede() {
        var decision = PlanificadorDeTraslado.decidir(
                new SituacionAprendiz(2, true, GRUPO_A, TipoCelula.REGULAR), POLITICA, RECEPCION, grupos(1, 0));

        assertThat(decision.destino()).isEqualTo(DestinoTraslado.SIN_CAMBIO);
    }

    @Test
    @DisplayName("elige el grupo con menos gente; ante empate, el de id menor: siempre el mismo")
    void eleccionDeterminista() {
        assertThat(PlanificadorDeTraslado.decidir(enNingunLado(8), POLITICA, RECEPCION, grupos(5, 2)).grupoDestino())
                .isEqualTo(GRUPO_B);
        assertThat(PlanificadorDeTraslado.decidir(enNingunLado(8), POLITICA, RECEPCION, grupos(2, 5)).grupoDestino())
                .isEqualTo(GRUPO_A);
        // Empate: gana el id menor, no "el primero que devolvio la base".
        assertThat(PlanificadorDeTraslado.decidir(enNingunLado(8), POLITICA, RECEPCION, grupos(3, 3)).grupoDestino())
                .isEqualTo(GRUPO_A);
    }

    @Test
    @DisplayName("un grupo lleno no recibe: se busca otro")
    void grupoLlenoNoRecibe() {
        var decision = PlanificadorDeTraslado.decidir(enNingunLado(8), POLITICA, RECEPCION, grupos(10, 7));

        assertThat(decision.grupoDestino()).isEqualTo(GRUPO_B);
    }

    @Test
    @DisplayName("todos llenos: ESPERANDO_GRUPO y sigue en recepcion, nunca sin chat (P-04)")
    void todosLlenosEsperaSinPerderChat() {
        var decision = PlanificadorDeTraslado.decidir(
                new SituacionAprendiz(8, true, RECEPCION, TipoCelula.RECEPCION), POLITICA, RECEPCION, grupos(10, 10));

        assertThat(decision.destino()).isEqualTo(DestinoTraslado.ESPERANDO_GRUPO);
        assertThat(decision.grupoDestino()).isNull();
        assertThat(decision.conservaAccesoActual()).isTrue();
    }

    @Test
    @DisplayName("sobreocupado por bajar la capacidad: tampoco recibe, pero nadie sale (RF-28)")
    void grupoSobreocupadoNoRecibe() {
        var decision = PlanificadorDeTraslado.decidir(enNingunLado(8), POLITICA, RECEPCION,
                List.of(new GrupoCandidato(GRUPO_A, 12, 10), new GrupoCandidato(GRUPO_B, 9, 10)));

        assertThat(decision.grupoDestino()).isEqualTo(GRUPO_B);
    }

    @Test
    @DisplayName("sin celula de recepcion configurada, el dia 2 no puede entrar a ningun lado")
    void sinRecepcionConfigurada() {
        var decision = PlanificadorDeTraslado.decidir(enNingunLado(2), POLITICA, null, grupos(0, 0));

        assertThat(decision.destino()).isEqualTo(DestinoTraslado.SIN_RECEPCION_CONFIGURADA);
    }

    @Test
    @DisplayName("el dia de traslado es configurable: con 7, el dia 4 sigue en recepcion")
    void diaDeTrasladoConfigurable() {
        PoliticaMentoria aLosSiete = PoliticaMentoria.rehydrate(COHORTE, 10, CadenciaRotacion.MENSUAL,
                "America/Lima", 7, 3, RECEPCION, 1);

        assertThat(PlanificadorDeTraslado.decidir(enNingunLado(4), aLosSiete, RECEPCION, grupos(0, 0)).destino())
                .isEqualTo(DestinoTraslado.RECEPCION);
        assertThat(PlanificadorDeTraslado.decidir(enNingunLado(7), aLosSiete, RECEPCION, grupos(0, 0)).destino())
                .isEqualTo(DestinoTraslado.GRUPO_ESTABLE);
    }
}
