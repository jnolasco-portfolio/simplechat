package cloud.nolasco.simplechat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import cloud.nolasco.simplechat.service.IngestService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
class SimplechatApplicationTests {

	@TestConfiguration
	static class TestConfig {
		@Bean
		public ChatClient chatClient() {
			return mock(ChatClient.class);
		}

		@Bean
		public ChatMemory chatMemory() {
			return mock(ChatMemory.class);
		}

		@Bean
		public IngestService ingestService() {
			return mock(IngestService.class);
		}
	}

	@Test
	void contextLoads() {
	}
}
