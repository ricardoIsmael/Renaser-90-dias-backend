package com.renaser.os.rag.infrastructure.adapter.out.persistence.memoria;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.memoria.MemoriaDeRenasiaPort;
import com.renaser.os.rag.domain.model.memoria.CategoriaDeRecuerdo;
import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;
import com.renaser.os.rag.domain.model.memoria.Recuerdo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code recuerdos_renasia} y {@code memorias_renasia} (V67) contra Postgres real: los CHECK, que lo
 * que la persona borra no vuelve con una compactacion que corria al mismo tiempo, que
 * {@code compactado_hasta} no retrocede, y que la memoria cae con la cuenta.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MemoriaDeRenasiaPersistenceAdapterIT {

    private static final Instant ANTES = Instant.parse("2026-09-20T15:00:00Z");
    private static final Instant DESPUES = Instant.parse("2026-09-22T15:00:00Z");

    @Autowired
    private MemoriaDeRenasiaPort port;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> sembrados = new ArrayList<>();
    private UserId participante;

    @BeforeEach
    void seedParticipante() {
        participante = sembrar();
    }

    private UserId sembrar() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture memoria', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test");
        sembrados.add(id);
        return UserId.of(id);
    }

    @AfterEach
    void limpiar() {
        sembrados.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    private static Recuerdo recuerdo(CategoriaDeRecuerdo categoria, String texto) {
        return new Recuerdo(UUID.randomUUID(), categoria, texto, ANTES);
    }

    private MemoriaDeRenasia guardada(Recuerdo... recuerdos) {
        var memoria = new MemoriaDeRenasia(List.of(recuerdos), Optional.of("Armaron su rutina."), ANTES);
        assertThat(port.reemplazar(participante, MemoriaDeRenasia.vacia(), memoria)).isTrue();
        return memoria;
    }

    @Test
    @DisplayName("ida y vuelta: sin memoria es la vacia; guardada, vuelve igual")
    void idaYVuelta() {
        assertThat(port.de(participante)).isEqualTo(MemoriaDeRenasia.vacia());

        var memoria = guardada(recuerdo(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche"),
                recuerdo(CategoriaDeRecuerdo.PREFERENCIAS_DE_TRATO, "Prefiere respuestas cortas"));

        assertThat(port.de(participante)).isEqualTo(memoria);
    }

    @Test
    @DisplayName("al compactar, lo que sigue igual no se toca, lo que sale se borra y lo nuevo se agrega")
    void reemplazarPorDiferencia() {
        var queda = recuerdo(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche");
        var sale = recuerdo(CategoriaDeRecuerdo.METAS_Y_LO_QUE_FUNCIONA, "Quiere dormir mas");
        var antes = guardada(queda, sale);
        var nueva = new MemoriaDeRenasia(List.of(queda,
                recuerdo(CategoriaDeRecuerdo.METAS_Y_LO_QUE_FUNCIONA, "Camina antes del trabajo")),
                Optional.of("Hablaron de caminar."), DESPUES);

        assertThat(port.reemplazar(participante, antes, nueva)).isTrue();

        assertThat(port.de(participante)).isEqualTo(nueva);
    }

    @Test
    @DisplayName("lo borrado no vuelve: una compactacion hecha sobre la memoria vieja ya no se guarda")
    void loBorradoNoVuelve() {
        var trabaja = recuerdo(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche");
        var antes = guardada(trabaja, recuerdo(CategoriaDeRecuerdo.PREFERENCIAS_DE_TRATO, "Respuestas cortas"));

        // La compactacion leyo `antes` y el modelo tarda; mientras, la persona borra un recuerdo.
        assertThat(port.olvidarRecuerdo(participante, trabaja.id())).isTrue();
        boolean guardo = port.reemplazar(participante, antes,
                new MemoriaDeRenasia(antes.recuerdos(), Optional.of("Trabaja de noche."), DESPUES));

        assertThat(guardo).isFalse();
        MemoriaDeRenasia quedo = port.de(participante);
        assertThat(quedo.recuerdos()).extracting(Recuerdo::texto).containsExactly("Respuestas cortas");
        assertThat(quedo.resumen()).as("el resumen podia nombrar lo borrado").isEmpty();
        assertThat(quedo.compactadoHasta()).isEqualTo(ANTES);
    }

    @Test
    @DisplayName("nadie olvida lo ajeno: el recuerdo de otra persona no se borra")
    void olvidarAjeno() {
        var trabaja = recuerdo(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche");
        guardada(trabaja);

        assertThat(port.olvidarRecuerdo(sembrar(), trabaja.id())).isFalse();
        assertThat(port.de(participante).recuerdos()).containsExactly(trabaja);
    }

    @Test
    @DisplayName("olvidar todo borra recuerdos y resumen, y compactado_hasta avanza pero no retrocede")
    void olvidarTodo() {
        guardada(recuerdo(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche"));

        port.olvidarTodo(participante, ANTES.minusSeconds(3600));
        assertThat(port.de(participante)).isEqualTo(new MemoriaDeRenasia(List.of(), Optional.empty(), ANTES));

        port.olvidarTodo(participante, DESPUES);
        assertThat(port.de(participante).compactadoHasta()).isEqualTo(DESPUES);
    }

    @Test
    @DisplayName("olvidar todo sin nada guardado igual deja marcado hasta donde: lo dicho antes no se aprende")
    void olvidarTodoSinFila() {
        port.olvidarTodo(participante, DESPUES);

        assertThat(port.de(participante)).isEqualTo(new MemoriaDeRenasia(List.of(), Optional.empty(), DESPUES));
    }

    @Test
    @DisplayName("los CHECK rechazan una categoria desconocida, un recuerdo largo y un resumen vacio")
    void checks() {
        assertThatThrownBy(() -> insertarRecuerdo("EMOCIONES", "Se siente bien"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarRecuerdo("CONTEXTO_DE_VIDA", "x".repeat(301)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO renaser.memorias_renasia (participante_id, resumen, compactado_hasta, actualizado_en)
                VALUES (?, '', now(), now())
                """, participante.value())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la memoria cae con la cuenta")
    void caeConLaCuenta() {
        guardada(recuerdo(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche"));

        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participante.value());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.recuerdos_renasia WHERE participante_id = ?",
                Integer.class, participante.value())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.memorias_renasia WHERE participante_id = ?",
                Integer.class, participante.value())).isZero();
    }

    private void insertarRecuerdo(String categoria, String texto) {
        jdbcTemplate.update("""
                INSERT INTO renaser.recuerdos_renasia (id, participante_id, categoria, texto, creado_en)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), participante.value(), categoria, texto, Timestamp.from(ANTES));
    }
}
