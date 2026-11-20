package com.opsmate.backend.agent;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Fake ChatModel for testing that returns canned responses.
 * Supports sequential responses for the same agent key.
 */
public class FakeChatModel implements ChatModel {

    private final Map<String, List<String>> cannedResponses = new ConcurrentHashMap<>();
    private String defaultResponse = "{}";
    private Function<Prompt, String> dynamicResponder;

    public FakeChatModel withResponse(String promptContains, String response) {
        cannedResponses.computeIfAbsent(promptContains, k -> new ArrayList<>()).add(response);
        return this;
    }

    public FakeChatModel withDefaultResponse(String response) {
        this.defaultResponse = response;
        return this;
    }

    public FakeChatModel withDynamicResponder(Function<Prompt, String> responder) {
        this.dynamicResponder = responder;
        return this;
    }

    /** Clears all canned responses for test isolation. */
    public void clear() {
        cannedResponses.clear();
        defaultResponse = "{}";
        dynamicResponder = null;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String response = findResponse(prompt);
        Generation generation = new Generation(new AssistantMessage(response));
        return new ChatResponse(List.of(generation));
    }

    private String findResponse(Prompt prompt) {
        if (dynamicResponder != null) {
            return dynamicResponder.apply(prompt);
        }
        String promptText = prompt.getInstructions().stream()
                .map(m -> m.getText())
                .reduce("", (a, b) -> a + b);
        for (Map.Entry<String, List<String>> entry : cannedResponses.entrySet()) {
            if (promptText.contains(entry.getKey())) {
                List<String> responses = entry.getValue();
                if (!responses.isEmpty()) {
                    return responses.remove(0);
                }
            }
        }
        return defaultResponse;
    }
}