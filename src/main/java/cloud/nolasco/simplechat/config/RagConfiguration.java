package cloud.nolasco.simplechat.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
// @Configuration
public class RagConfiguration {

  // @Bean
  public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embedddelingModel) {

    PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embedddelingModel)
        .build();

    return vectorStore;
  }
}
