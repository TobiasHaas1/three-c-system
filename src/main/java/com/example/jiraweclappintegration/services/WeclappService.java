package com.example.jiraweclappintegration.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Minimal client for pushing a sales invoice into Weclapp (ERP).
 * All account-specific values are placeholders - fill them in via
 * application.properties or environment variables before running this.
 */
@Service
public class WeclappService {

    @Value("${weclapp.url}")
    private String weclappUrl;

    @Value("${weclapp.token}")
    private String weclappToken;

    @Value("${weclapp.customerId}")
    private String customerId;

    @Value("${weclapp.unitId}")
    private String unitId;

    @Value("${weclapp.taxId}")
    private String taxId;

    @Value("${weclapp.articleId}")
    private String articleId;

    @Value("${weclapp.unitPrice}")
    private String unitPrice;

    private final ObjectMapper objectMapper;
    private RestClient restClient;

    public WeclappService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private RestClient client() {
        if (restClient == null) {
            restClient = RestClient.builder()
                    .baseUrl(weclappUrl)
                    .defaultHeader("AuthenticationToken", weclappToken)
                    .defaultHeader("Content-Type", "application/json")
                    .defaultHeader("Accept", "application/json")
                    .build();
        }
        return restClient;
    }

    // -------------------------------------------------------------------------
    // PUBLIC ENTRY POINT
    // -------------------------------------------------------------------------

    public void createDirectInvoice(String ticketKey, String ticketSummary, List<WorklogEntry> entries) {
        try {
            ArrayNode items = objectMapper.createArrayNode();

            for (WorklogEntry entry : entries) {
                double hours = (double) parseDurationToSeconds(entry.rawDuration) / 3600.0;

                ObjectNode item = objectMapper.createObjectNode();
                item.put("title", "[" + ticketKey + "] " + ticketSummary);
                item.put("description", entry.description);
                item.put("articleId", articleId);
                item.put("itemType", "DEFAULT");
                item.put("unitId", unitId);
                item.put("unitPrice", unitPrice);
                item.put("quantity", String.valueOf(hours));
                item.put("taxId", taxId);
                item.put("manualQuantity", true);
                item.put("manualUnitPrice", true);

                items.add(item);
            }

            ObjectNode body = objectMapper.createObjectNode();
            body.put("customerId", customerId);
            body.put("invoiceDate", System.currentTimeMillis());
            body.put("salesInvoiceType", "STANDARD_INVOICE");
            body.put("salesChannel", "NET1");
            body.set("salesInvoiceItems", items);

            String response = decompress(client().post()
                    .uri("/salesInvoice")
                    .body(body)
                    .retrieve()
                    .body(byte[].class));

            JsonNode json = objectMapper.readTree(response);
            System.out.println("Weclapp invoice created for " + ticketKey + " | invoiceId=" + json.path("id").asText());

        } catch (Exception e) {
            System.err.println("Weclapp sync failed for " + ticketKey + ": " + e.getMessage());
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
    // PARSE "2h 30m" STYLE DURATION STRING INTO SECONDS
    // -------------------------------------------------------------------------

    private long parseDurationToSeconds(String time) {
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
