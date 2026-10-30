package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * E-655: dos avisos de la misma persona en paralelo ven "sin conversacion" y los dos la crean; el segundo chocaba
 * con {@code conversaciones_renasia_pkey} y el mensaje de ese aviso se perdia. Aca la carrera se reduce a su
 * resultado: la lectura llega vieja (dice "no hay") y, cuando se crea, la conversacion YA existe. La prueba de
 * que la base lo aguanta con dos transacciones de verdad es {@code MensajeProactivoConcurrenteIT}.
 *
 * <p><b>Reloj a las 02:00 UTC</b> (regla 02 §3): en Lima es el dia anterior; este servicio no depende del dia, pero
 * el fixture no tiene por que esconder la hora.
 */
class MensajeProactivoDelAcompananteTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-23T02:00:00Z"));

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final ConversacionesComoLaBase conversaciones = new ConversacionesComoLaBase();
    private final List<MensajeRenasia> guardados = new ArrayList<>();
    private final SaveMensajeRenasiaPort saveMensajePort = mensaje -> {
        guardados.add(mensaje);
        return mensaje;
    };
    private final MensajeProactivoDelAcompanante proactivo = new MensajeProactivoDelAcompanante(
            mock(LoadMensajeRenasiaPort.class), saveMensajePort, conversaciones, conversaciones, CLOCK);

    @Test
    @DisplayName("sin conversacion la crea y escribe el mensaje")
    void sinConversacionLaCreaYEscribe() {
        proactivo.escribir(idDelMensaje(), aprendiz, "Se te vence Meditar");

        assertThat(conversaciones.filas).containsOnlyKeys(aprendiz);
        assertThat(guardados).singleElement().satisfies(mensaje ->
                assertThat(mensaje.contenido()).isEqualTo("Se te vence Meditar"));
    }

    @Test
    @DisplayName("con la conversacion ya creada por otro aviso a la vez, igual escribe el mensaje")
    void escribeElMensajeAunqueLaConversacionYaExista() {
        // El otro aviso la creo entre mi lectura y mi creacion: mi lectura llega vieja y dice "no hay".
        conversaciones.filas.put(aprendiz, ConversacionRenasia.iniciar(aprendiz, CLOCK.now()));
        conversaciones.lecturaVieja = true;

        proactivo.escribir(idDelMensaje(), aprendiz, "Se te vence Meditar");

        assertThat(guardados).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.usuarioId()).isEqualTo(aprendiz);
            assertThat(mensaje.contenido()).isEqualTo("Se te vence Meditar");
        });
        assertThat(conversaciones.filas).hasSize(1);
    }

    @Test
    @DisplayName("con la conversacion ya creada y bien leida no vuelve a crearla")
    void conConversacionExistenteNoLaCreaOtraVez() {
        conversaciones.filas.put(aprendiz, ConversacionRenasia.iniciar(aprendiz, CLOCK.now()));

        proactivo.escribir(idDelMensaje(), aprendiz, "Se te vence Meditar");

        assertThat(conversaciones.intentosDeCrear).isZero();
        assertThat(guardados).hasSize(1);
    }

    private static MensajeRenasiaId idDelMensaje() {
        return MensajeRenasiaId.of(UUID.randomUUID());
    }

    /**
     * Se porta como la base: {@code save} con la clave ya tomada revienta (es lo que pasaba bajo carrera) y
     * {@code crearSiFalta} no. {@code lecturaVieja} hace que la lectura diga "no hay" aunque la fila exista.
     */
    private static final class ConversacionesComoLaBase implements LoadConversacionRenasiaPort,
            SaveConversacionRenasiaPort {

        final Map<UserId, ConversacionRenasia> filas = new HashMap<>();
        boolean lecturaVieja;
        int intentosDeCrear;

        @Override
        public Optional<ConversacionRenasia> porUsuarioId(UserId usuarioId) {
            return lecturaVieja ? Optional.empty() : Optional.ofNullable(filas.get(usuarioId));
        }

        @Override
        public ConversacionRenasia save(ConversacionRenasia conversacion) {
            intentosDeCrear++;
            if (filas.putIfAbsent(conversacion.usuarioId(), conversacion) != null) {
                throw new IllegalStateException(
                        "duplicate key value violates unique constraint \"conversaciones_renasia_pkey\"");
            }
            return conversacion;
        }

        @Override
        public void crearSiFalta(ConversacionRenasia conversacion) {
            intentosDeCrear++;
            filas.putIfAbsent(conversacion.usuarioId(), conversacion);
        }
    }
}
