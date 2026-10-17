package com.aaron.helpdesk.config;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class MemoryConfiguration {
    @Bean
    ChatMemoryProvider chatMemoryProvider(ObjectProvider<ChatMemoryStore> stores) {
        return memoryId -> {
            var builder = MessageWindowChatMemory.builder().id(memoryId).maxMessages(20);
            ChatMemoryStore store = stores.getIfAvailable();
            if (store != null) builder.chatMemoryStore(store);
            return builder.build();
        };
    }
}
