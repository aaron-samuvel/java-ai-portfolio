package com.aaron.helpdesk.config;

import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisChatMemoryStoreTest {
    @SuppressWarnings("unchecked")
    @Test void serializesWindowByEncodedMemoryIdWithTwoHourTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var store = new RedisChatMemoryStore(redis);
        String key = "helpdesk:chat-memory:Y3VzdG9tZXItMTAwMQ";

        List<ChatMessage> messages = List.of(UserMessage.from("My order is NS-1042"));
        store.updateMessages("customer-1001", messages);
        verify(values).set(eq(key), eq(ChatMessageSerializer.messagesToJson(messages)), eq(Duration.ofHours(2)));

        when(values.get(key)).thenReturn(ChatMessageSerializer.messagesToJson(messages));
        assertEquals(messages, store.getMessages("customer-1001"));
        store.deleteMessages("customer-1001");
        verify(redis).delete(key);
    }

    @Test void returnsEmptyMemoryWhenRedisHasNoValue() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        assertTrue(new RedisChatMemoryStore(redis).getMessages("customer-1001").isEmpty());
    }
}
