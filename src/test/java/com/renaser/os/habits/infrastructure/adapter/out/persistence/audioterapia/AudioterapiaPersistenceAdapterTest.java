package com.renaser.os.habits.infrastructure.adapter.out.persistence.audioterapia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.out.audioterapia.AudioterapiaCatalogPort.Audioterapia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Corre contra las 13 filas reales sembradas por V5 (mismos ids/semanas que produccion) — sin
 * volver a insertar fixtures propias: es el mismo catalogo que ya viaja en toda migracion.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AudioterapiaPersistenceAdapterTest {

    @Autowired
    private AudioterapiaPersistenceAdapter adapter;

    @Test
    void porSemanaTraeLaAudioterapiaSembradaPorLaMigracion() {
        Optional<Audioterapia> resultado = adapter.porSemana(1);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().titulo()).isEqualTo("Despierta tu potencial infinito");
        assertThat(resultado.get().duracionDias()).isEqualTo(7); // default de V8
    }

    @Test
    void porSemanaInexistenteEsVacio() {
        assertThat(adapter.porSemana(99)).isEmpty();
    }

    @Test
    void todasOrdenadasTraeLas13SemanasEnOrden() {
        List<Audioterapia> catalogo = adapter.todasOrdenadas();

        assertThat(catalogo).hasSize(13);
        assertThat(catalogo).extracting(Audioterapia::semana).isSorted();
        assertThat(catalogo.getFirst().semana()).isEqualTo(1);
        assertThat(catalogo.getLast().semana()).isEqualTo(13);
    }

    @Test
    void actualizarDuracionPersisteElCambio() {
        Audioterapia actualizada = adapter.actualizarDuracion(2, 10);

        assertThat(actualizada.duracionDias()).isEqualTo(10);
        assertThat(adapter.porSemana(2)).isPresent().get()
                .extracting(Audioterapia::duracionDias).isEqualTo(10);
    }

    @Test
    void actualizarDuracionDeSemanaInexistenteFalla() {
        assertThatThrownBy(() -> adapter.actualizarDuracion(99, 10)).isInstanceOf(NoSuchElementException.class);
    }
}
