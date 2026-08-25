package com.renaser.os.onboarding.infrastructure.adapter.in.rest.media;

import com.renaser.os.onboarding.application.ports.in.media.ObtenerUrlSubidaMediaUseCase;
import com.renaser.os.onboarding.application.ports.in.media.ObtenerUrlSubidaMediaUseCase.ObtenerUrlSubidaMediaCommand;
import com.renaser.os.onboarding.application.ports.in.media.RegistrarMediaUseCase;
import com.renaser.os.onboarding.application.ports.in.media.RegistrarMediaUseCase.RegistrarMediaCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding/media")
public class MediaController {

    private final ObtenerUrlSubidaMediaUseCase urlSubidaUseCase;
    private final RegistrarMediaUseCase registrarMediaUseCase;

    public MediaController(ObtenerUrlSubidaMediaUseCase urlSubidaUseCase, RegistrarMediaUseCase registrarMediaUseCase) {
        this.urlSubidaUseCase = urlSubidaUseCase;
        this.registrarMediaUseCase = registrarMediaUseCase;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<UrlSubidaMediaResponse> urlDeSubida(@RequestHeader("X-Actor-Id") String actorId,
                                                                @Valid @RequestBody UrlSubidaMediaRequest request) {
        var comando = new ObtenerUrlSubidaMediaCommand(UserId.of(actorId), request.flow(), request.questionKey(),
                request.kind(), request.contentType());
        return ResponseEntity.ok(UrlSubidaMediaResponse.from(urlSubidaUseCase.obtener(comando)));
    }

    @PostMapping
    public ResponseEntity<MediaResponse> registrar(@RequestHeader("X-Actor-Id") String actorId,
                                                     @Valid @RequestBody RegistrarMediaRequest request) {
        var comando = new RegistrarMediaCommand(UserId.of(actorId), request.flow(), request.questionKey(),
                request.kind(), request.bucket(), request.path(), request.mime(), request.sizeBytes(),
                request.durationSeconds(), request.metadata());
        var media = registrarMediaUseCase.registrar(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(MediaResponse.from(media));
    }
}
