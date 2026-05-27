package cloud.nolasco.simplechat;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@CrossOrigin
public class ChatController {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public ChatController(ChatClient chatClient, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    /*
     * @GetMapping("/stream")
     * public Flux<String> chatWithStream(@RequestParam String message) {
     * return chatClient.prompt()
     * .user(message)
     * .stream()
     * .content();
     * }
     */
    @PostMapping("/stream/{userId}/chat")
    public Flux<String> chat(
            @PathVariable String userId,
            @RequestBody String request) {

        return chatClient
                .prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, userId))
                .user(request)
                .stream()
                .content();
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
