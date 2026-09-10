package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.GuiasConfigurados;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.PoliticaConfigurada;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.ReconfigurarPolitica;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.ReemplazarGuias;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.ReferenciaDeUsuario;
import com.renaser.os.community.application.ports.out.acompanamiento.SavePoliticaMentoriaPort;
import com.renaser.os.community.domain.model.acompanamiento.CadenciaRotacion;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaDesactualizadaException;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.FixedClock;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Configuración de la mentoría por un administrador.
 *
 * <p>Lo que más se prueba acá es el reemplazo atómico de guías: si una referencia falla, no se
 * escribe ninguna. Reemplazar de a uno dejaría la recepción a medias cuando el tercer correo está
 * mal escrito, y el administrador tendría que adivinar cuáles entraron.
 */
class ConfiguracionMentoriaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");
    private static final CohorteId COHORTE = CohorteId.of(UUID.randomUUID());
    private static final CelulaId RECEPCION = CelulaId.of(UUID.randomUUID());
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());

    private static final UserId GUIA_A = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId GUIA_B = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final UserId SUSPENDIDO = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a3"));

    private AcompanamientoEnMemoria banco;
    private final Map<String, UserSummary> porEmail = new LinkedHashMap<>();
    private final Map<UserId, UserSummary> porId = new LinkedHashMap<>();
    private PoliticaMentoria guardada;

    @BeforeEach
    void preparar() {
        banco = new AcompanamientoEnMemoria();
        porEmail.clear();
        porId.clear();
        guardada = null;

        banco.cohorte(COHORTE, PoliticaMentoria.rehydrate(COHORTE, 10, CadenciaRotacion.MENSUAL, "America/Lima",
                4, 3, RECEPCION, 1));
        banco.grupo(RECEPCION, COHORTE, TipoCelula.RECEPCION, null, AHORA);

        persona(GUIA_A, "ana@renaser.test", "Ana Guia", UserStatus.ACTIVE);
        persona(GUIA_B, "beto@renaser.test", "Beto Guia", UserStatus.ACTIVE);
        persona(SUSPENDIDO, "carla@renaser.test", "Carla Suspendida", UserStatus.SUSPENDED);
    }

    private void persona(UserId id, String email, String nombre, UserStatus estado) {
        UserSummary perfil = new UserSummary(id, nombre, null, UserRole.ADMIN, estado);
        porId.put(id, perfil);
        porEmail.put(email, perfil);
    }

    private ConfiguracionMentoriaService servicio() {
        return servicioEn(AHORA);
    }

    /**
     * Cada llamada del administrador ocurre en un instante distinto. Importa cuando una designa y
     * la siguiente retira: cerrar un intervalo en el mismo instante en que se abrió daría una
     * asignación de duración cero, que el dominio rechaza con razón —no existió— pero que un
     * reloj fijo fabrica artificialmente.
     */
    private ConfiguracionMentoriaService servicioEn(Instant momento) {
        UserSummaryFinder usuarios = new UserSummaryFinder() {
            @Override
            public Optional<UserSummary> findById(UserId id) {
                return Optional.ofNullable(porId.get(id));
            }

            @Override
            public Map<UserId, UserSummary> findByIds(Collection<UserId> ids) {
                return Map.of();
            }

            @Override
            public Optional<UserSummary> findByEmail(String email) {
                return Optional.ofNullable(porEmail.get(email));
            }
        };
        SavePoliticaMentoriaPort guardar = politica -> {
            guardada = politica;
            banco.politicas.put(politica.cohorteId(), politica);
            return politica;
        };
        return new ConfiguracionMentoriaService(banco.cargaPolitica, guardar, banco.cargaAsignaciones,
                banco.guardaAsignacion, banco.cargaCelulas, usuarios, banco.publicador,
                FixedClock.at(momento), banco.idGenerator);
    }

    private List<UserId> guiasVigentes() {
        return guiasVigentesEn(AHORA);
    }

    /**
     * Quiénes son guías EN ESE INSTANTE. Preguntar siempre por {@code AHORA} daría falsos
     * positivos: alguien retirado un minuto después sigue siendo guía en el instante anterior, y
     * eso es correcto —el historial no se borra—, pero no es lo que la prueba quiere afirmar.
     */
    private List<UserId> guiasVigentesEn(Instant momento) {
        return banco.asignaciones.stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.GUIA)
                .filter(a -> a.vigenteEn(momento))
                .map(a -> a.usuarioId())
                .toList();
    }

    // ── política ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cambiar la politica sube la version")
    void reconfigurarSubeVersion() {
        PoliticaConfigurada resultado = servicio().reconfigurar(new ReconfigurarPolitica(ADMIN, COHORTE,
                12, "SEMANAL", "America/Lima", 5, 4, 1));

        assertThat(resultado.capacidadCelula()).isEqualTo(12);
        assertThat(resultado.cadenciaRotacion()).isEqualTo("SEMANAL");
        assertThat(resultado.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("editar con una version vieja no pisa el cambio del otro administrador")
    void versionObsoletaRechazada() {
        assertThatThrownBy(() -> servicio().reconfigurar(new ReconfigurarPolitica(ADMIN, COHORTE,
                12, "MENSUAL", "America/Lima", 4, 3, 99)))
                .isInstanceOf(PoliticaDesactualizadaException.class);
    }

    @Test
    @DisplayName("una capacidad fuera de 10..15 se rechaza")
    void capacidadFueraDeRango() {
        assertThatThrownBy(() -> servicio().reconfigurar(new ReconfigurarPolitica(ADMIN, COHORTE,
                16, "MENSUAL", "America/Lima", 4, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bajar la capacidad de 15 a 10 con 12 integrantes NO expulsa a nadie (RF-28)")
    void reducirCapacidadNoExpulsa() {
        CelulaId grupo = CelulaId.of(UUID.randomUUID());
        banco.grupo(grupo, COHORTE, TipoCelula.REGULAR, null, AHORA);
        for (int i = 0; i < 12; i++) {
            banco.asignar(grupo, UserId.of(UUID.randomUUID()), FuncionAcompanamiento.APRENDIZ,
                    AHORA.minusSeconds(100), null);
        }

        servicio().reconfigurar(new ReconfigurarPolitica(ADMIN, COHORTE, 10, "MENSUAL", "America/Lima",
                4, 3, 1));

        // Los doce siguen adentro: la politica limita ALTAS, no saca gente.
        long dentro = banco.asignaciones.stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                .filter(a -> a.vigenteEn(AHORA)).count();
        assertThat(dentro).isEqualTo(12);
    }

    @Test
    @DisplayName("una cadencia inventada se rechaza con un mensaje que dice cuales valen")
    void cadenciaInvalida() {
        assertThatThrownBy(() -> servicio().reconfigurar(new ReconfigurarPolitica(ADMIN, COHORTE,
                10, "QUINCENAL", "America/Lima", 4, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MENSUAL");
    }

    // ── guías ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("se designan guias por UUID y por email indistintamente")
    void porUuidYPorEmail() {
        GuiasConfigurados resultado = servicio().reemplazarGuias(new ReemplazarGuias(ADMIN, COHORTE, null,
                List.of(new ReferenciaDeUsuario(GUIA_A.value(), null),
                        new ReferenciaDeUsuario(null, "beto@renaser.test"))));

        assertThat(resultado.guias()).containsExactlyInAnyOrder(GUIA_A.value(), GUIA_B.value());
        assertThat(guiasVigentes()).containsExactlyInAnyOrder(GUIA_A, GUIA_B);
    }

    @Test
    @DisplayName("mandar la misma lista dos veces no abre otro intervalo")
    void reemplazoIdempotente() {
        ReemplazarGuias comando = new ReemplazarGuias(ADMIN, COHORTE, null,
                List.of(new ReferenciaDeUsuario(GUIA_A.value(), null)));

        servicio().reemplazarGuias(comando);
        int tras = banco.asignaciones.size();
        servicio().reemplazarGuias(comando);

        assertThat(banco.asignaciones).hasSize(tras);
        assertThat(guiasVigentes()).containsExactly(GUIA_A);
    }

    @Test
    @DisplayName("quien sale de la lista deja de ser guia")
    void reemplazoQuitaAlQueYaNoEsta() {
        servicio().reemplazarGuias(new ReemplazarGuias(ADMIN, COHORTE, null,
                List.of(new ReferenciaDeUsuario(GUIA_A.value(), null),
                        new ReferenciaDeUsuario(GUIA_B.value(), null))));

        // Un minuto después, el administrador manda la lista sin Ana.
        servicioEn(AHORA.plusSeconds(60)).reemplazarGuias(new ReemplazarGuias(ADMIN, COHORTE, null,
                List.of(new ReferenciaDeUsuario(GUIA_B.value(), null))));

        assertThat(guiasVigentesEn(AHORA.plusSeconds(60))).containsExactly(GUIA_B);
        // Y el historial se conserva: Ana FUE guia hasta ese minuto.
        assertThat(guiasVigentesEn(AHORA)).contains(GUIA_A);
    }

    @Test
    @DisplayName("una cuenta suspendida no puede atender la recepcion")
    void suspendidoRechazado() {
        assertThatThrownBy(() -> servicio().reemplazarGuias(new ReemplazarGuias(ADMIN, COHORTE, null,
                List.of(new ReferenciaDeUsuario(null, "carla@renaser.test")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no esta activa");
    }

    @Test
    @DisplayName("si UNA referencia falla no se escribe NINGUNA: el reemplazo es atomico")
    void unaMalaAbortaTodo() {
        assertThatThrownBy(() -> servicio().reemplazarGuias(new ReemplazarGuias(ADMIN, COHORTE, null,
                List.of(new ReferenciaDeUsuario(GUIA_A.value(), null),
                        new ReferenciaDeUsuario(null, "noexiste@renaser.test")))))
                .isInstanceOf(IllegalArgumentException.class);

        // Ni siquiera el primero, que era valido.
        assertThat(guiasVigentes()).isEmpty();
    }

    @Test
    @DisplayName("nombrar a alguien por UUID Y email a la vez es ambiguo y se rechaza")
    void referenciaAmbigua() {
        assertThatThrownBy(() -> servicio().reemplazarGuias(new ReemplazarGuias(ADMIN, COHORTE, null,
                List.of(new ReferenciaDeUsuario(GUIA_A.value(), "ana@renaser.test")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId O por email");
    }

    @Test
    @DisplayName("designar un guia NO le cambia el rol: acompanar no es un rol global")
    void noCambiaRoles() {
        servicio().reemplazarGuias(new ReemplazarGuias(ADMIN, COHORTE, null,
                List.of(new ReferenciaDeUsuario(GUIA_A.value(), null))));

        // El doble de usuarios es de solo lectura: si el servicio intentara escribir un rol,
        // no tendria por donde. La designacion vive en asignaciones_celula y en ningun otro lado.
        assertThat(porId.get(GUIA_A).role()).isEqualTo(UserRole.ADMIN);
        assertThat(guiasVigentes()).containsExactly(GUIA_A);
    }

    @Test
    @DisplayName("el chat de recepcion se entera del cambio de guias")
    void avisaAlChat() {
        servicio().reemplazarGuias(new ReemplazarGuias(ADMIN, COHORTE, null,
                List.of(new ReferenciaDeUsuario(GUIA_A.value(), null))));

        assertThat(banco.composicionesAvisadas).containsExactly(RECEPCION.value());
    }

    @Test
    @DisplayName("una celula de otra cohorte no sirve como recepcion")
    void recepcionDeOtraCohorte() {
        CohorteId otra = CohorteId.of(UUID.randomUUID());
        CelulaId ajena = CelulaId.of(UUID.randomUUID());
        banco.grupo(ajena, otra, TipoCelula.RECEPCION, null, AHORA);

        assertThatThrownBy(() -> servicio().reemplazarGuias(new ReemplazarGuias(ADMIN, COHORTE, ajena.value(),
                List.of(new ReferenciaDeUsuario(GUIA_A.value(), null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pertenece a la cohorte");
    }
}
