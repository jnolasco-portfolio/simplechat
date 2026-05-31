package cloud.nolasco.simplechat.config;

import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RagConfiguration {

  @Value("classPath:/context/developer-profile.md")
  private Resource developerProfile;

  @Value("classPath:/context/project-overview.md")
  private Resource website;

  @Value("classPath:/context/project-brochure.md")
  private Resource brochure;

  @Value("vectorstore.json")
  private String vectorStoreName;

  @Bean
  public SimpleVectorStore vectorStore(EmbeddingModel embedddelingModel) {

    SimpleVectorStore vectorStore = SimpleVectorStore.builder(embedddelingModel)
        .build();

    File vectorStoreFile = getVectorStoreFile();
    if (vectorStoreFile.exists()) {
      log.info("Vector Store file exists");
      vectorStore.load(vectorStoreFile);
    } else {
      log.info("Vector Store file does not exist, starting with an empty one.");
      loadData(vectorStore, developerProfile);
      loadData(vectorStore, website);
      loadData(vectorStore, brochure);
      vectorStore.save(getVectorStoreFile());
    }

    return vectorStore;
  }

  private void loadData(SimpleVectorStore vectorStore, Resource resource) {
    try {
      TextReader textReader = new TextReader(resource);
      textReader.getCustomMetadata().put("filename", resource.getFilename());

      List<Document> documents = textReader.get();
      TokenTextSplitter textSplitter = TokenTextSplitter.builder().withChunkSize(128).build();
      List<Document> splitDocuments = textSplitter.apply(documents);

      vectorStore.add(splitDocuments);

    } catch (Exception e) {
      throw new RuntimeException("Error loading data from resource: " + resource.getFilename(), e);
    }
  }

  private File getVectorStoreFile() {
    Path path = Paths.get("src", "main", "resources", "data");
    String absolutePath = path.toFile().getAbsolutePath() + "/" + vectorStoreName;
    return new File(absolutePath);
  }

}
