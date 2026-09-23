package com.renaser.os.rag.infrastructure.adapter.in.rest.voz;

import com.renaser.os.rag.application.ports.in.voz.SintetizarVozUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La voz del orbe: texto -> audio WAV (Piper, voz {@code es_MX-claude-high}).
 *
 * <ul>
 *   <li><b>200</b> {@code audio/wav}: el audio, listo para reproducir.</li>
 *   <li><b>204</b> sin cuerpo: no hay voz del servidor (proveedor {@code noop}, o Piper caido o
 *   lento). La app habla con el TTS del telefono.</li>
 *   <li><b>400</b>: texto vacio o de mas de 400 caracteres.</li>
 * </ul>
 *
 * <p>Vive bajo {@code /api/v1/renasia/**}, que exige sesion real ({@code SecurityConfig}). Sin
 * {@code produces} en el mapping a proposito: asi un 400 sigue saliendo como el JSON de error de
 * siempre aunque la app pida {@code Accept: audio/wav}; el tipo del 200 lo fija la respuesta.
 */
@RestController
@RequestMapping("/api/v1/renasia/voz")
public class VozRenasiaController {

    static final MediaType AUDIO_WAV = MediaType.parseMediaType("audio/wav");

    private final SintetizarVozUseCase sintetizarVozUseCase;

    public VozRenasiaController(SintetizarVozUseCase sintetizarVozUseCase) {
        this.sintetizarVozUseCase = sintetizarVozUseCase;
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping
    public ResponseEntity<byte[]> sintetizar(@ActorAutenticado UserId actorId,
                                             @RequestBody @Valid SintetizarVozRequest request) {
        return sintetizarVozUseCase.sintetizar(actorId, request.texto())
                .map(audio -> ResponseEntity.ok().contentType(AUDIO_WAV).body(audio))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
