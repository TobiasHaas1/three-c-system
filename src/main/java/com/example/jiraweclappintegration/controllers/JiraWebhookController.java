package com.example.jiraweclappintegration.controllers;

import com.example.jiraweclappintegration.services.TempoTranslationService;
import com.example.jiraweclappintegration.services.WeclappService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Demo: turns raw technician ticket notes into clean, customer-facing
 * billing text via an LLM, then forwards the result to an ERP system.
 *
 * Two entry points share the same {@link #processAndSync} pipeline:
 *  - {@link #handleJiraWebhook}: fired automatically by a Jira "issue updated" webhook
 *  - {@link #processTicketText}: a plain REST endpoint for feeding in ticket text directly
 *
 * All values below are placeholders - wire up real ones via
 * application.properties or environment variables.
 */
@RestController
@RequestMapping("/api/tickets")
public class JiraWebhookController {

    @Value("${jira.domain}")
    private String jiraDomain;

    @Value("${jira.user}")
    private String jiraUser;

    @Value("${jira.token}")
    private String jiraToken;

    private final TempoTranslationService translationService;
    private final WeclappService weclappService;
    private final ObjectMapper objectMapper;
    private RestClient jiraClient;

    public JiraWebhookController(TempoTranslationService translationService, WeclappService weclappService, ObjectMapper objectMapper) {
        this.translationService = translationService;
        this.weclappService = weclappService;
        this.objectMapper = objectMapper;
    }

    private RestClient jiraClient() {
        if (jiraClient == null) {
            jiraClient = RestClient.builder()
                    .baseUrl(jiraDomain)
                    .defaultHeader("Authorization", "Basic " +
                            Base64.getEncoder().encodeToString((jiraUser + ":" + jiraToken).getBytes()))
                    .build();
        }
        return jiraClient;
    }

    /**
     * Webhook target for Jira "issue updated" events. Only reacts once the
     * issue is Done, then fetches all worklogs and runs each one through
     * the AI + ERP pipeline.
     */
    @PostMapping("/webhook")
    public ResponseEntity<JsonNode> handleJiraWebhook(@RequestBody String rawPayload) {
        ObjectNode responseJson = objectMapper.createObjectNode();
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode issueNode = root.path("issue");
            String issueKey = issueNode.path("key").asText();
            String statusName = issueNode.path("fields").path("status").path("name").asText();
            String ticketSummary = issueNode.path("fields").path("summary").asText();

            if (!statusName.equalsIgnoreCase("Done") && !statusName.equalsIgnoreCase("FERTIG")) {
                responseJson.put("status", "skipped");
                return ResponseEntity.ok(responseJson);
            }

            JsonNode worklogResponse = jiraClient().get()
                    .uri("/rest/api/2/issue/" + issueKey + "/worklog")
                    .retrieve()
                    .body(JsonNode.class);

            List<RawNote> notes = new ArrayList<>();
            JsonNode logsArray = worklogResponse.path("worklogs");
            if (logsArray.isArray()) {
                for (JsonNode log : logsArray) {
                    String text = log.path("comment").asText();
                    if (text != null && !text.isBlank() && !text.equals("null")) {
                        String author = log.path("author").path("displayName").asText();
                        String timeSpent = log.path("timeSpent").asText("1h");
                        notes.add(new RawNote(author, timeSpent, text));
                    }
                }
            }

            processAndSync(issueKey, ticketSummary, notes, responseJson);
            return ResponseEntity.ok(responseJson);

        } catch (Exception e) {
            responseJson.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(responseJson);
        }
    }

    /**
     * Plain endpoint for feeding ticket text into the same pipeline directly,
     * without going through Jira at all - useful for testing or for
     * triggering processing from any other source system.
     *
     * Expected body: {"ticketKey": "...", "ticketSummary": "...",
     *                 "author": "...", "timeSpent": "1h", "ticketText": "..."}
     */
    @PostMapping("/process")
    public ResponseEntity<JsonNode> processTicketText(@RequestBody JsonNode body) {
        ObjectNode responseJson = objectMapper.createObjectNode();
        try {
            String ticketText = body.path("ticketText").asText("");
            if (ticketText.isBlank()) {
                responseJson.put("error", "ticketText must not be empty");
                return ResponseEntity.badRequest().body(responseJson);
            }

            String ticketKey = body.path("ticketKey").asText("MANUAL-1");
            String ticketSummary = body.path("ticketSummary").asText("");
            String author = body.path("author").asText("");
            String timeSpent = body.path("timeSpent").asText("1h");

            RawNote note = new RawNote(author, timeSpent, ticketText);
            processAndSync(ticketKey, ticketSummary, List.of(note), responseJson);
            return ResponseEntity.ok(responseJson);

        } catch (Exception e) {
            responseJson.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(responseJson);
        }
    }

    /**
     * Shared pipeline: runs each raw note through the AI abstraction step,
     * then batches the results into a single ERP invoice sync call.
     */
    private void processAndSync(String ticketKey, String ticketSummary, List<RawNote> notes, ObjectNode responseJson) throws Exception {
        ArrayNode processed = responseJson.putArray("processed_notes");
        List<WeclappService.WorklogEntry> weclappEntries = new ArrayList<>();

        for (RawNote note : notes) {
            String aiResponseRaw = translationService.translateForInvoice(note.text());
            JsonNode aiJson = objectMapper.readTree(aiResponseRaw);
            String cleanText = aiJson.path("customer_billing_text").asText();

            if (cleanText != null && !cleanText.isBlank()) {
                weclappEntries.add(new WeclappService.WorklogEntry(cleanText, note.timeSpent()));
            }

            ObjectNode entry = processed.addObject();
            entry.put("author", note.author());
            entry.put("time_spent", note.timeSpent());
            entry.put("raw_text", note.text());
            entry.put("ai_cleaned_text", cleanText);
        }

        if (!weclappEntries.isEmpty()) {
            weclappService.createDirectInvoice(ticketKey, ticketSummary, weclappEntries);
            responseJson.put("erp_sync", "triggered");
        } else {
            responseJson.put("erp_sync", "skipped");
        }
    }

    private record RawNote(String author, String timeSpent, String text) {
    }
}
