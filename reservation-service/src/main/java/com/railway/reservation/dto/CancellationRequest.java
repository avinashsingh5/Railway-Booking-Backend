package com.railway.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CancellationRequest(
        @Schema(description = "PNR of the confirmed ticket", example = "4829173650")
        @NotBlank String pnr,

        @Schema(description = "Optional. Ids of the passengers to cancel (the `id` of each passenger in the ticket details). "
                + "Omit it to cancel the WHOLE ticket. If it lists every remaining passenger, the whole ticket is cancelled.",
                example = "[12, 13]")
        @Size(min = 1, message = "must not be empty (omit it to cancel the whole ticket)")
        List<@NotNull Long> passengerIds) {
}
