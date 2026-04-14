package com.example.backend.controllers;

import com.example.backend.services.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jira")
public class JiraWebhookController {

    private final TicketService ticketService;
    private final ObjectMapper objectMapper;

    public JiraWebhookController(TicketService ticketService, ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<JsonNode> handleJiraWebhook(@RequestBody String rawPayload) {
        try {
            // Delegate the heavy lifting to the TicketService
            JsonNode responseJson = ticketService.processWebhookPayload(rawPayload);

            // If the service decided to skip it (e.g., status wasn't Done)
            if (responseJson.has("status") && responseJson.get("status").asText().equals("skipped")) {
                return ResponseEntity.ok(responseJson);
            }

            return ResponseEntity.ok(responseJson);

        } catch (Exception e) {
            System.err.println("❌ WEBHOOK ERROR: " + e.getMessage());
            e.printStackTrace();

            ObjectNode errorJson = objectMapper.createObjectNode();
            errorJson.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorJson);
        }
    }
}