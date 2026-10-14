package com.renaser.os.plataforma.domain.model.estado;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.catalogo.PoliticaDeProcesos;
import com.renaser.os.plataforma.domain.model.catalogo.ValorPorDefecto;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** La regla del contrato §5, caso por caso. Sin Spring. */
class EvaluacionDeFuncionalidadesTest {

    private static final UserId SOPORTE = UserId.of(UUID.randomUUID());
    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final Function<String, Optional<String>> SIN_AUTOMATICOS = clave -> Optional.empty();

    private final EvaluacionDeFuncionalidades evaluacion =
            new EvaluacionDeFuncionalidades(CatalogoDeFuncionalidades.inicial());

    @Test
    @DisplayName("sin filas, todo prendido (valor por defecto)")
    void sinFilasTodoPrendido() {
        assertThat(evaluar("chat.grupos", FotoDeEstados.vacia(), UserRole.TRAINEE).prendida()).isTrue();
    }

    @Test
    @DisplayName("apagada para todos: apagada para cualquier rol y para un proceso, con su aviso")
    void apagadaParaTodos() {
        FotoDeEstados foto = foto(estado("chat.grupos", false, Set.of(), "Volvemos a las 18:00"));

        ResultadoDeEvaluacion resultado = evaluar("chat.grupos", foto, UserRole.MENTOR);

        assertThat(resultado.prendida()).isFalse();
        assertThat(resultado.aviso()).isEqualTo("Volvemos a las 18:00");
        assertThat(resultado.apagadaEn()).isEqualTo("chat.grupos");
        assertThat(evaluacion.evaluar("chat.grupos", foto, null, SIN_AUTOMATICOS).prendida()).isFalse();
    }

    @Test
    @DisplayName("apagada para APRENDIZ: el aprendiz la ve apagada, el mentor no y un proceso tampoco (US1-2)")
    void apagadaSoloParaUnRol() {
        FotoDeEstados foto = foto(estado("chat.grupos", true, Set.of(UserRole.TRAINEE), null));

        assertThat(evaluar("chat.grupos", foto, UserRole.TRAINEE).prendida()).isFalse();
        assertThat(evaluar("chat.grupos", foto, UserRole.TRAINEE).aviso())
                .isEqualTo(FuncionalidadApagadaException.AVISO_GENERICO);
        assertThat(evaluar("chat.grupos", foto, UserRole.MENTOR).prendida()).isTrue();
        assertThat(evaluacion.evaluar("chat.grupos", foto, null, SIN_AUTOMATICOS).prendida())
                .as("un proceso no tiene rol: solo lo apaga el estado global").isTrue();
    }

    @Test
    @DisplayName("apagar la madre apaga a las hijas, con el aviso de la madre (FR-002)")
    void laMadreApagaALasHijas() {
        FotoDeEstados foto = foto(estado("chat", false, Set.of(), "Chat en mantenimiento"));

        ResultadoDeEvaluacion resultado = evaluar("chat.audio", foto, UserRole.TRAINEE);

        assertThat(resultado.prendida()).isFalse();
        assertThat(resultado.apagadaEn()).isEqualTo("chat");
        assertThat(resultado.aviso()).isEqualTo("Chat en mantenimiento");
        assertThat(evaluar("muro", foto, UserRole.TRAINEE).prendida()).isTrue();
    }

    @Test
    @DisplayName("la madre apagada para un rol apaga a las hijas para ese rol")
    void laMadreApagadaParaUnRol() {
        FotoDeEstados foto = foto(estado("chat", true, Set.of(UserRole.TRAINEE), null));

        assertThat(evaluar("chat.grupos", foto, UserRole.TRAINEE).prendida()).isFalse();
        assertThat(evaluar("chat.grupos", foto, UserRole.ADMIN).prendida()).isTrue();
    }

    @Test
    @DisplayName("apagada para todos gana sobre una hija prendida")
    void apagadaParaTodosGana() {
        FotoDeEstados foto = foto(estado("chat", false, Set.of(), null), estado("chat.grupos", true, Set.of(), null));

        assertThat(evaluar("chat.grupos", foto, UserRole.ADMIN).prendida()).isFalse();
    }

