package com.renaser.os.habits.infrastructure.adapter.in.rest.diario;

import com.renaser.os.habits.application.ports.in.diario.ConsultarBitacoraNocturnaUseCase;
import com.renaser.os.habits.application.ports.in.diario.EscribirBitacoraNocturnaUseCase;
import com.renaser.os.habits.application.ports.in.diario.EscribirBitacoraNocturnaUseCase.EscribirBitacoraNocturnaCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Bitacora Nocturna" / Diario Nocturno — desbloquea el Espejo Sombra de `rag`. Ruta
 * literal del contrato viejo (D-36): {@code GET/PUT /api/v1/journal/today} (R-05/R-06,
 * vivia en `rocks` alla; `entradas_diario` es tabla de `habits` en este backend).
 */
@RestController
@RequestMapping("/api/v1/journal/today")
public class JournalTodayController {

    private final ConsultarBitacoraNocturnaUseCase consultarUseCase;
    private final EscribirBitacoraNocturnaUseCase escribirUseCase;

    public JournalTodayController(ConsultarBitacoraNocturnaUseCase consultarUseCase,
                                   EscribirBitacoraNocturnaUseCase escribirUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.escribirUseCase = escribirUseCase;
    }

    @GetMapping
    public JournalEntryResponse hoy(@RequestHeader("X-Actor-Id") String actorId) {
        return JournalEntryResponse.from(consultarUseCase.consultarHoy(UserId.of(actorId)));
    }

    @PutMapping
    public JournalEntryResponse escribir(@RequestHeader("X-Actor-Id") String actorId,
                                          @RequestBody @Valid UpsertJournalEntryRequest request) {
        var entrada = escribirUseCase.escribir(new EscribirBitacoraNocturnaCommand(UserId.of(actorId),
                request.textContent(), request.audioBucket(), request.audioPath()));
        return JournalEntryResponse.from(entrada);
    }
}
