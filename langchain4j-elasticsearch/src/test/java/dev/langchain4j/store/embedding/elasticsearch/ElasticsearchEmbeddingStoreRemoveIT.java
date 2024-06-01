package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.*;

//@Testcontainers
public class ElasticsearchEmbeddingStoreRemoveIT {

    @Container
    //    private static final ElasticsearchContainer elasticsearch =
    //            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.9.0")
    //                    .withEnv("xpack.security.enabled", "false");

    EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
            .serverUrl("http://127.0.0.1:9200")
            .indexName(randomUUID())
            .dimension(384)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void beforeEach() {
        embeddingStore.removeAll();
    }

    @Test
    void remove_all() {
        // given
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        embeddingStore.add(embedding);
        embeddingStore.add(embedding2);
        embeddingStore.add(embedding3);

        awaitUntilPersisted();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();

        assertThat(embeddingStore.search(request).matches()).hasSize(3);

        // when
        embeddingStore.removeAll();
        awaitUntilPersisted();

        // then
        assertThat(embeddingStore.search(request).matches()).hasSize(0);

    }

    @Test
    void remove_by_id() {
        // given
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        String id = embeddingStore.add(embedding);
        String id2 = embeddingStore.add(embedding2);
        String id3 = embeddingStore.add(embedding3);
        awaitUntilPersisted();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();

        assertThat(embeddingStore.search(request).matches()).hasSize(3);

        // when
        embeddingStore.remove(id);
        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        List<String> matchingIds = matches.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(matchingIds).hasSize(2);
        assertThat(matchingIds).containsExactly(id2, id3);
    }

    @Test
    void remove_by_all_ids() {
        // given
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        String id = embeddingStore.add(embedding);
        String id2 = embeddingStore.add(embedding2);
        String id3 = embeddingStore.add(embedding3);
        awaitUntilPersisted();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();

        assertThat(embeddingStore.search(request).matches()).hasSize(3);

        // when
        embeddingStore.removeAll(Arrays.asList(id2, id3));
        awaitUntilPersisted();

        // then
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        List<String> matchingIds = matches.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(matchingIds).hasSize(1);
        assertThat(matchingIds).containsExactly(id);
    }

    @Test
    void remove_all_by_ids_null() {
        assertThatThrownBy(() -> embeddingStore.removeAll((Collection<String>) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids cannot be null or empty");
    }

    @Test
    void remove_all_by_filter() {
        // given
        Metadata metadata = Metadata.metadata("id", "1");
        TextSegment segment = TextSegment.from("matching", metadata);
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);
        awaitUntilPersisted();

        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        String id2 = embeddingStore.add(embedding2);
        String id3 = embeddingStore.add(embedding3);
        awaitUntilPersisted();

        // when
        embeddingStore.removeAll(metadataKey("id").isEqualTo("1"));
        awaitUntilPersisted();

        // then
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        List<String> matchingIds = matches.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(matchingIds).hasSize(2);
        assertThat(matchingIds).containsExactly(id2, id3);
    }

    @Test
    void remove_all_by_filter_not_matching() {
        // given
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        embeddingStore.add(embedding);
        embeddingStore.add(embedding2);
        embeddingStore.add(embedding3);
        awaitUntilPersisted();

        // when
        embeddingStore.removeAll(metadataKey("unknown").isEqualTo("1"));
        awaitUntilPersisted();

        // then
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        List<String> matchingIds = matches.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(matchingIds).hasSize(3);
    }


    @Test
    void remove_all_by_filter_null() {
        assertThatThrownBy(() -> embeddingStore.removeAll((Filter) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("filter cannot be null");
    }

    @SneakyThrows
    protected void awaitUntilPersisted() {
        Thread.sleep(3000);
    }


}