    @Test
    @DisplayName("el rol se lee solo si hace falta: sin excepciones por rol, nadie lo consulta (FR-011)")
    void elRolSeLeeSoloSiHaceFalta() {
        AtomicInteger lecturas = new AtomicInteger();
        ActorEvaluado rol = new ActorEvaluado() {
            @Override
            public UserId id() {
                return APRENDIZ;
            }

            @Override
            public UserRole rol() {
                lecturas.incrementAndGet();
                return UserRole.TRAINEE;
            }
        };

        evaluacion.evaluar("chat.grupos", foto(estado("muro", true, Set.of(UserRole.TRAINEE), null)), rol,
                SIN_AUTOMATICOS);
        assertThat(lecturas).hasValue(0);

        evaluacion.evaluar("chat.grupos", foto(estado("chat", true, Set.of(UserRole.MENTOR), null)), rol,
                SIN_AUTOMATICOS);
        assertThat(lecturas).hasValue(1);
    }

    @Test
    @DisplayName("el circuito automático apaga una clave ia.* con su aviso; no toca las demás (contrato §5)")
    void elCircuitoApagaSoloLaIa() {
        Function<String, Optional<String>> circuito = clave -> clave.equals("ia.chat")
                ? Optional.of("SER está en pausa") : Optional.empty();

        ResultadoDeEvaluacion chat = evaluacion.evaluar("ia.chat", FotoDeEstados.vacia(), actor(UserRole.TRAINEE),
                circuito);

        assertThat(chat.prendida()).isFalse();
        assertThat(chat.automatico()).isTrue();
        assertThat(chat.aviso()).isEqualTo("SER está en pausa");
        assertThat(evaluacion.evaluar("ia.voz", FotoDeEstados.vacia(), actor(UserRole.TRAINEE), circuito).prendida())
                .isTrue();
        assertThat(evaluacion.evaluar("chat", FotoDeEstados.vacia(), actor(UserRole.TRAINEE),
                clave -> Optional.of("no debería mirarse")).prendida()).isTrue();
    }

    @Test
    @DisplayName("lo manual manda sobre el circuito: el aviso es el escrito por Soporte TI (FR-017)")
    void loManualMandaSobreElCircuito() {
        FotoDeEstados foto = foto(estado("ia", false, Set.of(), "Actualizando SER"));

        ResultadoDeEvaluacion resultado = evaluacion.evaluar("ia.chat", foto, actor(UserRole.TRAINEE),
                clave -> Optional.of("aviso automático"));

        assertThat(resultado.prendida()).isFalse();
        assertThat(resultado.automatico()).isFalse();
        assertThat(resultado.aviso()).isEqualTo("Actualizando SER");
    }

    @Test
    @DisplayName("una clave que no está en el catálogo no tiene interruptor: prendida")
    void claveFueraDelCatalogo() {
        assertThat(evaluar("login", foto(estado("chat", false, Set.of(), null)), UserRole.TRAINEE).prendida()).isTrue();
    }

    @Test
    @DisplayName("una funcionalidad que nace apagada está apagada sin fila, y prendida con su fila")
    void naceApagada() {
        CatalogoDeFuncionalidades catalogo = CatalogoDeFuncionalidades.de(List.of(new DefinicionDeFuncionalidad(
                "nueva", null, "Área", "Nueva", "Prueba", Set.of(), false, ValorPorDefecto.APAGADA,
                PoliticaDeProcesos.NO_APLICA)));
        EvaluacionDeFuncionalidades conNueva = new EvaluacionDeFuncionalidades(catalogo);

        assertThat(conNueva.evaluar("nueva", FotoDeEstados.vacia(), null, SIN_AUTOMATICOS).prendida()).isFalse();
        assertThat(conNueva.evaluar("nueva", foto(estado("nueva", true, Set.of(), null)), null, SIN_AUTOMATICOS)
                .prendida()).isTrue();
    }

    @Test
    @DisplayName("fase 2: una regla que deja fuera a la persona la vuelve no disponible, en el nivel que dice la regla")
    void unaReglaLaVuelveNoDisponible() {
        List<List<String>> cadenasVistas = new java.util.ArrayList<>();
        ReglaDeDisponibilidad soloParaOtros = (cadena, quien) -> {
            cadenasVistas.add(cadena);
            return cadena.contains("chat") ? Optional.of("chat") : Optional.empty();
        };

        ResultadoDeEvaluacion audio = evaluacion.evaluar("chat.audio", FotoDeEstados.vacia(), actor(UserRole.TRAINEE),
                SIN_AUTOMATICOS, List.of(soloParaOtros));

        assertThat(audio.prendida()).isFalse();
        assertThat(audio.noDisponible()).isTrue();
        assertThat(audio.apagadaEn()).isEqualTo("chat");
        assertThat(audio.aviso()).isEqualTo(com.renaser.os.shared.domain.FuncionalidadNoDisponibleException.MENSAJE);
        assertThat(cadenasVistas).containsExactly(List.of("chat", "chat.audio"));
        assertThat(evaluacion.evaluar("muro", FotoDeEstados.vacia(), actor(UserRole.TRAINEE), SIN_AUTOMATICOS,
                List.of(soloParaOtros)).prendida()).isTrue();
    }

