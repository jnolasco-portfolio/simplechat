package cloud.nolasco.simplechat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ChatClientConfig {

  @Bean
  public ChatClient chatClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
    return chatClientBuilder
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();
  }

  @Bean
  public ChatMemory jdbcChatMemory(JdbcTemplate jdbcTemplate) {
    ChatMemoryRepository chatMemoryRepository = JdbcChatMemoryRepository.builder()
        .jdbcTemplate(jdbcTemplate)
        .dialect(new PostgresChatMemoryRepositoryDialect())
        .build();

    ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(chatMemoryRepository)
        // .maxMessages(10)
        .build();

    return chatMemory;
  }

}
