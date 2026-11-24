package cloud.nolasco.simplechat;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import reactor.core.publisher.Flux;

@WebMvcTest(ChatController.class)
@ActiveProfiles("test")
class ChatControllerWebTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ChatMemory chatMemory;

  @MockitoBean
  private VectorStore vectorStore;

  @Test
  void streamsChatResponseAsSse() throws Exception {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(new Document("Residence B-18 | Status: Available")));

    var result = mockMvc.perform(post("/stream/visitor-001/chat")
        .contentType(MediaType.TEXT_PLAIN)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .content("cheapest residence?"))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", containsString("text/event-stream")))
        .andExpect(content().string(containsString("data:B-")))
        .andExpect(content().string(containsString("data:18")));

    verify(chatMemory).get("visitor-001");
  }

  @Test
  void returnsHistory() throws Exception {
    List<Message> history = List.of(new AssistantMessage("previous answer"));
    when(chatMemory.get("visitor-001")).thenReturn(history);

    mockMvc.perform(get("/visitor-001/history").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("previous answer")));
  }

  @Test
  void clearsHistory() throws Exception {
    mockMvc.perform(delete("/visitor-001/history"))
        .andExpect(status().isOk())
        .andExpect(content().string(
            "Conversation history cleared for user: visitor-001"));

    verify(chatMemory).clear("visitor-001");
  }

  @TestConfiguration
  static class ChatClientBeans {

    @Bean
    ChatClient chatClient(ChatMemory chatMemory) {
      ChatModel chatModel = mock(ChatModel.class);
      when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
      when(chatModel.stream(any(Prompt.class)))
          .thenReturn(Flux.just(
              response("B-"),
              response("18")));
      return ChatClient.builder(chatModel)
          .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
          .build();
    }

    private static ChatResponse response(String text) {
      return ChatResponse.builder()
          .generations(List.of(new Generation(new AssistantMessage(text))))
          .build();
    }
  }
}
