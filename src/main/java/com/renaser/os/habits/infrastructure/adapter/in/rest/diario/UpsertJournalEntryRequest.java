package com.renaser.os.habits.infrastructure.adapter.in.rest.diario;

/** {@code type} es siempre BITACORA_NOCTURNA — nunca lo manda el cliente (D-36, literal del repo viejo). */
public record UpsertJournalEntryRequest(String textContent, String audioBucket, String audioPath) {
}
