package cloud.nolasco.simplechat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.core.io.ClassPathResource;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ChatControllerTest {

  private static final String FALLBACK =
      "Lo siento, la conexión fue interrumpida. Please try again.";

  private ChatModel chatModel;
  private ChatMemory chatMemory;
  private VectorStore vectorStore;
  private ChatController controller;

  @BeforeEach
  void setUp() {
    chatModel = mock(ChatModel.class);
    when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    chatMemory = mock(ChatMemory.class);
    vectorStore = mock(VectorStore.class);

    ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();

    controller = new ChatController(
        chatClient,
        chatMemory,
        vectorStore,
        new ClassPathResource("/prompts/rag-prompt.st"));
  }

  @Test
  void streamsChunksWithRagContextAndConversationId() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(new Document("Residence B-18 | Status: Available")));

    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    when(chatModel.stream(promptCaptor.capture()))
        .thenAnswer(invocation -> Flux.just(response("B-"), response("18")));

    StepVerifier.create(controller.chat("visitor-001", "cheapest residence?"))
        .expectNext("B-")
        .expectNext("18")
        .verifyComplete();

    Prompt prompt = promptCaptor.getValue();
    assertThat(prompt.getContents())
        .contains("cheapest residence?")
        .contains("Residence B-18 | Status: Available")
        .contains("For superlatives, compare every record with Status: Available");

    ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(searchCaptor.capture());
    assertThat(searchCaptor.getValue().getTopK()).isEqualTo(10);

    verify(chatMemory).get("visitor-001");
  }

  @Test
  void retriesTransientFailuresBeforeStreaming() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    AtomicInteger attempts = new AtomicInteger();
    when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.defer(() ->
        attempts.getAndIncrement() < 3
            ? Flux.error(new RuntimeException("transient"))
            : Flux.just(response("recovered"))));

    StepVerifier.create(controller.chat("visitor-001", "hola"))
        .expectNext("recovered")
        .verifyComplete();

    assertThat(attempts.get()).isEqualTo(4);
  }

  @Test
  void fallsBackAfterAllRetriesFail() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    AtomicInteger attempts = new AtomicInteger();
    when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.defer(() -> {
      attempts.incrementAndGet();
      return Flux.error(new RuntimeException("down"));
    }));

    StepVerifier.create(controller.chat("visitor-001", "hola"))
        .expectNext(FALLBACK)
        .verifyComplete();

    assertThat(attempts.get()).isEqualTo(4);
  }

  @Test
  void exposesAndClearsConversationHistory() {
    List<Message> history = List.of(new AssistantMessage("previous answer"));
    when(chatMemory.get("visitor-001")).thenReturn(history);

    assertThat(controller.getHistory("visitor-001")).isEqualTo(history);
    assertThat(controller.clearHistory("visitor-001"))
        .isEqualTo("Conversation history cleared for user: visitor-001");

    verify(chatMemory).clear("visitor-001");
  }

  private static ChatResponse response(String text) {
    return ChatResponse.builder()
        .generations(List.of(new Generation(new AssistantMessage(text))))
        .build();
  }
}
