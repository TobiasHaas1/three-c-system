package com.example.jiraweclappintegration.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class TempoTranslationService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private String dynamicSystemInstruction;

    @Value("classpath:bausteine.json")
    private Resource bausteineResource;

    public TempoTranslationService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initSystemPrompt() {
        try {
            JsonNode rootNode = objectMapper.readTree(bausteineResource.getInputStream());
            StringBuilder promptBuilder = new StringBuilder();

            // 1. Identität & Aufgabe
            promptBuilder.append(rootNode.path("identitaet").asText()).append("\n");
            promptBuilder.append(rootNode.path("aufgabe").asText()).append("\n\n");

            // 2. Filter Logik
            promptBuilder.append("FILTER-LOGIK (Diese Punkte STREICHEN):\n");
            int counter = 1;
            for (JsonNode node : rootNode.path("filter_logik")) {
                promptBuilder.append(counter++).append(". ").append(node.asText()).append("\n");
            }
            promptBuilder.append("\n");

            // 3. Abstraktions-Bausteine
            promptBuilder.append("ABSTRAKTIONS-BAUSTEINE (Nutze NUR diese Phrasen):\n");
            for (JsonNode node : rootNode.path("bausteine")) {
                promptBuilder.append("- ")
                        .append(node.path("trigger").asText())
                        .append(" -> \"")
                        .append(node.path("replacement").asText())
                        .append("\"\n");
            }
            promptBuilder.append("\n");

            // 4. Strikte Verbote
            promptBuilder.append("STRIKTE VERBOTE:\n");
            counter = 1;
            for (JsonNode node : rootNode.path("strikte_verbote")) {
                promptBuilder.append(counter++).append(". ").append(node.asText()).append("\n");
            }
            promptBuilder.append("\n");

            // 5. Bedingter Abschluss
            promptBuilder.append("BEDINGTER ABSCHLUSS (WICHTIG):\n");
            for (JsonNode node : rootNode.path("bedingter_abschluss")) {
                promptBuilder.append("- ").append(node.asText()).append("\n");
            }
            promptBuilder.append("\n");

            // 6. Output Format
            promptBuilder.append(rootNode.path("output_format").asText()).append("\n");

            this.dynamicSystemInstruction = promptBuilder.toString();

            System.out.println("✅ System-Prompt erfolgreich dynamisch geladen.");
            System.out.println("Gefundene Bausteine: " + rootNode.path("bausteine").size());
            System.out.println("Gefundene Verbote: " + rootNode.path("strikte_verbote").size());

        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Laden der bausteine.json. Bitte prüfen, ob die Datei in src/main/resources liegt.", e);
        }
    }

    public String translateForInvoice(String rawTechnicianNote) {
        SystemMessage systemMessage = new SystemMessage(this.dynamicSystemInstruction);
        UserMessage userMessage = new UserMessage("Raw Technician Note: " + rawTechnicianNote);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        String aiResponse = chatClient.prompt(prompt)
                .call()
                .content();

        // SICHERHEITS-FILTER FÜR DEEPSEEK-R1: Entfernt <think>-Blöcke
        if (aiResponse != null && aiResponse.contains("<think>")) {
            // (?s) allows dot to match newlines
            aiResponse = aiResponse.replaceAll("(?s)<think>.*?</think>", "").trim();
        }

        // SICHERHEITS-FILTER 2: Isoliere strikt das JSON-Objekt
        // (Falls das Modell Markdown wie ```json davor oder ``` danach setzt)
        if (aiResponse != null && aiResponse.contains("{") && aiResponse.contains("}")) {
            int startIndex = aiResponse.indexOf("{");
            int endIndex = aiResponse.lastIndexOf("}") + 1;
            aiResponse = aiResponse.substring(startIndex, endIndex);
        }

        return aiResponse;
    }
}