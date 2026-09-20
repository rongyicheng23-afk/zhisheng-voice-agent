package com.wc.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wc.config.MinioInfo;
import com.wc.entity.KnowledgeChunk;
import com.wc.entity.KnowledgeDocumentAccess;
import com.wc.entity.KnowledgeDocument;
import com.wc.entity.KnowledgeDocumentVisibility;
import com.wc.entity.UserInfo;
import com.wc.mapper.KnowledgeDocumentAccessMapper;
import com.wc.mapper.KnowledgeChunkMapper;
import com.wc.mapper.KnowledgeDocumentMapper;
import com.wc.mapper.KnowledgeDocumentVisibilityMapper;
import com.wc.mapper.UserInfoMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int CHUNK_CHARS = 450;
    private static final int CHUNK_OVERLAP = 80;

    private final KnowledgeDocumentMapper documents;
    private final KnowledgeChunkMapper chunks;
    private final KnowledgeDocumentVisibilityMapper visibilities;
    private final KnowledgeDocumentAccessMapper accesses;
    private final UserInfoMapper users;
    private final KnowledgeRagClient ragClient;
    private final MinioClient minioClient;
    private final MinioInfo minioInfo;

    public KnowledgeService(
            KnowledgeDocumentMapper documents,
            KnowledgeChunkMapper chunks,
            KnowledgeDocumentVisibilityMapper visibilities,
            KnowledgeDocumentAccessMapper accesses,
            UserInfoMapper users,
            KnowledgeRagClient ragClient,
            MinioClient minioClient,
            MinioInfo minioInfo
    ) {
        this.documents = documents;
        this.chunks = chunks;
        this.visibilities = visibilities;
        this.accesses = accesses;
        this.users = users;
        this.ragClient = ragClient;
        this.minioClient = minioClient;
        this.minioInfo = minioInfo;
    }

    public Map<String, Object> upload(
            Integer userId, MultipartFile file, String title, String sourceName, String version,
            LocalDate validFrom, LocalDate validUntil, String visibilityScope, Collection<Integer> teamMemberIds
    ) throws Exception {
        validateUpload(file, validFrom, validUntil);
        validateVisibilityRequest(visibilityScope, teamMemberIds, userId);
        byte[] bytes = file.getBytes();
        String filename = safeFilename(file.getOriginalFilename());
        String text = extractText(filename, bytes);
        List<String> parts = splitText(text);
        if (parts.isEmpty()) throw new IllegalArgumentException("资料没有可索引的文本内容");

        KnowledgeDocument document = new KnowledgeDocument();
        document.setOwnerUserId(userId);
        document.setTitle(StringUtils.hasText(title) ? title.trim() : filename);
        document.setSourceName(StringUtils.hasText(sourceName) ? sourceName.trim() : "用户上传资料");
        document.setVersionLabel(StringUtils.hasText(version) ? version.trim() : "v1");
        document.setValidFrom(validFrom);
        document.setValidUntil(validUntil);
        document.setStatus("DRAFT");
        document.setBucketName(minioInfo.getBucket());
        document.setOriginalFilename(filename);
        document.setContentType(normalizeContentType(file.getContentType()));
        document.setFileSize((long) bytes.length);
        // The database requires a storage key at creation time. A UUID keeps
        // the object private and collision-free without depending on the yet
        // unknown auto-increment document id.
        document.setObjectName("knowledge/" + userId + "/" + UUID.randomUUID() + "-" + filename);
        document.setCreateTime(new Date());
        document.setUpdateTime(new Date());
        documents.insert(document);
        saveVisibility(document.getId(), visibilityScope, teamMemberIds, userId);

        try {
            ensureBucket(document.getBucketName());
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(document.getBucketName())
                    .object(document.getObjectName())
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(document.getContentType())
                    .build());
            for (int index = 0; index < parts.size(); index++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(document.getId());
                chunk.setChunkNo(index + 1);
                chunk.setContent(parts.get(index));
                chunk.setContentHash(sha256(parts.get(index)));
                chunk.setCreateTime(new Date());
                chunks.insert(chunk);
            }
        } catch (Exception error) {
            accesses.delete(new LambdaQueryWrapper<KnowledgeDocumentAccess>().eq(KnowledgeDocumentAccess::getDocumentId, document.getId()));
            visibilities.deleteById(document.getId());
            documents.deleteById(document.getId());
            throw error;
        }
        return documentView(document, parts.size());
    }

    public Map<String, Object> publish(Integer userId, Long documentId) {
        KnowledgeDocument document = requireOwner(userId, documentId);
        if (!"REVIEW".equals(document.getStatus()) && !"DRAFT".equals(document.getStatus())) {
            throw new IllegalArgumentException("只有待审核资料可以发布");
        }
        return indexAndPublish(document);
    }

    public Map<String, Object> submitReview(Integer userId, Long documentId) {
        KnowledgeDocument document = requireOwner(userId, documentId);
        if (!"DRAFT".equals(document.getStatus())) throw new IllegalArgumentException("只有草稿可以提交审核");
        document.setStatus("REVIEW");
        document.setUpdateTime(new Date());
        documents.updateById(document);
        return documentView(document, documentChunks(documentId).size());
    }

    public Map<String, Object> approve(Integer userId, Long documentId) {
        KnowledgeDocument document = requireOwner(userId, documentId);
        if (!"REVIEW".equals(document.getStatus())) throw new IllegalArgumentException("请先将资料提交审核");
        return indexAndPublish(document);
    }

    public Map<String, Object> offline(Integer userId, Long documentId) {
        KnowledgeDocument document = requireOwner(userId, documentId);
        if (!"PUBLISHED".equals(document.getStatus())) throw new IllegalArgumentException("只有已发布资料可以下架");
        document.setStatus("OFFLINE");
        document.setUpdateTime(new Date());
        documents.updateById(document);
        return documentView(document, documentChunks(documentId).size());
    }

    /**
     * An offline document must be reviewed again before it can be published.
     * Moving it back to a draft keeps that lifecycle explicit instead of
     * letting the UI submit an invalid OFFLINE -> REVIEW transition.
     */
    public Map<String, Object> restoreToDraft(Integer userId, Long documentId) {
        KnowledgeDocument document = requireOwner(userId, documentId);
        if (!"OFFLINE".equals(document.getStatus())) throw new IllegalArgumentException("只有已下架资料可以恢复为草稿");
        document.setStatus("DRAFT");
        document.setUpdateTime(new Date());
        documents.updateById(document);
        return documentView(document, documentChunks(documentId).size());
    }

    public Map<String, Object> reindex(Integer userId, Long documentId) {
        KnowledgeDocument document = requireOwner(userId, documentId);
        if (!"PUBLISHED".equals(document.getStatus())) throw new IllegalArgumentException("请先发布资料，再重新索引");
        return indexAndPublish(document);
    }

    private Map<String, Object> indexAndPublish(KnowledgeDocument document) {
        Long documentId = document.getId();
        List<KnowledgeChunk> documentChunks = documentChunks(documentId);
        if (documentChunks.isEmpty()) throw new IllegalArgumentException("资料尚未完成切片，不能发布");
        ragClient.index(documentChunks);
        document.setStatus("PUBLISHED");
        document.setUpdateTime(new Date());
        documents.updateById(document);
        return documentView(document, documentChunks.size());
    }

    public List<Map<String, Object>> list(Integer userId) {
        List<KnowledgeDocument> result = documents.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getOwnerUserId, userId)
                .orderByDesc(KnowledgeDocument::getUpdateTime)
                .orderByDesc(KnowledgeDocument::getId));
        List<Map<String, Object>> views = new ArrayList<>();
        for (KnowledgeDocument document : result) {
            views.add(documentView(document, documentChunks(document.getId()).size()));
        }
        return views;
    }

    public Map<String, Object> retrieve(Integer userId, String query) {
        if (!StringUtils.hasText(query)) throw new IllegalArgumentException("检索问题不能为空");
        LocalDate today = LocalDate.now();
        List<KnowledgeDocument> published = documents.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getStatus, "PUBLISHED")
                .and(wrapper -> wrapper.isNull(KnowledgeDocument::getValidFrom)
                        .or().le(KnowledgeDocument::getValidFrom, today))
                .and(wrapper -> wrapper.isNull(KnowledgeDocument::getValidUntil)
                        .or().ge(KnowledgeDocument::getValidUntil, today)));
        List<KnowledgeDocument> allowed = filterReadableDocuments(userId, published);
        if (allowed.isEmpty()) return Map.of("context", "", "citations", Map.of(), "results", List.of());

        Map<Long, KnowledgeDocument> documentsById = new LinkedHashMap<>();
        for (KnowledgeDocument document : allowed) documentsById.put(document.getId(), document);
        List<KnowledgeRagClient.RagHit> hits = ragClient.search(query.trim(), documentsById.keySet(), 4);
        if (hits.isEmpty()) return Map.of("context", "", "citations", Map.of(), "results", List.of());

        List<Long> chunkIds = hits.stream().map(KnowledgeRagClient.RagHit::chunkId).toList();
        Map<Long, KnowledgeChunk> chunksById = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : chunks.selectByIds(chunkIds)) chunksById.put(chunk.getId(), chunk);
        Map<String, Object> citations = new LinkedHashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        int citationNumber = 1;
        for (KnowledgeRagClient.RagHit hit : hits) {
            KnowledgeDocument document = documentsById.get(hit.documentId());
            KnowledgeChunk chunk = chunksById.get(hit.chunkId());
            if (document == null || chunk == null || !document.getId().equals(chunk.getDocumentId())) continue;
            String citationId = String.format("C%03d", citationNumber++);
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("documentId", document.getId());
            citation.put("title", document.getTitle());
            citation.put("source", document.getSourceName());
            citation.put("version", document.getVersionLabel());
            citation.put("validFrom", document.getValidFrom());
            citation.put("validUntil", document.getValidUntil());
            citation.put("chunkId", chunk.getId());
            citation.put("chunkNo", chunk.getChunkNo());
            citation.put("page", chunk.getPageNo());
            citation.put("excerpt", chunk.getContent());
            citation.put("score", hit.score());
            citations.put(citationId, citation);
            results.add(Map.of("citationId", citationId, "score", hit.score()));
            context.append("[").append(citationId).append("] ")
                    .append(chunk.getContent()).append("\n\n");
        }
        return Map.of("context", context.toString().trim(), "citations", citations, "results", results);
    }

    /**
     * Updates visibility and the explicit TEAM recipients. This method is
     * transactional so a partially updated member list can never widen access.
     */
    @Transactional
    public Map<String, Object> updateVisibility(Integer userId, Long documentId, String visibilityScope,
                                                Collection<Integer> teamMemberIds) {
        KnowledgeDocument document = requireOwner(userId, documentId);
        saveVisibility(documentId, visibilityScope, teamMemberIds, userId);
        return documentView(document, documentChunks(documentId).size());
    }

    private List<KnowledgeDocument> filterReadableDocuments(Integer userId, List<KnowledgeDocument> published) {
        if (published.isEmpty()) return List.of();
        Set<Long> documentIds = published.stream().map(KnowledgeDocument::getId).collect(java.util.stream.Collectors.toSet());
        Map<Long, String> scopes = new HashMap<>();
        visibilities.selectList(new LambdaQueryWrapper<KnowledgeDocumentVisibility>()
                        .in(KnowledgeDocumentVisibility::getDocumentId, documentIds))
                .forEach(item -> scopes.put(item.getDocumentId(), normalizeVisibility(item.getVisibilityScope())));
        Set<Long> teamAllowedDocumentIds = new HashSet<>();
        accesses.selectList(new LambdaQueryWrapper<KnowledgeDocumentAccess>()
                        .eq(KnowledgeDocumentAccess::getUserId, userId)
                        .in(KnowledgeDocumentAccess::getDocumentId, documentIds))
                .forEach(item -> teamAllowedDocumentIds.add(item.getDocumentId()));
        List<KnowledgeDocument> result = new ArrayList<>();
        for (KnowledgeDocument document : published) {
            String scope = scopes.getOrDefault(document.getId(), "PRIVATE");
            if (userId.equals(document.getOwnerUserId()) || "PUBLIC".equals(scope)
                    || ("TEAM".equals(scope) && teamAllowedDocumentIds.contains(document.getId()))) {
                result.add(document);
            }
        }
        return result;
    }

    private void saveVisibility(Long documentId, String requestedScope, Collection<Integer> requestedMembers,
                                Integer ownerUserId) {
        String scope = normalizeVisibility(requestedScope);
        Set<Integer> members = normalizeMembers(requestedMembers, ownerUserId);
        if ("TEAM".equals(scope) && members.isEmpty()) {
            throw new IllegalArgumentException("TEAM 范围至少需要授权一名其他用户");
        }
        KnowledgeDocumentVisibility visibility = new KnowledgeDocumentVisibility();
        visibility.setDocumentId(documentId);
        visibility.setVisibilityScope(scope);
        visibility.setUpdateTime(new Date());
        if (visibilities.selectById(documentId) == null) visibilities.insert(visibility);
        else visibilities.updateById(visibility);
        accesses.delete(new LambdaQueryWrapper<KnowledgeDocumentAccess>().eq(KnowledgeDocumentAccess::getDocumentId, documentId));
        if ("TEAM".equals(scope)) for (Integer memberId : members) {
            KnowledgeDocumentAccess access = new KnowledgeDocumentAccess();
            access.setDocumentId(documentId);
            access.setUserId(memberId);
            access.setCreateTime(new Date());
            accesses.insert(access);
        }
    }

    private void validateVisibilityRequest(String requestedScope, Collection<Integer> requestedMembers,
                                           Integer ownerUserId) {
        String scope = normalizeVisibility(requestedScope);
        Set<Integer> members = normalizeMembers(requestedMembers, ownerUserId);
        if ("TEAM".equals(scope) && members.isEmpty()) {
            throw new IllegalArgumentException("TEAM 范围至少需要授权一名其他用户");
        }
    }

    private Set<Integer> normalizeMembers(Collection<Integer> requestedMembers, Integer ownerUserId) {
        Set<Integer> members = new LinkedHashSet<>();
        if (requestedMembers == null) return members;
        for (Integer memberId : requestedMembers) {
            if (memberId == null || memberId <= 0 || memberId.equals(ownerUserId)) continue;
            UserInfo user = users.selectById(memberId);
            if (user == null) throw new IllegalArgumentException("授权用户不存在：" + memberId);
            members.add(memberId);
        }
        return members;
    }

    private static String normalizeVisibility(String value) {
        String scope = StringUtils.hasText(value) ? value.trim().toUpperCase() : "PRIVATE";
        if (!scope.equals("PRIVATE") && !scope.equals("TEAM") && !scope.equals("PUBLIC")) {
            throw new IllegalArgumentException("可见范围只能是 PRIVATE、TEAM 或 PUBLIC");
        }
        return scope;
    }

    private KnowledgeDocument requireOwner(Integer userId, Long documentId) {
        KnowledgeDocument document = documents.selectById(documentId);
        if (document == null || !userId.equals(document.getOwnerUserId())) {
            throw new IllegalArgumentException("资料不存在或无权操作");
        }
        return document;
    }

    private List<KnowledgeChunk> documentChunks(Long documentId) {
        return chunks.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId)
                .orderByAsc(KnowledgeChunk::getChunkNo));
    }

    private void ensureBucket(String bucket) throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private void validateUpload(MultipartFile file, LocalDate validFrom, LocalDate validUntil) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择资料文件");
        if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("资料文件不能超过 10MB");
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("有效期结束日期不能早于开始日期");
        }
        String extension = extension(safeFilename(file.getOriginalFilename()));
        if (!extension.equals("txt") && !extension.equals("md") && !extension.equals("docx")) {
            throw new IllegalArgumentException("首期仅支持 txt、md、docx；扫描件 PDF 需在 OCR 阶段接入");
        }
    }

    private String extractText(String filename, byte[] bytes) throws Exception {
        String extension = extension(filename);
        if (extension.equals("txt") || extension.equals("md")) return new String(bytes, StandardCharsets.UTF_8).trim();
        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder text = new StringBuilder();
            word.getParagraphs().forEach(paragraph -> appendLine(text, paragraph.getText()));
            for (XWPFTable table : word.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) appendLine(text, cell.getText());
                }
            }
            return text.toString().trim();
        }
    }

    private List<String> splitText(String raw) {
        String text = raw.replaceAll("\\s+", " ").trim();
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_CHARS);
            if (end < text.length()) {
                int boundary = Math.max(text.lastIndexOf('。', end), Math.max(text.lastIndexOf('！', end), text.lastIndexOf('？', end)));
                if (boundary >= start + 200) end = boundary + 1;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) result.add(chunk);
            if (end >= text.length()) break;
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return result;
    }

    private Map<String, Object> documentView(KnowledgeDocument document, int chunkCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", document.getId());
        result.put("title", document.getTitle());
        result.put("source", document.getSourceName());
        result.put("version", document.getVersionLabel());
        result.put("status", document.getStatus());
        result.put("validFrom", document.getValidFrom());
        result.put("validUntil", document.getValidUntil());
        result.put("filename", document.getOriginalFilename());
        result.put("chunkCount", chunkCount);
        result.put("visibility", visibilityOf(document.getId()));
        result.put("teamMemberIds", teamMemberIds(document.getId()));
        return result;
    }

    private String visibilityOf(Long documentId) {
        KnowledgeDocumentVisibility visibility = visibilities.selectById(documentId);
        return visibility == null ? "PRIVATE" : normalizeVisibility(visibility.getVisibilityScope());
    }

    private List<Integer> teamMemberIds(Long documentId) {
        return accesses.selectList(new LambdaQueryWrapper<KnowledgeDocumentAccess>()
                        .eq(KnowledgeDocumentAccess::getDocumentId, documentId)
                        .orderByAsc(KnowledgeDocumentAccess::getUserId))
                .stream().map(KnowledgeDocumentAccess::getUserId).toList();
    }

    private static void appendLine(StringBuilder target, String value) {
        if (StringUtils.hasText(value)) target.append(value.trim()).append('\n');
    }

    private static String safeFilename(String value) {
        String filename = StringUtils.hasText(value) ? value.replace('\\', '/').substring(value.replace('\\', '/').lastIndexOf('/') + 1) : "document.txt";
        return filename.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]", "_");
    }

    private static String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase();
    }

    private static String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }

    private static String sha256(String content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) result.append(String.format("%02x", value));
        return result.toString();
    }
}
