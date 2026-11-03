package com.renaser.os.chat.infrastructure.adapter.in.rest.bienvenida;

import com.renaser.os.chat.application.ports.in.bienvenida.CambiarPortadaDeBienvenidaUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.CambiarTextoDeBienvenidaUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.VerBienvenidaUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.VerTarjetaDeMuestraUseCase;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La bienvenida automática, editable desde la app por Administración y Alquimista (D-210): sus tres
 * mensajes y la portada de la tarjeta. Todo responde la bienvenida entera ({@link BienvenidaResponse}),
 * salvo la URL de subida y la vista previa de la tarjeta (un JPEG).
 *
 * <p><b>De una formación</b> (spec 035): cada ruta acepta {@code ?formation=ESCUELA|DIPLOMADO|CERTIFICACION}. Sin él es la Escuela: la app anterior sigue
 * editando lo de siempre sin saber que hay otras. Un código que no existe es un 400.
 *
 * <p>Los cuerpos no llevan {@code @Valid}: lo que se valida (texto, tipo de imagen, ruta) lo valida el
 * dominio, con mensajes que la app muestra tal cual, y DESPUÉS de saber si quien pide puede (403 antes
 * que 400).
 */
@RestController
@RequestMapping("/api/v1/admin/bienvenida")
@Funcionalidad("admin.bienvenida")
public class BienvenidaAdminController {

    private static final String SOLO_ADMIN = "solo ADMIN/ALCHEMIST activos (D-210): el servicio rechaza a los "
            + "demás roles y a las cuentas suspendidas";

    private final VerBienvenidaUseCase verUseCase;
    private final CambiarTextoDeBienvenidaUseCase textoUseCase;
    private final CambiarPortadaDeBienvenidaUseCase portadaUseCase;
    private final VerTarjetaDeMuestraUseCase muestraUseCase;

    public BienvenidaAdminController(VerBienvenidaUseCase verUseCase, CambiarTextoDeBienvenidaUseCase textoUseCase,
                                     CambiarPortadaDeBienvenidaUseCase portadaUseCase,
                                     VerTarjetaDeMuestraUseCase muestraUseCase) {
        this.verUseCase = verUseCase;
        this.textoUseCase = textoUseCase;
        this.portadaUseCase = portadaUseCase;
        this.muestraUseCase = muestraUseCase;
    }

    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @GetMapping
    public BienvenidaResponse ver(@ActorAutenticado UserId actorId, @RequestParam(required = false) String formation) {
        return BienvenidaResponse.from(verUseCase.ver(actorId, formacionDe(formation)));
    }

    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @PutMapping("/textos/{clave}")
    public BienvenidaResponse cambiarTexto(@ActorAutenticado UserId actorId, @PathVariable String clave,
                                           @RequestParam(required = false) String formation,
                                           @RequestBody CambiarTextoRequest request) {
        return BienvenidaResponse.from(textoUseCase.cambiar(actorId, formacionDe(formation), clave, request.texto()));
    }

    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @DeleteMapping("/textos/{clave}")
    public BienvenidaResponse volverAlTextoOriginal(@ActorAutenticado UserId actorId, @PathVariable String clave,
                                                    @RequestParam(required = false) String formation) {
        return BienvenidaResponse.from(textoUseCase.volverAlOriginal(actorId, formacionDe(formation), clave));
    }

    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @PostMapping("/portada/upload-url")
    public UrlDeSubidaResponse solicitarSubidaDePortada(@ActorAutenticado UserId actorId,
                                                         @RequestBody SolicitarSubidaDePortadaRequest request) {
        return UrlDeSubidaResponse.from(portadaUseCase.solicitarSubida(actorId, request.contentType()));
    }

    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @PostMapping("/portada/confirm")
    public BienvenidaResponse confirmarPortada(@ActorAutenticado UserId actorId,
                                               @RequestParam(required = false) String formation,
                                               @RequestBody ConfirmarPortadaRequest request) {
        return BienvenidaResponse.from(portadaUseCase.confirmar(actorId, formacionDe(formation), request.ruta()));
    }

    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @DeleteMapping("/portada")
    public BienvenidaResponse volverALaPortadaOriginal(@ActorAutenticado UserId actorId,
                                                       @RequestParam(required = false) String formation) {
        return BienvenidaResponse.from(portadaUseCase.volverALaOriginal(actorId, formacionDe(formation)));
    }

    /**
     * La tarjeta con un nombre de ejemplo, sobre la portada vigente o sobre la candidata {@code portada}
     * recién subida. Sin caché en el teléfono: es una vista previa de algo que se está cambiando.
     */
    @RequiresPermission(value = Permission.MANAGE_WELCOME, scope = SOLO_ADMIN)
    @GetMapping("/tarjeta")
    public ResponseEntity<byte[]> tarjeta(@ActorAutenticado UserId actorId,
                                          @RequestParam(required = false) String formation,
                                          @RequestParam(required = false) String nombre,
                                          @RequestParam(required = false) String portada) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(muestraUseCase.muestra(actorId, formacionDe(formation), nombre, portada));
    }

    /** Sin {@code formation}, la Escuela (la app anterior); un código que no existe, 400. */
    static Formacion formacionDe(String formation) {
        return formation == null || formation.isBlank() ? Formacion.POR_DEFECTO : Formacion.desde(formation);
    }
}
