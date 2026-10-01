package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarFichaDeMentorUseCase.FichaDeMentor;
import com.renaser.os.leadership.application.ports.in.ConsultarObservacionesUseCase.PaginaDeObservaciones;
import com.renaser.os.leadership.application.ports.in.RegistrarObservacionUseCase.RegistrarObservacionCommand;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.leadership.domain.model.observacion.TipoObservacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.ANA;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.LIDER;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.LUISA;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.RAUL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La ficha de un mentor y las observaciones del líder (SDD 002, RL-06/RL-08/RL-15/RL-16/RL-18; D-241). */
class FichaYObservacionesServiceTest {

    private final BancoDeLiderazgo banco = new BancoDeLiderazgo();
    private ObservacionesDeMentorService observaciones;

    @BeforeEach
    void preparar() {
        PadronDeMentoresServiceTest.prepararCuerpo(banco);
        observaciones = banco.observacionesService();
    }

    private static RegistrarObservacionCommand comando(UserId actor, UserId mentor, String clave) {
        return new RegistrarObservacionCommand(actor, mentor, "SUGERENCIA", "Las consultas del fin de semana",
                false, null, clave);
    }

    @Test
    @DisplayName("la ficha trae perfil, indicadores y lo ultimo que el lider le dijo")
    void ficha() {
        observaciones.registrar(comando(LIDER, LUISA, "k1"));

        FichaDeMentor ficha = banco.ficha().ficha(LIDER, LUISA);

        assertThat(ficha.indicadores().nombre()).isEqualTo("Luisa Rojas");
        assertThat(ficha.perfil().nivel()).isEqualTo("N1");
        assertThat(ficha.cuentaActiva()).isTrue();
        assertThat(ficha.ultimasObservaciones()).singleElement()
                .satisfies(o -> assertThat(o.tipo()).isEqualTo(TipoObservacion.SUGERENCIA));
    }

    @Test
    @DisplayName("RL-08: un id que no es de un mentor (un aprendiz, uno que no existe) es 404, no una ficha vacia")
    void fichaDeNoMentor() {
        assertThatThrownBy(() -> banco.ficha().ficha(LIDER, ANA)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> banco.ficha().ficha(LIDER, UserId.of(UUID.randomUUID())))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("un MENTOR no abre la ficha de otro: 403 antes de saber si el id existe")
    void mentorNoAbreFichas() {
        assertThatThrownBy(() -> banco.ficha().ficha(RAUL, LUISA)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> banco.ficha().ficha(RAUL, UserId.of(UUID.randomUUID())))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("RL-15: se registra con tipo, autor, texto, envio y fecha")
    void registra() {
        UUID mensaje = UUID.randomUUID();
        ObservacionDeMentor o = observaciones.registrar(new RegistrarObservacionCommand(LIDER, LUISA, "RECONOCIMIENTO",
                "Muy buena respuesta a Pía", true, mensaje, "k1"));

        assertThat(o.autorId()).isEqualTo(LIDER);
        assertThat(o.mentorId()).isEqualTo(LUISA);
        assertThat(o.enviadaPorChat()).isTrue();
        assertThat(o.mensajeId()).isEqualTo(mensaje);
        assertThat(o.creadoEn()).isEqualTo(BancoDeLiderazgo.AHORA);
        assertThat(banco.observaciones).hasSize(1);
    }

    @Test
    @DisplayName("repetir la misma clave no crea dos; la misma clave para otro mentor es un error")
    void idempotente() {
        ObservacionDeMentor primera = observaciones.registrar(comando(LIDER, LUISA, "k1"));
        ObservacionDeMentor segunda = observaciones.registrar(comando(LIDER, LUISA, "k1"));

        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(banco.observaciones).hasSize(1);
        assertThatThrownBy(() -> observaciones.registrar(comando(LIDER, RAUL, "k1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RL-18: no se observa a quien no es mentor")
    void soloSobreMentores() {
        assertThatThrownBy(() -> observaciones.registrar(comando(LIDER, ANA, "k1")))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(banco.observaciones).isEmpty();
    }

    @Test
    @DisplayName("RL-26: un MENTOR no registra observaciones; un lider suspendido tampoco")
    void autorizacionNegativa() {
        assertThatThrownBy(() -> observaciones.registrar(comando(RAUL, LUISA, "k1")))
                .isInstanceOf(NotAuthorizedException.class);
        banco.usuario(LIDER, "Líder", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> observaciones.registrar(comando(LIDER, LUISA, "k2")))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(banco.observaciones).isEmpty();
    }

    @Test
    @DisplayName("la lista es solo de ese mentor, de a paginas")
    void lista() {
        observaciones.registrar(comando(LIDER, LUISA, "k1"));
        observaciones.registrar(comando(LIDER, RAUL, "k2"));

        PaginaDeObservaciones pagina = observaciones.observaciones(LIDER, LUISA, null);

        assertThat(pagina.observaciones()).singleElement().satisfies(o -> assertThat(o.mentorId()).isEqualTo(LUISA));
        assertThat(pagina.siguiente()).isNull();
    }
}
