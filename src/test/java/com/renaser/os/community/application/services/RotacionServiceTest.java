package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.acompanamiento.RotarMentoresUseCase.ResultadoRotacion;
import com.renaser.os.community.domain.model.acompanamiento.CadenciaRotacion;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La rotación aplicada de verdad. El caso que manda es el intercambio A↔B: la base no admite
 * dos mentores vigentes en la misma célula ni el mismo mentor en dos, así que el servicio tiene
 * que cerrar y vaciar TODO antes de abrir nada. Acá el doble reproduce esa unicidad, de modo
 * que un orden equivocado falla en esta prueba y no recién contra Postgres.
 */
class RotacionServiceTest {

    /** 1 de octubre de 2026, 09:00 UTC = 04:00 en Lima: día de anclaje mensual. */
    private static final Instant PRIMERO_DE_OCTUBRE = Instant.parse("2026-10-01T09:00:00Z");

    private static final CohorteId COHORTE = CohorteId.of(UUID.randomUUID());
    private static final CelulaId GRUPO_A = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final CelulaId GRUPO_B = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final UserId MENTOR_A = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId MENTOR_B = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));

    private AcompanamientoEnMemoria banco;

    @BeforeEach
    void preparar() {
        banco = new AcompanamientoEnMemoria();
    }

    private RotacionService servicio(Clock clock) {
        return new RotacionService(banco.cargaCelulas, banco.guardaCelula, banco.cargaCohortes,
                banco.cargaAsignaciones, banco.guardaAsignacion, banco.cargaPolitica, banco.existePerfil,
                banco.buscaParticipacion, banco.punteroDeUsers, banco.publicador, clock, banco.idGenerator);
    }

    /** Dos grupos con su mentor y un aprendiz en A. */
    private void dosGruposConMentor(Instant ahora) {
        banco.cohorte(COHORTE, PoliticaMentoria.rehydrate(COHORTE, 10, CadenciaRotacion.MENSUAL, "America/Lima",
                4, 3, null, 1));
        banco.grupo(GRUPO_A, COHORTE, TipoCelula.REGULAR, MENTOR_A, ahora);
        banco.grupo(GRUPO_B, COHORTE, TipoCelula.REGULAR, MENTOR_B, ahora);
        banco.mentorConPerfil(MENTOR_A);
        banco.mentorConPerfil(MENTOR_B);
        banco.asignar(GRUPO_A, MENTOR_A, FuncionAcompanamiento.MENTOR, ahora.minusSeconds(2_592_000), null);
        banco.asignar(GRUPO_B, MENTOR_B, FuncionAcompanamiento.MENTOR, ahora.minusSeconds(2_592_000), null);
        banco.asignar(GRUPO_A, ANA, FuncionAcompanamiento.APRENDIZ, ahora.minusSeconds(2_592_000), null);
        banco.aprendiz(ANA, 30, true, GRUPO_A.value());
    }

    @Test
    @DisplayName("intercambio A↔B: cierra y libera los dos punteros ANTES de abrir ninguno")
    void intercambioLiberaAntesDeAsignar() {
        dosGruposConMentor(PRIMERO_DE_OCTUBRE);

        ResultadoRotacion resultado = servicio(FixedClock.at(PRIMERO_DE_OCTUBRE)).rotar(COHORTE, "rot:octubre");

        assertThat(resultado.cambiosAplicados()).isEqualTo(2);
        // La ultima liberacion ocurre antes de la primera apertura: es lo que hace posible el swap.
        int ultimaLiberacion = ultimaOperacionQueEmpiezaCon("celula:", ":mentor=null");
        int primeraApertura = banco.primeraOperacion("abrir:MENTOR");
        assertThat(ultimaLiberacion).isLessThan(primeraApertura);
    }

    @Test
    @DisplayName("tras el intercambio cada grupo tiene al otro mentor, y el chat/grupo no cambia")
    void resultadoDelIntercambio() {
        dosGruposConMentor(PRIMERO_DE_OCTUBRE);

        servicio(FixedClock.at(PRIMERO_DE_OCTUBRE)).rotar(COHORTE, "rot:octubre");

        assertThat(banco.celulas.get(GRUPO_A.value()).mentorId()).isEqualTo(MENTOR_B);
        assertThat(banco.celulas.get(GRUPO_B.value()).mentorId()).isEqualTo(MENTOR_A);
        // Los grupos siguen siendo los mismos objetos: no se creo ni borro ninguno (D-06).
        assertThat(banco.celulas).hasSize(2);
    }

    @Test
    @DisplayName("el puntero mentor_id del aprendiz se mueve con la rotacion")
    void punteroDelAprendizSeSincroniza() {
        dosGruposConMentor(PRIMERO_DE_OCTUBRE);

        servicio(FixedClock.at(PRIMERO_DE_OCTUBRE)).rotar(COHORTE, "rot:octubre");

        // Sin esto el mentor saliente seguiria autorizado sobre la evidencia de Ana.
        assertThat(banco.punteros.get(ANA)).isEqualTo(GRUPO_A.value() + "/" + MENTOR_B.value());
    }

    @Test
    @DisplayName("los intervalos viejos quedan cerrados y los nuevos abiertos en la hora real")
    void intervalosCoherentes() {
        dosGruposConMentor(PRIMERO_DE_OCTUBRE);

        servicio(FixedClock.at(PRIMERO_DE_OCTUBRE)).rotar(COHORTE, "rot:octubre");

        List<com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula> deMentor =
                banco.asignaciones.stream().filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR).toList();

        assertThat(deMentor).hasSize(4);
        assertThat(deMentor.stream().filter(a -> a.vigente()).count()).isEqualTo(2);
        assertThat(deMentor.stream().filter(a -> !a.vigente()))
                .allSatisfy(a -> assertThat(a.periodo().fin()).isEqualTo(PRIMERO_DE_OCTUBRE));
    }

    @Test
    @DisplayName("la rotacion avisa que la composicion cambio: sin eso, el chat queda congelado")
    void avisaLaComposicionCambiada() {
        dosGruposConMentor(PRIMERO_DE_OCTUBRE);

        servicio(FixedClock.at(PRIMERO_DE_OCTUBRE)).rotar(COHORTE, "rot:octubre");

        // Uno por grupo afectado. Es lo que dispara la reconciliacion de participantes del chat;
        // sin este evento, el mentor saliente conservaria el acceso a la conversacion.
        assertThat(banco.composicionesAvisadas)
                .containsExactlyInAnyOrder(GRUPO_A.value(), GRUPO_B.value());
    }

    @Test
    @DisplayName("repetir la misma clave de operacion no abre otros intervalos")
    void repetirEsIdempotente() {
        dosGruposConMentor(PRIMERO_DE_OCTUBRE);
        RotacionService servicio = servicio(FixedClock.at(PRIMERO_DE_OCTUBRE));

        servicio.rotar(COHORTE, "rot:octubre");
        int trasLaPrimera = banco.asignaciones.size();

        ResultadoRotacion segunda = servicio.rotar(COHORTE, "rot:octubre");

        assertThat(segunda.cambiosAplicados()).isZero();
        assertThat(segunda.yaAplicados()).isEqualTo(2);
        assertThat(banco.asignaciones).hasSize(trasLaPrimera);
    }

    @Test
    @DisplayName("un solo grupo y un solo mentor: conserva al actual y lo reporta pendiente (P-03)")
    void sinSustitutoConserva() {
        banco.cohorte(COHORTE, PoliticaMentoria.porDefecto(COHORTE));
        banco.grupo(GRUPO_A, COHORTE, TipoCelula.REGULAR, MENTOR_A, PRIMERO_DE_OCTUBRE);
        banco.mentorConPerfil(MENTOR_A);
        banco.asignar(GRUPO_A, MENTOR_A, FuncionAcompanamiento.MENTOR, PRIMERO_DE_OCTUBRE.minusSeconds(100), null);

        ResultadoRotacion resultado = servicio(FixedClock.at(PRIMERO_DE_OCTUBRE)).rotar(COHORTE, "rot:octubre");

        assertThat(resultado.cambiosAplicados()).isZero();
        assertThat(resultado.sinSustituto()).containsExactly(GRUPO_A.value());
        assertThat(banco.celulas.get(GRUPO_A.value()).mentorId()).isEqualTo(MENTOR_A);
    }

    @Test
    @DisplayName("la recepcion no entra en la rotacion")
    void recepcionNoRota() {
        banco.cohorte(COHORTE, PoliticaMentoria.porDefecto(COHORTE));
        banco.grupo(GRUPO_A, COHORTE, TipoCelula.RECEPCION, null, PRIMERO_DE_OCTUBRE);
        banco.grupo(GRUPO_B, COHORTE, TipoCelula.REGULAR, MENTOR_B, PRIMERO_DE_OCTUBRE);
        banco.mentorConPerfil(MENTOR_B);
        banco.asignar(GRUPO_B, MENTOR_B, FuncionAcompanamiento.MENTOR, PRIMERO_DE_OCTUBRE.minusSeconds(100), null);

        ResultadoRotacion resultado = servicio(FixedClock.at(PRIMERO_DE_OCTUBRE)).rotar(COHORTE, "rot:octubre");

        assertThat(resultado.sinSustituto()).containsExactly(GRUPO_B.value());
        assertThat(banco.celulas.get(GRUPO_A.value()).mentorId()).isNull();
    }

    // ── recuperación del scheduler (T23) ────────────────────────────────────

    @Test
    @DisplayName("un dia cualquiera el job no rota nada")
    void diaComunNoRota() {
        dosGruposConMentor(PRIMERO_DE_OCTUBRE);
        // 15 de octubre: no es dia 1 ni lunes de anclaje mensual.
        Clock quince = FixedClock.at(Instant.parse("2026-10-15T09:00:00Z"));

        assertThat(servicio(quince).rotarLasQueCorresponda()).isEmpty();
        assertThat(banco.celulas.get(GRUPO_A.value()).mentorId()).isEqualTo(MENTOR_A);
    }

    @Test
    @DisplayName("el dia de anclaje el job rota, y correrlo otra vez ese mismo dia no repite")
    void anclajeRotaUnaSolaVezPorDia() {
        dosGruposConMentor(PRIMERO_DE_OCTUBRE);
        Clock anclaje = FixedClock.at(PRIMERO_DE_OCTUBRE);

        List<ResultadoRotacion> primera = servicio(anclaje).rotarLasQueCorresponda();
        assertThat(primera).hasSize(1);
        assertThat(primera.getFirst().cambiosAplicados()).isEqualTo(2);

        // El job corre cada hora: la segunda corrida del mismo dia deriva la MISMA clave.
        List<ResultadoRotacion> segunda = servicio(FixedClock.at(PRIMERO_DE_OCTUBRE.plusSeconds(3600)))
                .rotarLasQueCorresponda();
        assertThat(segunda.getFirst().cambiosAplicados()).isZero();
        assertThat(segunda.getFirst().yaAplicados()).isEqualTo(2);
    }

    @Test
    @DisplayName("el dia local manda: 04:00 UTC del 1 de octubre en Lima todavia es 30 de setiembre")
    void elDiaLocalDecideElAnclaje() {
        dosGruposConMentor(PRIMERO_DE_OCTUBRE);
        // 03:00 UTC del 1-oct = 22:00 del 30-sep en Lima: todavia no es el anclaje.
        Clock antes = FixedClock.at(Instant.parse("2026-10-01T03:00:00Z"));

        assertThat(servicio(antes).rotarLasQueCorresponda()).isEmpty();
    }

    @Test
    @DisplayName("job caido varias semanas: al volver aplica UNA transicion, no las perdidas")
    void jobAtrasadoNoFabricaRotacionesHistoricas() {
        dosGruposConMentor(PRIMERO_DE_OCTUBRE);
        // Vuelve el 1 de diciembre: se perdio noviembre. Se aplica la de hoy, una sola vez.
        Clock diciembre = FixedClock.at(Instant.parse("2026-12-01T09:00:00Z"));

        List<ResultadoRotacion> resultados = servicio(diciembre).rotarLasQueCorresponda();

        assertThat(resultados).hasSize(1);
        assertThat(resultados.getFirst().cambiosAplicados()).isEqualTo(2);
        // Cuatro filas: dos cerradas y dos abiertas. No hay rastro inventado de noviembre.
        assertThat(banco.asignaciones.stream().filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR))
                .hasSize(4);
    }

    @Test
    @DisplayName("cadencia semanal: rota el lunes, no el martes")
    void cadenciaSemanal() {
        banco.cohorte(COHORTE, PoliticaMentoria.rehydrate(COHORTE, 10, CadenciaRotacion.SEMANAL, "America/Lima",
                4, 3, null, 1));
        banco.grupo(GRUPO_A, COHORTE, TipoCelula.REGULAR, MENTOR_A, PRIMERO_DE_OCTUBRE);
        banco.grupo(GRUPO_B, COHORTE, TipoCelula.REGULAR, MENTOR_B, PRIMERO_DE_OCTUBRE);
        banco.mentorConPerfil(MENTOR_A);
        banco.mentorConPerfil(MENTOR_B);
        banco.asignar(GRUPO_A, MENTOR_A, FuncionAcompanamiento.MENTOR, PRIMERO_DE_OCTUBRE.minusSeconds(100), null);
        banco.asignar(GRUPO_B, MENTOR_B, FuncionAcompanamiento.MENTOR, PRIMERO_DE_OCTUBRE.minusSeconds(100), null);

        // Martes 6 de octubre de 2026 en Lima.
        assertThat(servicio(FixedClock.at(Instant.parse("2026-10-06T15:00:00Z"))).rotarLasQueCorresponda())
                .isEmpty();
        // Lunes 5 de octubre de 2026 en Lima.
        assertThat(servicio(FixedClock.at(Instant.parse("2026-10-05T15:00:00Z"))).rotarLasQueCorresponda())
                .hasSize(1);
    }

    private int ultimaOperacionQueEmpiezaCon(String prefijo, String sufijo) {
        int ultima = -1;
        for (int i = 0; i < banco.operaciones.size(); i++) {
            String op = banco.operaciones.get(i);
            if (op.startsWith(prefijo) && op.endsWith(sufijo)) {
                ultima = i;
            }
        }
        return ultima;
    }
}
