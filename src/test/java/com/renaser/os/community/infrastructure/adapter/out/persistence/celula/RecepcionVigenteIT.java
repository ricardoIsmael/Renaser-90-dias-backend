package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.application.ports.out.celula.ConsultarRecepcionVigentePort;
import com.renaser.os.community.domain.model.celula.CelulaId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La consulta del grupo de bienvenida, contra Postgres real.
 *
 * <p><b>Por que existe esta clase (E-180).</b> El servicio que la consume,
 * {@code IngresoARecepcionService}, se prueba con un doble del puerto — asi que su SQL no se
 * ejecutaba nunca. Y el metodo estaba roto: un literal de enum en JPQL sobre una columna
 * {@code NAMED_ENUM} hacia que Hibernate generara {@code cast(? as tipocelula)} cuando el tipo de
 * Postgres se llama {@code renaser.tipo_celula}, asi que cada llamada moria.
 *
 * <p>Lo peor no fue el fallo sino que era <b>invisible</b>: "no hay recepcion vigente" es un
 * resultado legitimo de este puerto, y la excepcion producia exactamente esa misma consecuencia
 * observable. La automatica no metia a nadie y el log decia lo mismo que si el administrador
 * todavia no hubiera abierto la bienvenida.
 *
 * <p>La leccion, escrita como prueba: <b>cuando "no hay nada" es un resultado valido, el adaptador
 * necesita prueba propia contra la base</b>. El doble del puerto nunca ejecuta el SQL.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RecepcionVigenteIT {

    @Autowired
    private ConsultarRecepcionVigentePort puerto;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID cohorteId;
    private final List<UUID> celulas = new ArrayList<>();

    @BeforeEach
    void seed() {
        celulas.clear();
        cohorteId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.cohortes (id, nombre, fecha_inicio)
                VALUES (?, 'Cohorte recepcion', CURRENT_DATE - 30)
                """, cohorteId);
    }

    @AfterEach
    void limpiar() {
        celulas.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
    }

    @Test
    @DisplayName("Encuentra la bienvenida cuyo periodo contiene el dia, y no la que ya cerro")
    void encuentraLaRecepcionVigente() {
        UUID vigente = recepcion("Bienvenida de esta semana", -2, 4);
        recepcion("Bienvenida del mes pasado", -40, -33);

        Optional<CelulaId> encontrada = puerto.recepcionVigenteEn(LocalDate.now());

        // Contra el codigo anterior esto no fallaba con "vacio": reventaba con PSQLException.
        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().value()).isEqualTo(vigente);
    }

    @Test
    @DisplayName("Con varias bienvenidas abiertas gana la que empezo mas tarde")
    void conVariasGanaLaMasReciente() {
        recepcion("Abrio hace una semana", -7, 3);
        UUID laMasNueva = recepcion("Abrio ayer", -1, 6);

        assertThat(puerto.recepcionVigenteEn(LocalDate.now()))
                .map(CelulaId::value)
                .contains(laMasNueva);
    }

    @Test
    @DisplayName("Un grupo REGULAR con periodo abierto NO es una bienvenida")
    void unGrupoRegularNoCuenta() {
        regular("Grupo estable de septiembre", -5, 25);

        assertThat(puerto.recepcionVigenteEn(LocalDate.now())).isEmpty();
    }

    @Test
    @DisplayName("Sin ninguna abierta devuelve vacio — y eso es un resultado, no un error")
    void sinRecepcionDevuelveVacio() {
        recepcion("Cerro anteayer", -9, -2);

        assertThat(puerto.recepcionVigenteEn(LocalDate.now())).isEmpty();
    }

    @Test
    @DisplayName("Una recepcion SIN fechas es permanente: siempre vigente")
    void recepcionSinFechasEsPermanente() {
        UUID permanente = recepcionSinPeriodo("Bienvenida permanente");

        assertThat(puerto.recepcionVigenteEn(LocalDate.now()))
                .map(CelulaId::value)
                .contains(permanente);
    }

    @Test
    @DisplayName("Una recepcion fechada y vigente hoy gana sobre la permanente")
    void laFechadaVigenteGanaSobreLaPermanente() {
        recepcionSinPeriodo("Bienvenida permanente");
        UUID especial = recepcion("Bienvenida de un evento", -1, 5);

        assertThat(puerto.recepcionVigenteEn(LocalDate.now()))
                .map(CelulaId::value)
                .contains(especial);
    }

    private UUID recepcion(String nombre, int desde, int hasta) {
        return celula(nombre, "RECEPCION", desde, hasta);
    }

    private UUID recepcionSinPeriodo(String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, ?, ?, CAST('RECEPCION' AS renaser.tipo_celula), NULL, NULL)
                """, id, nombre, cohorteId);
        celulas.add(id);
        return id;
    }

    private UUID regular(String nombre, int desde, int hasta) {
        return celula(nombre, "REGULAR", desde, hasta);
    }

    private UUID celula(String nombre, String tipo, int desde, int hasta) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, ?, ?, CAST(? AS renaser.tipo_celula), CURRENT_DATE + ?, CURRENT_DATE + ?)
                """, id, nombre, cohorteId, tipo, desde, hasta);
        celulas.add(id);
        return id;
    }
}
