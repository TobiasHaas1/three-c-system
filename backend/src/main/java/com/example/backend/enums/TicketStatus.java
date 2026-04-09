package com.example.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TicketStatus {
    SECOND_LEVEL("2ND Level"),
    SLA_AUSGESETZT("SLA ausgesetzt"),
    ERLEDIGT("Erledigt");

    private final String displayName;

    TicketStatus(String displayName) {
        this.displayName = displayName;
    }

    // Tells Spring Boot: "When sending this ticket to Angular, use this string!"
    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    // Tells Spring Boot: "When receiving a string from Angular, map it back to this Enum!"
    @JsonCreator
    public static TicketStatus fromString(String value) {
        for (TicketStatus status : TicketStatus.values()) {
            // Check against both the display name ("2ND Level") and the constant ("SECOND_LEVEL") just in case
            if (status.displayName.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}