package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/** Una versión publicada del diagnóstico (spec 026): la foto de la ficha, quién la publicó y cuándo. Append-only, como la tabla. */
public record VersionPublicada(int version, FichaDelDiagnostico ficha, UserId publicadaPor, String publicadaPorNombre, Instant publicadaEn) {
}
