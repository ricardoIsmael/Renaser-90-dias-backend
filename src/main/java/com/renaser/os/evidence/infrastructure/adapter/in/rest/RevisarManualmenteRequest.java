package com.renaser.os.evidence.infrastructure.adapter.in.rest;

/** {@code aprobar=true} -> VALIDA, {@code aprobar=false} -> RECHAZADA. Solo aplica a evidencia en REVISION_MANUAL. */
public record RevisarManualmenteRequest(boolean aprobar, String notas) {
}
