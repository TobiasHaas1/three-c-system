package com.example.backend.services;

import com.example.backend.enums.TicketStatus;
import com.example.backend.models.TempoBooking;
import com.example.backend.models.Ticket;
import com.example.backend.repositories.TempoBookingRepository;
import com.example.backend.repositories.TicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class TicketService {

    private final String JIRA_DOMAIN = "https://tobiashaas.atlassian.net";
    private final String JIRA_USER = "tobias.haas05@gmail.com";
    private final String JIRA_TOKEN = "ATATT3xFfGF0a8xcxLncamjdg2pkx60eZqnu6M9mSokeVfigC2j36Wcd7_Iq55F8mf2Jn2lQ04SWILvFE3q9K_lwSohUoNiHTfLd4-pETOAYOhHVxXafMdUAwXZbCnP_XWB7KY7INmjG1OkBO_AmWo032gHJFssVhuNxx5fZKQvfuLqj0S74NHM=560AFB93";

    private final TicketRepository ticketRepository;
    private final TempoBookingRepository tempoBookingRepository;
    private final TempoTranslationService translationService;
    private final WeclappService weclappService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TicketService(TicketRepository ticketRepository,
                         TempoBookingRepository tempoBookingRepository,
                         TempoTranslationService translationService,
                         WeclappService weclappService,
                         ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.tempoBookingRepository = tempoBookingRepository;
        this.translationService = translationService;
        this.weclappService = weclappService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(JIRA_DOMAIN)
                .defaultHeader("Authorization", "Basic " +
                        Base64.getEncoder().encodeToString((JIRA_USER + ":" + JIRA_TOKEN).getBytes()))
                .build();
    }

    // --- LOCAL DATABASE OPERATIONS ---

    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    public Optional<Ticket> getTicketByKey(String fullKey) {
        String[] parts = fullKey.split("-");
        if (parts.length != 2) {
            return Optional.empty();
        }

        try {
            String projectKey = parts[0];
            Long ticketNumber = Long.parseLong(parts[1]);
            return ticketRepository.findByProjectKeyAndTicketNumber(projectKey, ticketNumber);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public Ticket createTicket(Ticket ticket) {
        return ticketRepository.save(ticket);
    }

    public Optional<TempoBooking> addTempoBooking(Long ticketId, TempoBooking booking) {
        return ticketRepository.findById(ticketId).map(ticket -> {
            booking.setTicket(ticket);
            return tempoBookingRepository.save(booking);
        });
    }

    public Optional<Ticket> updateTicketStatus(Long id, TicketStatus newStatus) {
        return ticketRepository.findById(id).map(ticket -> {
            boolean isNewlyDone = (newStatus == TicketStatus.ERLEDIGT);

            ticket.setStatus(String.valueOf(newStatus));
            Ticket savedTicket = ticketRepository.save(ticket);

            if (isNewlyDone) {
                processBillingForTicket(savedTicket);
            }

            return savedTicket;
        });
    }

    private void processBillingForTicket(Ticket ticket) {
        System.out.println("\n--- 🚀 INTERNAL BILLING TRIGGERED FOR " + ticket.getProjectKey() + "-" + ticket.getTicketNumber() + " 🚀 ---");

        List<WeclappService.WorklogEntry> worklogEntries = new ArrayList<>();

        try {
            for (TempoBooking booking : ticket.getTempoBookings()) {
                String rawText = booking.getDescription();
                String timeSpent = booking.getDuration();

                if (rawText != null && !rawText.isBlank() && !rawText.equals("null")) {
                    String aiResponseRaw = translationService.translateForInvoice(rawText);
                    JsonNode aiJson = objectMapper.readTree(aiResponseRaw);
                    String cleanText = aiJson.path("customer_billing_text").asText();

                    worklogEntries.add(new WeclappService.WorklogEntry(cleanText, timeSpent));
                    System.out.println("✅ Processed Worklog: " + timeSpent + " -> " + cleanText);
                }
            }

            if (!worklogEntries.isEmpty()) {
                String fullTicketKey = ticket.getProjectKey() + "-" + ticket.getTicketNumber();
                weclappService.createDirectInvoice(fullTicketKey, ticket.getSummary(), worklogEntries);
                System.out.println("✅ Successfully sent invoice to Weclapp!");
            } else {
                System.out.println("⚠️ No valid worklogs found to bill.");
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR Processing Weclapp Invoice: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- EXTERNAL JIRA WEBHOOK OPERATIONS ---

    public JsonNode processWebhookPayload(String rawPayload) throws Exception {
        ObjectNode responseJson = objectMapper.createObjectNode();
        System.out.println("\n--- 🟢 NEW WEBHOOK EVENT (GRANULAR MODE) 🟢 ---");

        JsonNode root = objectMapper.readTree(rawPayload);
        JsonNode issueNode = root.path("issue");
        String issueKey = issueNode.path("key").asText();
        String statusName = issueNode.path("fields").path("status").path("name").asText();
        String ticketSummary = issueNode.path("fields").path("summary").asText();

        if (!statusName.equalsIgnoreCase("Done") && !statusName.equalsIgnoreCase("FERTIG")) {
            responseJson.put("status", "skipped");
            return responseJson;
        }

        ObjectNode ticketInfo = responseJson.putObject("ticket_details");
        ticketInfo.put("key", issueKey);
        ticketInfo.put("summary", ticketSummary);
        ticketInfo.put("description", issueNode.path("fields").path("description").asText());
        ticketInfo.put("status", statusName);
        ticketInfo.put("project", issueNode.path("fields").path("project").path("name").asText());

        JsonNode worklogResponse = restClient.get()
                .uri("/rest/api/2/issue/" + issueKey + "/worklog")
                .retrieve()
                .body(JsonNode.class);

        ArrayNode processedWorklogs = responseJson.putArray("processed_worklogs");
        JsonNode logsArray = worklogResponse.path("worklogs");

        List<WeclappService.WorklogEntry> worklogEntries = new ArrayList<>();

        if (logsArray != null && logsArray.isArray()) {
            for (JsonNode log : logsArray) {
                String author = log.path("author").path("displayName").asText();
                String timeSpent = log.path("timeSpent").asText();
                String rawText = log.path("comment").asText();

                if (rawText != null && !rawText.isBlank() && !rawText.equals("null")) {
                    String aiResponseRaw = translationService.translateForInvoice(rawText);
                    JsonNode aiJson = objectMapper.readTree(aiResponseRaw);
                    String cleanText = aiJson.path("customer_billing_text").asText();

                    worklogEntries.add(new WeclappService.WorklogEntry(cleanText, timeSpent));

                    ObjectNode logDetail = processedWorklogs.addObject();
                    logDetail.put("creator", author);
                    logDetail.put("time", timeSpent);
                    logDetail.put("raw_text", rawText);
                    logDetail.put("ai_cleaned_text", cleanText);
                }
            }
        }

        if (!worklogEntries.isEmpty()) {
            weclappService.createDirectInvoice(issueKey, ticketSummary, worklogEntries);
        }

        System.out.println("🔍 GRANULAR DATA FOR VERIFICATION:");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseJson));
        System.out.println("------------------------------------\n");

        return responseJson;
    }
}