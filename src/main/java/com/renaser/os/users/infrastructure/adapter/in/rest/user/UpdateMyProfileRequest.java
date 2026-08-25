package com.renaser.os.users.infrastructure.adapter.in.rest.user;

/**
 * U-02/U-03. Campos null = "no cambiar". A proposito NO tiene programDay/coherenceScore/
 * leaguePoints/currentPhase/role — el compilador los excluye (§5.3.3).
 */
public record UpdateMyProfileRequest(String fullName, String avatarUrl, String bio, String department) {
}
