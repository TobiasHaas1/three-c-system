package com.example.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

@Service
public class WeclappService {

    private final String WECLAPP_URL = "https://kylhxmueebvyyse.weclapp.com/webapp/api/v2";
    private final String WECLAPP_TOKEN = "a6887972-f7be-462a-987d-77c1fc41b1b6";
    private final String CUSTOMER_ID = "4228";       // Berger
    private final String UNIT_ID = "3575";           // h (Stunde)
    private final String TAX_ID = "3659";            // 20% AT
    private final String ARTICLE_LOWCOMPLEX = "4436"; // lowcomplex @ 150/h
    private final String UNIT_PRICE_LOW = "150.00";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WeclappService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(WECLAPP_URL)
                .defaultHeader("AuthenticationToken", WECLAPP_TOKEN)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    // -------------------------------------------------------------------------
    // PUBLIC ENTRY POINT ✅ Directly matches the working cURL payload
    // -------------------------------------------------------------------------

    public void createDirectInvoice(String jiraKey, String ticketSummary, List<WorklogEntry> entries) {
        try {
            System.out.println("🚀 Starting direct weclapp invoice sync for " + jiraKey + " with " + entries.size() + " worklogs.");

            ArrayNode items = objectMapper.createArrayNode();

            for (WorklogEntry entry : entries) {
                double hours = (double) parseJiraTimeToSeconds(entry.rawDuration) / 3600.0;

                ObjectNode item = objectMapper.createObjectNode();
                item.put("title", "[" + jiraKey + "] " + ticketSummary);
                item.put("description", entry.description);
                item.put("articleId", ARTICLE_LOWCOMPLEX);
                item.put("itemType", "DEFAULT");
                item.put("unitId", UNIT_ID);
                item.put("unitPrice", UNIT_PRICE_LOW);
                item.put("quantity", String.valueOf(hours));
                item.put("taxId", TAX_ID);
                item.put("manualQuantity", true);
                item.put("manualUnitPrice", true);

                items.add(item);
            }

            ObjectNode body = objectMapper.createObjectNode();
            body.put("customerId", CUSTOMER_ID);
            body.put("invoiceDate", System.currentTimeMillis());
            body.put("salesInvoiceType", "STANDARD_INVOICE");
            body.put("salesChannel", "NET1");
            body.set("salesInvoiceItems", items);

            String response = decompress(restClient.post()
                    .uri("/salesInvoice")
                    .body(body)
                    .retrieve()
                    .body(byte[].class));

            System.out.println("🧾 Invoice created successfully: " + response);

            JsonNode json = objectMapper.readTree(response);
            System.out.println("✅ weclapp direct invoice complete for " + jiraKey + " | invoiceId=" + json.path("id").asText());

        } catch (Exception e) {
            System.err.println("❌ weclapp sync failed for " + jiraKey + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // GZIP DECOMPRESS
    // -------------------------------------------------------------------------

    private String decompress(byte[] data) {
        if (data == null || data.length == 0) return "";
        if (data[0] == (byte) 0x1f && data[1] == (byte) 0x8b) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = gzip.read(buf)) != -1) out.write(buf, 0, len);
                return out.toString("UTF-8");
            } catch (Exception e) {
                throw new RuntimeException("Failed to decompress gzip response", e);
            }
        }
        try {
            return new String(data, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Failed to read response bytes", e);
        }
    }

    // -------------------------------------------------------------------------
    // PARSE JIRA TIME STRING ➡️ SECONDS
    // -------------------------------------------------------------------------

    private long parseJiraTimeToSeconds(String time) {
        long seconds = 0;
        try {
            String temp = time.toLowerCase().trim();
            if (temp.contains("h")) {
                String[] parts = temp.split("h");
                seconds += Long.parseLong(parts[0].trim()) * 3600;
                temp = parts.length > 1 ? parts[1].trim() : "";
            }
            if (temp.contains("m")) {
                seconds += Long.parseLong(temp.split("m")[0].trim()) * 60;
            }
        } catch (Exception e) {
            return 3600; // default 1h
        }
        return seconds == 0 ? 3600 : seconds;
    }

    // -------------------------------------------------------------------------
    // INNER CLASSES
    // -------------------------------------------------------------------------

    public static class WorklogEntry {
        public final String description;
        public final String rawDuration;

        public WorklogEntry(String description, String rawDuration) {
            this.description = description;
            this.rawDuration = rawDuration;
        }
    }
}