package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.phasecontracts.application.ports.in.animal.AnimalDeFaseVista;
import com.renaser.os.phasecontracts.application.ports.out.animal.AnimalesDeFasePort;
import com.renaser.os.phasecontracts.application.ports.out.animal.ImagenDeAnimalPort;
import com.renaser.os.phasecontracts.domain.model.animal.AnimalDeFase;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnimalesDeFaseServiceTest {

    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final AnimalesDeFasePort animales = mock(AnimalesDeFasePort.class);
    private final ImagenDeAnimalPort imagenes = mock(ImagenDeAnimalPort.class);
    private final AlmacenamientoPort almacenamiento = mock(AlmacenamientoPort.class);
    private final IdGenerator ids = mock(IdGenerator.class);
    private final FixedClock clock = FixedClock.at(Instant.parse("2026-10-06T03:00:00Z"));
    private GuardiaDeAnimalesDeFase guardia;
    private AnimalesDeFaseParaMostrar paraMostrar;
    private CambiarImagenDeAnimalService imagen;
    private RestaurarImagenDeAnimalService restaurar;
    private CambiarNombreDeAnimalService nombre;

    @BeforeEach
    void armar() {
        guardia = new GuardiaDeAnimalesDeFase(usuarios);
        paraMostrar = new AnimalesDeFaseParaMostrar(animales, almacenamiento);
        imagen = new CambiarImagenDeAnimalService(guardia, animales, imagenes, paraMostrar, almacenamiento, ids, clock);
        restaurar = new RestaurarImagenDeAnimalService(guardia, animales, paraMostrar, clock);
        nombre = new CambiarNombreDeAnimalService(guardia, animales, paraMostrar, clock);
        when(animales.todos()).thenReturn(Arrays.stream(FasePrograma.values()).map(AnimalDeFase::sinPersonalizar).toList());
        when(animales.porFase(any())).thenAnswer(i -> AnimalDeFase.sinPersonalizar(i.getArgument(0)));
        when(almacenamiento.guardaObjetos()).thenReturn(true);
    }

    private UserId cuenta(UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Ana", null, rol, estado)));
        return id;
    }

    @Test
    @DisplayName("ADMIN y ALCHEMIST activos cambian el nombre y se guarda con su id")
    void cambiaNombre() {
        for (UserRole rol : List.of(UserRole.ADMIN, UserRole.ALCHEMIST)) {
            UserId actor = cuenta(rol, UserStatus.ACTIVE);
            List<AnimalDeFaseVista> r = nombre.cambiar(actor, 2, "Gorila de montaña");
            assertThat(r).hasSize(4);
        }
        verify(animales, org.mockito.Mockito.times(2)).guardar(any());
    }

    @Test
    @DisplayName("403 para TRAINEE, MENTOR, MENTOR_LEAD y un ADMIN suspendido, y no se guarda nada")
    void sinPermiso() {
        for (UserRole rol : List.of(UserRole.TRAINEE, UserRole.MENTOR, UserRole.MENTOR_LEAD)) {
            UserId actor = cuenta(rol, UserStatus.ACTIVE);
            assertThatThrownBy(() -> nombre.cambiar(actor, 1, "X")).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> restaurar.restaurar(actor, 1)).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> imagen.solicitarSubida(actor, 1, "image/png")).isInstanceOf(NotAuthorizedException.class);
        }
        UserId suspendido = cuenta(UserRole.ADMIN, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> nombre.cambiar(suspendido, 1, "X")).isInstanceOf(NotAuthorizedException.class);
        verify(animales, never()).guardar(any());
    }

    @Test
    @DisplayName("la fase tiene que existir (1 a 4) y el tipo ser PNG o WebP")
    void validaAntesDeFirmar() {
        UserId actor = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        assertThatThrownBy(() -> imagen.solicitarSubida(actor, 5, "image/png")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> imagen.solicitarSubida(actor, 1, "image/jpeg")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("subir: la ruta lleva la fase y un id nuevo; confirmar revisa y guarda la ruta")
    void subirYConfirmar() {
        UserId actor = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(ids.newId()).thenReturn(id);
        when(almacenamiento.firmarSubida(any(), any(), any())).thenReturn(URI.create("https://s3/subida"));
        var subida = imagen.solicitarSubida(actor, 3, "image/webp");
        assertThat(subida.ruta()).isEqualTo("fases/animales/3/" + id);

        imagen.confirmar(actor, 3, subida.ruta());
        verify(imagenes).revisar("fases/animales/3/" + id);
        verify(animales).guardar(any());
    }

    @Test
    @DisplayName("si la imagen no pasa la revisión no se guarda; sin almacenamiento de verdad, 409")
    void revisionYAlmacenamiento() {
        UserId actor = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("muy chica")).when(imagenes).revisar(any());
        assertThatThrownBy(() -> imagen.confirmar(actor, 1, "fases/animales/1/a")).hasMessage("muy chica");
        when(almacenamiento.guardaObjetos()).thenReturn(false);
        assertThatThrownBy(() -> imagen.confirmar(actor, 1, "fases/animales/1/a")).isInstanceOf(IllegalStateException.class);
        verify(animales, never()).guardar(any());
    }

    @Test
    @DisplayName("restaurar sin imagen propia no escribe; con imagen propia la quita")
    void restaurarImagen() {
        UserId actor = cuenta(UserRole.ALCHEMIST, UserStatus.ACTIVE);
        restaurar.restaurar(actor, 1);
        verify(animales, never()).guardar(any());
        when(animales.porFase(FasePrograma.FASE_1_RENACER)).thenReturn(AnimalDeFase.rehidratar(
                FasePrograma.FASE_1_RENACER, null, "fases/animales/1/a", null, null));
        restaurar.restaurar(actor, 1);
        verify(animales).guardar(any());
    }
}
