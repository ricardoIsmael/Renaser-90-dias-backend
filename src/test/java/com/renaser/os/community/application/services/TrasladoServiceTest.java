package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.acompanamiento.TrasladarAprendicesUseCase.ResultadoTraslado;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El traslado aplicado. Lo que se verifica acá y no en el planificador es lo transaccional:
 * que la pertenencia anterior se cierre antes de abrir la nueva, que el puntero de mentor
 * quede apuntando al del grupo destino, y que repetir el comando no abra otro intervalo.
 */
class TrasladoServiceTest {

    /** 09:00 UTC = 04:00 en Lima. */
    private static final Instant AHORA = Instant.parse("2026-09-20T09:00:00Z");

    private static final CohorteId COHORTE = CohorteId.of(UUID.randomUUID());
    private static final CelulaId RECEPCION = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
    private static final CelulaId GRUPO_A = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final CelulaId GRUPO_B = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final UserId MENTOR_A = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));

    private AcompanamientoEnMemoria banco;

    @BeforeEach
    void preparar() {
        banco = new AcompanamientoEnMemoria();
        banco.cohorte(COHORTE, PoliticaMentoria.rehydrate(COHORTE, 10, CadenciaRotacion.MENSUAL, "America/Lima",
                4, 3, RECEPCION, 1));
        banco.grupo(RECEPCION, COHORTE, TipoCelula.RECEPCION, null, AHORA);
        banco.grupo(GRUPO_A, COHORTE, TipoCelula.REGULAR, MENTOR_A, AHORA);
        banco.mentorConPerfil(MENTOR_A);
        banco.asignar(GRUPO_A, MENTOR_A, FuncionAcompanamiento.MENTOR, AHORA.minusSeconds(100_000), null);
    }

    private TrasladoService servicio() {
        return new TrasladoService(banco.cargaCelulas, banco.cargaAsignaciones, banco.guardaAsignacion,
                banco.cargaPolitica, banco.buscaParticipacion, banco.punteroDeUsers, banco.publicador,
                FixedClock.at(AHORA), banco.idGenerator);
    }

    /** Ana en recepción, con el día de programa indicado. */
    private void anaEnRecepcion(int dia) {
        banco.aprendiz(ANA, dia, true, RECEPCION.value());
        banco.asignar(RECEPCION, ANA, FuncionAcompanamiento.APRENDIZ, AHORA.minusSeconds(200_000), null);
    }

    @Test
    @DisplayName("dia 3 en recepcion: no se mueve")
    void diaTresSeQueda() {
        anaEnRecepcion(3);

        ResultadoTraslado resultado = servicio().ubicar(ANA);

        assertThat(resultado.destino()).isEqualTo("SIN_CAMBIO");
        assertThat(banco.asignaciones.stream().filter(a -> a.usuarioId().equals(ANA)).filter(a -> a.vigente()))
                .hasSize(1);
    }

    @Test
    @DisplayName("dia 4: cierra recepcion y abre grupo estable, en ese orden")
    void diaCuatroSeMueve() {
        anaEnRecepcion(4);

        ResultadoTraslado resultado = servicio().ubicar(ANA);

        assertThat(resultado.destino()).isEqualTo("GRUPO_ESTABLE");
        assertThat(resultado.grupoId()).isEqualTo(GRUPO_A.value());

        var deAna = banco.asignaciones.stream()
                .filter(a -> a.usuarioId().equals(ANA) && a.funcion() == FuncionAcompanamiento.APRENDIZ).toList();
        assertThat(deAna).hasSize(2);
        // Exactamente una vigente: el indice de exclusion de V45 no admitiria dos.
        assertThat(deAna.stream().filter(a -> a.vigente())).hasSize(1);
        assertThat(deAna.stream().filter(a -> a.vigente()).findFirst().orElseThrow().celulaId())
                .isEqualTo(GRUPO_A);
        // El cierre va antes que la apertura.
        assertThat(banco.primeraOperacion("cerrar:APRENDIZ"))
                .isLessThan(banco.primeraOperacion("abrir:APRENDIZ"));
    }

    @Test
    @DisplayName("al llegar al grupo, el puntero de mentor apunta al mentor de ESE grupo")
    void punteroApuntaAlMentorDelDestino() {
        anaEnRecepcion(4);

        servicio().ubicar(ANA);

        assertThat(banco.punteros.get(ANA)).isEqualTo(GRUPO_A.value() + "/" + MENTOR_A.value());
    }

    @Test
    @DisplayName("el traslado avisa por los DOS grupos: el que pierde y el que gana")
    void avisaAmbosGrupos() {
        anaEnRecepcion(4);

        servicio().ubicar(ANA);

        // Sin el aviso del origen, el chat de recepcion la seguiria mostrando como integrante.
        assertThat(banco.composicionesAvisadas)
                .containsExactlyInAnyOrder(GRUPO_A.value(), RECEPCION.value());
    }

    @Test
    @DisplayName("repetir el traslado no abre otro intervalo")
    void trasladoIdempotente() {
        anaEnRecepcion(4);
        TrasladoService servicio = servicio();

        servicio.ubicar(ANA);
        int tras = banco.asignaciones.size();
        ResultadoTraslado segunda = servicio.ubicar(ANA);

        assertThat(banco.asignaciones).hasSize(tras);
        assertThat(segunda.destino()).isEqualTo("SIN_CAMBIO");
    }

    @Test
    @DisplayName("todos los grupos llenos: ESPERANDO_GRUPO y NO pierde la recepcion")
    void sinCupoConservaRecepcion() {
        anaEnRecepcion(4);
        for (int i = 0; i < 10; i++) {
            UserId relleno = UserId.of(UUID.randomUUID());
            banco.asignar(GRUPO_A, relleno, FuncionAcompanamiento.APRENDIZ, AHORA.minusSeconds(100), null);
        }

        ResultadoTraslado resultado = servicio().ubicar(ANA);

        assertThat(resultado.destino()).isEqualTo("ESPERANDO_GRUPO");
        assertThat(resultado.grupoId()).isNull();
        // Sigue en recepcion: nadie se queda sin chat mientras espera (P-04).
        var vigente = banco.asignaciones.stream()
                .filter(a -> a.usuarioId().equals(ANA) && a.vigente()).findFirst().orElseThrow();
        assertThat(vigente.celulaId()).isEqualTo(RECEPCION);
    }

    @Test
    @DisplayName("el mentor no ocupa lugar: con 9 aprendices y un mentor, todavia entra el decimo")
    void elMentorNoConsumeCupo() {
        anaEnRecepcion(4);
        for (int i = 0; i < 9; i++) {
            banco.asignar(GRUPO_A, UserId.of(UUID.randomUUID()), FuncionAcompanamiento.APRENDIZ,
                    AHORA.minusSeconds(100), null);
        }

        assertThat(servicio().ubicar(ANA).destino()).isEqualTo("GRUPO_ESTABLE");
    }

    @Test
    @DisplayName("programa sin activar: el reloj no arranco, no entra a ningun lado")
    void programaSinActivarNoEntra() {
        banco.aprendiz(ANA, 0, false, RECEPCION.value());
        banco.asignar(RECEPCION, ANA, FuncionAcompanamiento.APRENDIZ, AHORA.minusSeconds(100), null);

        assertThat(servicio().ubicar(ANA).destino()).isEqualTo("SIN_CAMBIO");
    }

    @Test
    @DisplayName("quien ya esta en grupo estable no vuelve a recepcion aunque le bajen el dia")
    void noRetrocedeAGrupoDeRecepcion() {
        banco.aprendiz(ANA, 2, true, GRUPO_A.value());
        banco.asignar(GRUPO_A, ANA, FuncionAcompanamiento.APRENDIZ, AHORA.minusSeconds(100), null);

        assertThat(servicio().ubicar(ANA).destino()).isEqualTo("SIN_CAMBIO");
    }

    @Test
    @DisplayName("elige el grupo con menos gente entre los dos disponibles")
    void eligeElMenosCargado() {
        banco.grupo(GRUPO_B, COHORTE, TipoCelula.REGULAR, null, AHORA);
        anaEnRecepcion(4);
        for (int i = 0; i < 6; i++) {
            banco.asignar(GRUPO_A, UserId.of(UUID.randomUUID()), FuncionAcompanamiento.APRENDIZ,
                    AHORA.minusSeconds(100), null);
        }

        assertThat(servicio().ubicar(ANA).grupoId()).isEqualTo(GRUPO_B.value());
    }

    @Test
    @DisplayName("un fallo en un aprendiz no frena el lote")
    void elLoteAislaFallos() {
        anaEnRecepcion(4);
        UserId roto = UserId.of(UUID.randomUUID());
        // Inscrito pero sin celula: el servicio lo saltea sin reventar el lote.
        banco.aprendiz(roto, 5, true, null);

        assertThat(servicio().procesarLote(10)).isEqualTo(1);
    }
}
