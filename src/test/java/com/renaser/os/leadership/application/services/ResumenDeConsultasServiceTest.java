package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.api.RevisionDelLiderEvent;
import com.renaser.os.leadership.application.ports.out.LeerConsultasDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * El barrido horario del resumen al mentor (spec 005, FR-017): quién está en su hora, qué se lee y cómo se publica.
 * Las reglas de qué cuenta y qué dice están en {@code ReglaDelResumenDeConsultasTest}; acá, el cableado: la zona de cada
 * mentor, la lectura por lotes, el fallo aislado por mentor, la transacción propia de cada evento (E-250) y la madrugada
 * UTC, donde el día de Lima y el de UTC no coinciden (regla 02).
 */
@ExtendWith(MockitoExtension.class)
class ResumenDeConsultasServiceTest {

    /** Domingo 18 de octubre, 20:10 en Lima = lunes 19, 01:10 UTC. */
    private static final Instant LAS_VEINTE_EN_LIMA = Instant.parse("2026-10-19T01:10:00Z");
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 18);
    private static final UserId LIDER = id("c1");
    private static final UserId LUISA = id("a1");
    private static final UserId RAUL = id("a2");
    private static final UserId ANA = id("b1");
    private static final UserId LUIS = id("b2");
    private static final UserId PIA = id("b3");
    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID AURORA = UUID.fromString("00000000-0000-0000-0000-00000000f002");

    @Mock
    private AcompanamientoFinder acompanamiento;
    @Mock
    private ParticipacionProgramaFinder participaciones;
    @Mock
    private UserSummaryFinder usuarios;

    private final List<ConsultaDeFicha> registradas = new ArrayList<>();
    private final List<Collection<UserId>> lecturas = new ArrayList<>();
    private final List<Object> publicados = new ArrayList<>();
    private final List<Boolean> enTransaccion = new ArrayList<>();
    private final List<Integer> propagaciones = new ArrayList<>();
    private boolean transaccionAbierta;
    private final Map<UserId, String> nombres = new HashMap<>();

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    @BeforeEach
    void preparar() {
        nombres.put(ANA, "Ana María Pérez");
        nombres.put(LUIS, "Luis Soto");
        nombres.put(PIA, "Pía Rojas");
        lenient().when(participaciones.deParticipante(any())).thenReturn(Optional.empty());
        lenient().when(usuarios.findByIds(any())).thenAnswer(invocacion -> {
            Map<UserId, UserSummary> encontrados = new HashMap<>();
            for (UserId persona : (Collection<UserId>) invocacion.getArgument(0)) {
                if (nombres.containsKey(persona)) {
                    encontrados.put(persona, new UserSummary(persona, nombres.get(persona), null, UserRole.TRAINEE,
                            UserStatus.ACTIVE));
                }
            }
            return encontrados;
        });
    }

    private void grupo(UUID grupoId, UserId mentor, String zona) {
        List<GrupoAcompanado> actuales = new ArrayList<>(acompanamiento.gruposConMentorVigente(LAS_VEINTE_EN_LIMA));
        actuales.add(new GrupoAcompanado(grupoId, "Grupo " + grupoId, mentor, UUID.randomUUID(), zona, 3));
        lenient().when(acompanamiento.gruposConMentorVigente(any())).thenReturn(actuales);
    }

    private void consulta(UserId consultor, UserRole rol, UserId persona, UUID grupo, UserId mentor, Instant cuando) {
        registradas.add(new ConsultaDeFicha(UUID.randomUUID(), consultor, rol, persona, grupo, mentor,
                LocalDate.of(2026, 10, 10), cuando));
    }

    private void delLider(UserId persona, UUID grupo, UserId mentor, Instant cuando) {
        consulta(LIDER, UserRole.MENTOR_LEAD, persona, grupo, mentor, cuando);
    }

    private ResumenDeConsultasService servicio(Instant ahora) {
        LeerConsultasDeFichaPort puerto = (mentores, desde, hasta) -> {
            lecturas.add(List.copyOf(mentores));
            return registradas.stream().filter(c -> c.rolConsultor() == UserRole.MENTOR_LEAD)
                    .filter(c -> mentores.contains(c.mentorId()))
                    .filter(c -> !c.consultadaEn().isBefore(desde) && c.consultadaEn().isBefore(hasta)).toList();
        };
        PlatformTransactionManager transacciones = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definicion) {
                propagaciones.add(definicion.getPropagationBehavior());
                transaccionAbierta = true;
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus estado) {
                transaccionAbierta = false;
            }

            @Override
            public void rollback(TransactionStatus estado) {
                transaccionAbierta = false;
            }
        };
        return new ResumenDeConsultasService(acompanamiento, participaciones, puerto, usuarios, evento -> {
            publicados.add(evento);
            enTransaccion.add(transaccionAbierta);
        }, transacciones, FixedClock.at(ahora));
    }

    private List<RevisionDelLiderEvent> avisos() {
        return publicados.stream().map(RevisionDelLiderEvent.class::cast).toList();
    }

    @Test
    @DisplayName("a las 20:10 de Lima publica un aviso con las personas que miro el lider, la clave del dia y la ruta del grupo")
    void publicaElResumen() {
        grupo(FENIX, LUISA, "America/Lima");
        delLider(ANA, FENIX, LUISA, Instant.parse("2026-10-18T15:00:00Z"));
        delLider(LUIS, FENIX, LUISA, Instant.parse("2026-10-18T16:00:00Z"));
        delLider(ANA, FENIX, LUISA, Instant.parse("2026-10-18T22:00:00Z"));

        assertThat(servicio(LAS_VEINTE_EN_LIMA).resumir()).isEqualTo(1);

        assertThat(avisos()).singleElement().satisfies(aviso -> {
            assertThat(aviso.mentorId()).isEqualTo(LUISA.value());
            assertThat(aviso.grupoId()).isEqualTo(FENIX);
            assertThat(aviso.cuerpo()).isEqualTo("Hoy revisó a Ana y Luis");
            assertThat(aviso.personas()).isEqualTo(2);
            assertThat(aviso.dia()).isEqualTo(DOMINGO);
            assertThat(aviso.claveDeduplicacion()).isEqualTo(ReglaDelResumenDeConsultas.clave(LUISA, DOMINGO));
            assertThat(aviso.generadoEn()).isEqualTo(LAS_VEINTE_EN_LIMA);
            assertThat(aviso.rutaApp()).isEqualTo("/mentor/groups/" + FENIX + "/semaforo");
        });
    }

    @Test
    @DisplayName("fuera de la hora del mentor no lee ni publica nada, y a las 20 UTC no toca en Lima")
    void fueraDeHora() {
        grupo(FENIX, LUISA, "America/Lima");
        delLider(ANA, FENIX, LUISA, Instant.parse("2026-10-18T15:00:00Z"));

        assertThat(servicio(Instant.parse("2026-10-19T00:59:00Z")).resumir()).isZero();
        assertThat(servicio(Instant.parse("2026-10-19T02:00:00Z")).resumir()).isZero();
        assertThat(servicio(Instant.parse("2026-10-18T20:10:00Z")).resumir()).isZero();

        assertThat(publicados).isEmpty();
        assertThat(lecturas).isEmpty();
    }

    @Test
    @DisplayName("el dia es el de Lima aunque en UTC ya sea otro: la consulta de las 23:30 de Lima entra al domingo")
    void madrugadaUtc() {
        grupo(FENIX, LUISA, "America/Lima");
        delLider(ANA, FENIX, LUISA, Instant.parse("2026-10-18T05:00:00Z"));
        delLider(LUIS, FENIX, LUISA, Instant.parse("2026-10-17T04:59:00Z"));
        delLider(PIA, FENIX, LUISA, Instant.parse("2026-10-19T01:05:00Z"));

        servicio(LAS_VEINTE_EN_LIMA).resumir();

        assertThat(avisos()).singleElement().satisfies(aviso -> {
            assertThat(aviso.cuerpo()).isEqualTo("Hoy revisó a Ana y Pía");
            assertThat(aviso.dia()).isEqualTo(DOMINGO);
        });
    }

    @Test
    @DisplayName("cada mentor en su zona: a las 18:10 UTC solo toca el de Madrid")
    void cadaMentorEnSuZona() {
        grupo(FENIX, LUISA, "America/Lima");
        grupo(AURORA, RAUL, "Europe/Madrid");
        Instant lasVeinteEnMadrid = Instant.parse("2026-10-18T18:10:00Z");
        delLider(ANA, FENIX, LUISA, Instant.parse("2026-10-18T15:00:00Z"));
        delLider(PIA, AURORA, RAUL, Instant.parse("2026-10-18T10:00:00Z"));

        servicio(lasVeinteEnMadrid).resumir();

        assertThat(avisos()).singleElement().satisfies(aviso -> {
            assertThat(aviso.mentorId()).isEqualTo(RAUL.value());
            assertThat(aviso.cuerpo()).isEqualTo("Hoy revisó a Pía");
            assertThat(aviso.dia()).isEqualTo(DOMINGO);
        });
    }

    @Test
    @DisplayName("la zona sale del programa del mentor si tiene uno; si no, de su primer grupo; por defecto Lima")
    void zonaDelMentor() {
        grupo(FENIX, LUISA, "Europe/Madrid");
        grupo(AURORA, RAUL, "esto-no-es-una-zona");
        lenient().when(participaciones.deParticipante(RAUL)).thenReturn(Optional.of(participacion(RAUL, false, "Asia/Tokyo")));
        lenient().when(participaciones.deParticipante(LUISA)).thenReturn(Optional.of(participacion(LUISA, true, "Europe/Madrid")));
        delLider(ANA, FENIX, LUISA, Instant.parse("2026-10-18T10:00:00Z"));
        delLider(PIA, AURORA, RAUL, Instant.parse("2026-10-18T15:00:00Z"));

        // 18:10 UTC = 20:10 en Madrid (Luisa, por su programa); el grupo de Raul tiene una zona invalida: Lima, 13:10.
        servicio(Instant.parse("2026-10-18T18:10:00Z")).resumir();
        assertThat(avisos()).singleElement().satisfies(aviso -> assertThat(aviso.mentorId()).isEqualTo(LUISA.value()));
        publicados.clear();

        // 01:10 UTC = 20:10 en Lima (Raul, por defecto), pero no es la hora de Madrid.
        servicio(LAS_VEINTE_EN_LIMA).resumir();
        assertThat(avisos()).singleElement().satisfies(aviso -> assertThat(aviso.mentorId()).isEqualTo(RAUL.value()));
    }

    private static ParticipacionPrograma participacion(UserId id, boolean inscrito, String zona) {
        return new ParticipacionPrograma(id, inscrito, 3, LocalDate.of(2026, 9, 1), ZoneId.of(zona),
                FasePrograma.PHASE_1_REBIRTH, null, null, UserRole.MENTOR, false, true);
    }

    @Test
    @DisplayName("Administracion y Alquimista no generan aviso: sin consultas del lider no se publica nada")
    void soloElLider() {
        grupo(FENIX, LUISA, "America/Lima");
        consulta(id("d1"), UserRole.ADMIN, ANA, FENIX, LUISA, Instant.parse("2026-10-18T15:00:00Z"));
        consulta(id("d2"), UserRole.ALCHEMIST, LUIS, FENIX, LUISA, Instant.parse("2026-10-18T16:00:00Z"));

        assertThat(servicio(LAS_VEINTE_EN_LIMA).resumir()).isZero();
        assertThat(publicados).isEmpty();
    }

    @Test
    @DisplayName("una persona cuya cuenta ya no existe no se nombra; si no queda nadie, no hay aviso")
    void cuentaBorrada() {
        grupo(FENIX, LUISA, "America/Lima");
        delLider(ANA, FENIX, LUISA, Instant.parse("2026-10-18T15:00:00Z"));
        delLider(id("ff"), FENIX, LUISA, Instant.parse("2026-10-18T16:00:00Z"));
        grupo(AURORA, RAUL, "America/Lima");
        delLider(id("fe"), AURORA, RAUL, Instant.parse("2026-10-18T16:00:00Z"));

        servicio(LAS_VEINTE_EN_LIMA).resumir();

        assertThat(avisos()).singleElement().satisfies(aviso -> {
            assertThat(aviso.mentorId()).isEqualTo(LUISA.value());
            assertThat(aviso.cuerpo()).isEqualTo("Hoy revisó a Ana");
            assertThat(aviso.personas()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("un mentor que falla no frena a los demas, y cada aviso sale en su propia transaccion (E-250)")
    void falloAisladoYTransaccionPropia() {
        grupo(FENIX, LUISA, "America/Lima");
        grupo(AURORA, RAUL, "America/Lima");
        delLider(ANA, FENIX, LUISA, Instant.parse("2026-10-18T15:00:00Z"));
        delLider(PIA, AURORA, RAUL, Instant.parse("2026-10-18T16:00:00Z"));
        lenient().when(usuarios.findByIds(List.of(ANA))).thenThrow(new IllegalStateException("base caida"));

        assertThat(servicio(LAS_VEINTE_EN_LIMA).resumir()).isEqualTo(1);

        assertThat(avisos()).singleElement().satisfies(aviso -> assertThat(aviso.mentorId()).isEqualTo(RAUL.value()));
        assertThat(enTransaccion).containsExactly(true);
        assertThat(propagaciones).containsExactly(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    @DisplayName("lee las consultas por lotes de 100 mentores, nunca una consulta por mentor")
    void lecturaPorLotes() {
        List<UserId> mentores = IntStream.range(0, 250)
                .mapToObj(i -> UserId.of(UUID.nameUUIDFromBytes(("mentor-" + i).getBytes()))).toList();
        List<GrupoAcompanado> grupos = mentores.stream().map(m -> new GrupoAcompanado(UUID.randomUUID(), "G", m,
                UUID.randomUUID(), "America/Lima", 3)).toList();
        lenient().when(acompanamiento.gruposConMentorVigente(any())).thenReturn(grupos);

        servicio(LAS_VEINTE_EN_LIMA).resumir();

        assertThat(lecturas).extracting(Collection::size).containsExactly(100, 100, 50);
    }

    @Test
    @DisplayName("un mentor con varios grupos recibe un solo aviso, con la ruta del grupo con mas personas miradas")
    void unAvisoPorMentor() {
        grupo(FENIX, LUISA, "America/Lima");
        grupo(AURORA, LUISA, "America/Lima");
        delLider(ANA, FENIX, LUISA, Instant.parse("2026-10-18T15:00:00Z"));
        delLider(LUIS, AURORA, LUISA, Instant.parse("2026-10-18T16:00:00Z"));
        delLider(PIA, AURORA, LUISA, Instant.parse("2026-10-18T17:00:00Z"));

        assertThat(servicio(LAS_VEINTE_EN_LIMA).resumir()).isEqualTo(1);

        assertThat(avisos()).singleElement().satisfies(aviso -> {
            assertThat(aviso.grupoId()).isEqualTo(AURORA);
            assertThat(aviso.cuerpo()).isEqualTo("Hoy revisó a Ana, Luis y Pía");
        });
    }

    @Test
    @DisplayName("la clave del aviso de un mismo dia no cambia entre corridas: repetir el barrido publica la misma clave")
    void corridaRepetida() {
        grupo(FENIX, LUISA, "America/Lima");
        delLider(ANA, FENIX, LUISA, Instant.parse("2026-10-18T15:00:00Z"));

        servicio(LAS_VEINTE_EN_LIMA).resumir();
        servicio(LAS_VEINTE_EN_LIMA.plusSeconds(1800)).resumir();

        assertThat(avisos()).hasSize(2).extracting(RevisionDelLiderEvent::claveDeduplicacion).containsOnly(
                ReglaDelResumenDeConsultas.clave(LUISA, DOMINGO));
    }
}
