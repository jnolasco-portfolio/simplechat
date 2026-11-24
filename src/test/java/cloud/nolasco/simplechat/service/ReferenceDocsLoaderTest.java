package cloud.nolasco.simplechat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;

class ReferenceDocsLoaderTest {

  private JdbcClient jdbcClient;
  private VectorStore vectorStore;
  private ReferenceDocsLoader loader;

  @BeforeEach
  void setUp() {
    jdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
    vectorStore = mock(VectorStore.class);
    loader = new ReferenceDocsLoader(jdbcClient, vectorStore);

    ReflectionTestUtils.setField(loader, "developerProfile",
        new ClassPathResource("/context/developer-profile.md"));
    ReflectionTestUtils.setField(loader, "projectOverview",
        new ClassPathResource("/context/project-overview.md"));
    ReflectionTestUtils.setField(loader, "projectBrochure",
        new ClassPathResource("/context/project-brochure.md"));
    ReflectionTestUtils.setField(loader, "projectInventory",
        new ClassPathResource("/context/project-inventory.md"));
  }

  @Test
  void loadsOneWholeDocumentPerContextFileWhenStoreIsEmpty() {
    when(jdbcClient.sql(anyString()).query(Integer.class).single()).thenReturn(0);

    loader.init();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore, times(4)).accept(captor.capture());

    List<Document> documents = captor.getAllValues().stream().flatMap(List::stream).toList();
    assertThat(documents).hasSize(4);
    assertThat(documents)
        .extracting(document -> document.getMetadata().get("filename"))
        .containsExactlyInAnyOrder(
            "developer-profile.md",
            "project-overview.md",
            "project-brochure.md",
            "project-inventory.md");
    assertThat(documents)
        .anySatisfy(document -> assertThat(document.getText()).contains("Residence B-18"));
  }

  @Test
  void skipsLoadingWhenStoreIsNotEmpty() {
    when(jdbcClient.sql(anyString()).query(Integer.class).single()).thenReturn(1);

    loader.init();

    verifyNoInteractions(vectorStore);
  }

  @Test
  void continuesAfterOneDocumentFails() {
    when(jdbcClient.sql(anyString()).query(Integer.class).single()).thenReturn(0);
    doThrow(new RuntimeException("bad document"))
        .doNothing().doNothing().doNothing()
        .when(vectorStore).accept(anyList());

    assertThatCode(loader::init).doesNotThrowAnyException();

    verify(vectorStore, times(4)).accept(anyList());
  }

  @Test
  void remainsQuietWhenCountQueryFails() {
    when(jdbcClient.sql(anyString()).query(Integer.class).single())
        .thenThrow(new RuntimeException("no table"));

    assertThatCode(loader::init).doesNotThrowAnyException();

    verifyNoInteractions(vectorStore);
  }
}
