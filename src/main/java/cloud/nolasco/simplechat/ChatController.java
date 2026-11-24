package cloud.nolasco.simplechat;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
@CrossOrigin
public class ChatController {

    // Four fact sheets are loaded as one document each. Retrieve all of them so
    // superlatives compare every residence.
    private static final int RAG_TOP_K = 10;

    private static final PromptTemplate RAG_PROMPT_TEMPLATE = new PromptTemplate("""
            Answer only from the conversation history and the context below.
            - Use earlier turns when the question refers to something the user said before.
            - Each residence record starts with "Residence <ID>" and contains pipe-separated fields.
            - For superlatives, compare every record with Status: Available. Ignore Reserved and Sold records.
            - State the exact Residence ID and value from the matching record. If it is not in the context, say so.
            Question: {query}
            Context:
            ---------------------
            {question_answer_context}
            ---------------------
            """);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;

    public ChatController(ChatClient chatClient, ChatMemory chatMemory, VectorStore vectorStore) {

        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
    }

    @PostMapping("/stream/{userId}/chat")
    public Flux<String> chat(
            @PathVariable String userId,
            @RequestBody String request) {
        return chatClient
                .prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, userId))
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(RAG_TOP_K).build())
                        .promptTemplate(RAG_PROMPT_TEMPLATE)
                        .build())
                .user(request)
                .stream()
                .content()
                .retry(3)
                .onErrorResume(e -> Flux.just("Lo siento, la conexión fue interrumpida. Please try again."));
    }

    @GetMapping("/{userId}/history")
    public List<Message> getHistory(@PathVariable String userId) {
        return chatMemory.get(userId);
    }

    @DeleteMapping("/{userId}/history")
    public String clearHistory(@PathVariable String userId) {
        chatMemory.clear(userId);

        return "Conversation history cleared for user: " + userId;
    }

}