    @Test
    @DisplayName("fase 2: el apagado manual gana sobre las reglas (es mantenimiento para todos, con su aviso)")
    void elApagadoGanaSobreLasReglas() {
        FotoDeEstados foto = foto(estado("chat", false, Set.of(), "Chat en mantenimiento"));
        ReglaDeDisponibilidad nadie = (cadena, quien) -> Optional.of(cadena.getLast());

        ResultadoDeEvaluacion resultado = evaluacion.evaluar("chat.grupos", foto, actor(UserRole.TRAINEE),
                SIN_AUTOMATICOS, List.of(nadie));

        assertThat(resultado.noDisponible()).isFalse();
        assertThat(resultado.aviso()).isEqualTo("Chat en mantenimiento");
    }

    @Test
    @DisplayName("fase 2: un proceso sin actor no ve las reglas, y la primera regla que deja fuera es la que responde")
    void reglasSoloParaPersonasYEnOrden() {
        AtomicInteger segundaConsultada = new AtomicInteger();
        ReglaDeDisponibilidad primera = (cadena, quien) -> Optional.of("muro");
        ReglaDeDisponibilidad segunda = (cadena, quien) -> {
            segundaConsultada.incrementAndGet();
            return Optional.empty();
        };

        assertThat(evaluacion.evaluar("muro", FotoDeEstados.vacia(), null, SIN_AUTOMATICOS, List.of(primera))
                .prendida()).as("un proceso no tiene persona: la regla no aplica").isTrue();
        assertThat(evaluacion.evaluar("muro", FotoDeEstados.vacia(), actor(UserRole.MENTOR), SIN_AUTOMATICOS,
                List.of(primera, segunda)).noDisponible()).isTrue();
        assertThat(segundaConsultada).hasValue(0);
        assertThat(evaluacion.evaluar("login", FotoDeEstados.vacia(), actor(UserRole.MENTOR), SIN_AUTOMATICOS,
                List.of(primera)).prendida()).as("fuera del catálogo no hay regla que valga").isTrue();
    }

    @Test
    @DisplayName("la versión de la foto es la mayor de sus filas")
    void versionDeLaFoto() {
        assertThat(foto(estado("chat", true, Set.of(), null, 7), estado("muro", true, Set.of(), null, 42)).version())
                .isEqualTo(42);
        assertThat(FotoDeEstados.vacia().version()).isZero();
    }

    private ResultadoDeEvaluacion evaluar(String clave, FotoDeEstados foto, UserRole rol) {
        return evaluacion.evaluar(clave, foto, actor(rol), SIN_AUTOMATICOS);
    }

    static ActorEvaluado actor(UserRole rol) {
        return new ActorEvaluado() {
            @Override
            public UserId id() {
                return APRENDIZ;
            }

            @Override
            public UserRole rol() {
                return rol;
            }
        };
    }

    static FotoDeEstados foto(EstadoDeFuncionalidad... estados) {
        return FotoDeEstados.de(List.of(estados));
    }

    static EstadoDeFuncionalidad estado(String clave, boolean prendida, Set<UserRole> apagadaPara, String aviso) {
        return estado(clave, prendida, apagadaPara, aviso, 1);
    }

    static EstadoDeFuncionalidad estado(String clave, boolean prendida, Set<UserRole> apagadaPara, String aviso,
                                        long version) {
        return new EstadoDeFuncionalidad(clave, prendida, apagadaPara, aviso, version, SOPORTE,
                Instant.parse("2026-10-13T22:00:00Z"));
    }

    @Test
    @DisplayName("la foto para la auditoría lista los roles apagados ordenados y el aviso")
    void fotoParaLaAuditoria() {
        Map<String, Object> foto = estado("chat", true, Set.of(UserRole.TRAINEE, UserRole.ADMIN), "x").paraAuditoria();

        assertThat(foto).containsEntry("prendidaGlobal", true).containsEntry("aviso", "x")
                .containsEntry("apagadaPara", List.of("ADMIN", "TRAINEE"));
    }
}
