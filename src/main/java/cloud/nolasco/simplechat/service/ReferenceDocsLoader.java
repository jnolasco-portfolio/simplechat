package cloud.nolasco.simplechat.service;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ReferenceDocsLoader {

  private final JdbcClient jdbcClient;
  private final VectorStore vectorStore;
  @Value("classpath:/context/developer-profile.md")
  private Resource developerProfile;

  @Value("classpath:/context/project-overview.md")
  private Resource website;

  @Value("classpath:/context/project-brochure.md")
  private Resource brochure;

  @Value("classpath:/context/project-inventory.md")
  private Resource projectInventory;

  public ReferenceDocsLoader(JdbcClient jdbcClient, VectorStore vectorStore) {
    this.jdbcClient = jdbcClient;
    this.vectorStore = vectorStore;
  }

  @PostConstruct
  public void init() {
    try {
      Integer count = jdbcClient.sql("select count(*) from vector_store")
          .query(Integer.class)
          .single();

      log.info("Current count of the Vector Store: {}", count);
      if (count == 0) {
        log.info("Loading context documents into Vector Store");
        // loadData(vectorStore, developerProfile);
        safeLoad(developerProfile);
        safeLoad(website);
        safeLoad(brochure);
        safeLoad(projectInventory);

        log.info("Application is ready");
      }
    } catch (Exception e) {
      log.warn("Failed to initialize vector store with reference documents. " +
          "The application will start, but RAG functionality may be limited. " +
          "Error: {}", e.getMessage());
      log.debug("Full exception details: ", e);
    }
  }

  private void safeLoad(Resource resource) {
    try {
      loadData(vectorStore, resource);
    } catch (Exception e) {
      Throwable root = e;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }
      log.warn("Skipping reference document '{}' due to error: {} (root cause: {}: {})",
          resource.getFilename(), e.getMessage(),
          root.getClass().getName(), root.getMessage());
      log.warn("Full exception chain for '{}':", resource.getFilename(), e);
    }
  }

  private void loadData(VectorStore vectorStore, Resource resource) {
    try {
      TextReader textReader = new TextReader(resource);
      textReader.getCustomMetadata().put("filename", resource.getFilename());

      List<Document> documents = textReader.get();
      TokenTextSplitter textSplitter = TokenTextSplitter.builder().withChunkSize(128).build();
      vectorStore.accept(textSplitter.apply(documents));

    } catch (Exception e) {
      throw new RuntimeException("Error loading data from resource: " + resource.getFilename(), e);
    }

  }
}
