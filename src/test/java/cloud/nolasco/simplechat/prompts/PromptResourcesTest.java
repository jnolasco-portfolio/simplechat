package cloud.nolasco.simplechat.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PromptResourcesTest {

  @Test
  void ragPromptKeepsAdvisorPlaceholders() throws IOException {
    String prompt = read("/prompts/rag-prompt.st");

    assertThat(prompt)
        .contains("{query}")
        .contains("{question_answer_context}")
        .contains("Status: Available")
        .contains("Residence <ID>");
  }

  @Test
  void systemPromptKeepsGreetingVariables() throws IOException {
    String prompt = read("/prompts/system-prompt.st");

    assertThat(prompt)
        .contains("{greeting}")
        .contains("{assistantName}")
        .contains("{DevelopmentName}");
  }

  private static String read(String path) throws IOException {
    ClassPathResource resource = new ClassPathResource(path);
    assertThat(resource.exists()).as("%s must be on the classpath", path).isTrue();
    return new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
