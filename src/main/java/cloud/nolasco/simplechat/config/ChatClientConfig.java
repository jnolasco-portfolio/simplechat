package cloud.nolasco.simplechat.config;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;

import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.chat.prompt.Prompt;

import cloud.nolasco.simplechat.tools.datetime.CommonTools;

@Configuration
public class ChatClientConfig {

  @Value("classpath:/prompts/system-prompt.st")
  private Resource systemPrompt;

  @Bean
  public ChatClient chatClient(
      ChatClient.Builder chatClientBuilder,
      ChatMemory chatMemory,
      CommonTools commonTools) {
    SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPrompt);
    Prompt systemPrompt = systemPromptTemplate.create(Map.of("assistantName", "Sofía",
        "DevelopmentName", "Altavista",
        "greeting", commonTools.greeting()));

    return chatClientBuilder
        .defaultSystem(systemPrompt.toString())
        // Run memory after ToolCallingAdvisor so history survives its loop in Spring AI 2.1.
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory)
            .order(ToolCallingAdvisor.DEFAULT_ORDER + 100)
            .build())
        .defaultTools(commonTools)
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
