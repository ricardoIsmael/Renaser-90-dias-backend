package com.renaser.os.chat.infrastructure.adapter.in.rest.bienvenida;

import com.renaser.os.chat.application.ports.in.bienvenida.AdministrarImagenDeLaFormacionUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.AdministrarImagenDeLaFormacionUseCase.ImagenEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.UltimoCambio;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * La imagen de cada formación en el chat, editable desde la app por Administración y Alquimista (spec 035): la de su comunidad general y la de sus grupos sin foto
 * propia. Mismo camino en tres pasos que la portada de la bienvenida: URL prefirmada, el teléfono sube, se confirma la ruta.
 *
 * <p>El código de la formación va en la ruta: uno que no existe es un 404 (el recurso no existe). Los cuerpos no llevan {@code @Valid}: lo que se valida
 * (tipo de imagen, ruta) lo valida el dominio, con mensajes que la app muestra tal cual, y DESPUÉS de saber si quien pide puede (403 antes que 400).
 */
@RestController
@RequestMapping("/api/v1/admin/formaciones/{codigo}/imagen")
@Funcionalidad("chat")
public class ImagenDeLaFormacionAdminController {

    private static final String SOLO_ADMIN = "solo ADMIN/ALCHEMIST activos (spec 035): el servicio rechaza a los demás roles y a las cuentas suspendidas";

    private final AdministrarImagenDeLaFormacionUseCase imagen;

    public ImagenDeLaFormacionAdminController(AdministrarImagenDeLaFormacionUseCase imagen) {
        this.imagen = imagen;
    }

    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @GetMapping
    public ImagenDeLaFormacionResponse ver(@ActorAutenticado UserId actorId, @PathVariable String codigo) {
        return ImagenDeLaFormacionResponse.from(imagen.ver(actorId, Formacion.deLaRuta(codigo)));
    }

    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @PostMapping("/upload-url")
    public UrlDeSubidaResponse solicitarSubida(@ActorAutenticado UserId actorId, @PathVariable String codigo,
                                               @RequestBody SolicitarSubidaDePortadaRequest request) {
        Formacion.deLaRuta(codigo);
        return UrlDeSubidaResponse.from(imagen.solicitarSubida(actorId, request.contentType()));
    }

    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @PostMapping("/confirm")
    public ImagenDeLaFormacionResponse confirmar(@ActorAutenticado UserId actorId, @PathVariable String codigo,
                                                 @RequestBody ConfirmarPortadaRequest request) {
        return ImagenDeLaFormacionResponse.from(imagen.confirmar(actorId, Formacion.deLaRuta(codigo), request.ruta()));
    }

    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @DeleteMapping
    public ImagenDeLaFormacionResponse volverALaOriginal(@ActorAutenticado UserId actorId, @PathVariable String codigo) {
        return ImagenDeLaFormacionResponse.from(imagen.volverALaOriginal(actorId, Formacion.deLaRuta(codigo)));
    }

    /**
     * La imagen tal como se sirve en el chat: la vigente o la candidata {@code ruta} recién subida. Sin caché en el teléfono: es una vista previa de algo que se
     * está cambiando. 404 si no hay ninguna imagen subida y no se pidió una candidata (la app muestra la suya).
     */
    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @GetMapping("/vista")
    public ResponseEntity<byte[]> vista(@ActorAutenticado UserId actorId, @PathVariable String codigo,
                                        @RequestParam(required = false) String ruta) {
        byte[] jpeg = imagen.vista(actorId, Formacion.deLaRuta(codigo), ruta);
        if (jpeg == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(jpeg);
    }

    /** @param ultimoCambio {@code null} si nunca se cambió */
    record ImagenDeLaFormacionResponse(String formation, boolean hayImagen, boolean cambiada, boolean heredada, boolean sePuedeCambiar,
                                       UltimoCambioDeLaImagen ultimoCambio) {

        static ImagenDeLaFormacionResponse from(ImagenEditable imagen) {
            return new ImagenDeLaFormacionResponse(imagen.formacion().codigo(), imagen.hayImagen(), imagen.cambiada(), imagen.heredada(),
                    imagen.sePuedeCambiar(), UltimoCambioDeLaImagen.from(imagen.ultimoCambio()));
        }
    }

    /** @param por {@code null} si la cuenta de quien lo cambió ya no existe */
    record UltimoCambioDeLaImagen(String por, Instant en, boolean volvioAlOriginal) {

        static UltimoCambioDeLaImagen from(UltimoCambio cambio) {
            return cambio == null ? null : new UltimoCambioDeLaImagen(cambio.por(), cambio.en(), cambio.volvioAlOriginal());
        }
    }
}
