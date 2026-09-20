package com.wc.knowledge;

import com.wc.config.MinioInfo;
import com.wc.entity.KnowledgeDocument;
import com.wc.entity.KnowledgeDocumentAccess;
import com.wc.entity.KnowledgeDocumentVisibility;
import com.wc.mapper.KnowledgeChunkMapper;
import com.wc.mapper.KnowledgeDocumentAccessMapper;
import com.wc.mapper.KnowledgeDocumentMapper;
import com.wc.mapper.KnowledgeDocumentVisibilityMapper;
import com.wc.mapper.UserInfoMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Ensures the service, rather than a browser-supplied document id, enforces RAG visibility. */
class KnowledgeVisibilityTests {
    private KnowledgeDocumentMapper documents;
    private KnowledgeDocumentAccessMapper accesses;
    private KnowledgeRagClient ragClient;
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        documents = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        KnowledgeDocumentVisibilityMapper visibilities = mock(KnowledgeDocumentVisibilityMapper.class);
        accesses = mock(KnowledgeDocumentAccessMapper.class);
        ragClient = mock(KnowledgeRagClient.class);
        service = new KnowledgeService(documents, chunks, visibilities, accesses,
                mock(UserInfoMapper.class), ragClient, mock(MinioClient.class), mock(MinioInfo.class));

        when(documents.selectList(any())).thenReturn(List.of(
                document(101L, 1), document(102L, 1), document(103L, 1)
        ));
        when(visibilities.selectList(any())).thenReturn(List.of(
                visibility(101L, "PRIVATE"), visibility(102L, "PUBLIC"), visibility(103L, "TEAM")
        ));
        when(ragClient.search(anyString(), any(), anyInt())).thenReturn(List.of());
    }

    @Test
    void ordinaryUserCanOnlySendPublicDocumentsToRag() {
        when(accesses.selectList(any())).thenReturn(List.of());

        service.retrieve(2, "校园服务时间");

        verify(ragClient).search(anyString(), documentIds(Set.of(102L)), anyInt());
    }

    @Test
    void namedTeamMemberCanAlsoSendTeamDocumentToRag() {
        when(accesses.selectList(any())).thenReturn(List.of(access(103L, 2)));

        service.retrieve(2, "校园服务时间");

        verify(ragClient).search(anyString(), documentIds(Set.of(102L, 103L)), anyInt());
    }

    @Test
    void noReadableDocumentsNeverReachRag() {
        when(documents.selectList(any())).thenReturn(List.of(document(101L, 1)));
        when(accesses.selectList(any())).thenReturn(List.of());

        service.retrieve(2, "校园服务时间");

        verifyNoInteractions(ragClient);
    }

    @SuppressWarnings("unchecked")
    private static Collection<Long> documentIds(Set<Long> expected) {
        return org.mockito.ArgumentMatchers.argThat((Collection<Long> actual) -> actual != null && Set.copyOf(actual).equals(expected));
    }

    private static KnowledgeDocument document(long id, int ownerUserId) {
        KnowledgeDocument value = new KnowledgeDocument();
        value.setId(id);
        value.setOwnerUserId(ownerUserId);
        value.setStatus("PUBLISHED");
        return value;
    }

    private static KnowledgeDocumentVisibility visibility(long documentId, String scope) {
        KnowledgeDocumentVisibility value = new KnowledgeDocumentVisibility();
        value.setDocumentId(documentId);
        value.setVisibilityScope(scope);
        return value;
    }

    private static KnowledgeDocumentAccess access(long documentId, int userId) {
        KnowledgeDocumentAccess value = new KnowledgeDocumentAccess();
        value.setDocumentId(documentId);
        value.setUserId(userId);
        return value;
    }
}
