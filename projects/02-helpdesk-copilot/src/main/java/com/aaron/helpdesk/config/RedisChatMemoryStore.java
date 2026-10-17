package com.aaron.helpdesk.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/** Optional Redis persistence for a bounded LangChain4j message window. */
@Component
@ConditionalOnProperty(name = "helpdesk.memory.redis.enabled", havingValue = "true")
public class RedisChatMemoryStore implements ChatMemoryStore {
    private static final Duration TTL = Duration.ofHours(2);
    private final StringRedisTemplate redis;

    public RedisChatMemoryStore(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redis.opsForValue().get(key(memoryId));
        return json == null || json.isBlank() ? List.of() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        redis.opsForValue().set(key(memoryId), ChatMessageSerializer.messagesToJson(messages), TTL);
    }

    @Override
    public void deleteMessages(Object memoryId) { redis.delete(key(memoryId)); }

    private String key(Object memoryId) {
        if (memoryId == null) throw new IllegalArgumentException("Chat memory ID is required");
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(memoryId).getBytes(StandardCharsets.UTF_8));
        return "helpdesk:chat-memory:" + encoded;
    }
}
