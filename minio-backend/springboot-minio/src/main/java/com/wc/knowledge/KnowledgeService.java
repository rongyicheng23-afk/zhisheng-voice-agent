package com.wc.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.net.URI;
import java.util.*;

/** Small private corpus, immutable document bodies and explicit publication. */
@Service
public class KnowledgeService {
    public record Draft(String title, String sourceUrl, String publisher, String sourceVersion,
                        LocalDate validFrom, LocalDate validUntil, String content, String seriesId, String requestId) {
        public Draft(String title, String sourceUrl, String publisher, String sourceVersion,
                     LocalDate validFrom, LocalDate validUntil, String content, String seriesId) {
            this(title, sourceUrl, publisher, sourceVersion, validFrom, validUntil, content, seriesId, null);
        }
    }
    public record Document(String id, String seriesId, String title, String sourceUrl, String publisher,
                           String sourceVersion, LocalDate validFrom, LocalDate validUntil,
                           String content, String status, int revision) {}
    public record Citation(String id, String documentId, String title, String sourceUrl, String publisher,
                           String sourceVersion, LocalDate validFrom, LocalDate validUntil,
                           int paragraph, String quote) {}
    public record SearchResult(String mode, String message, List<Citation> citations) {}
    public record PublicationReview(Document candidate, List<Document> replaced, LocalDate checkedOn,
                                    boolean eligible, String message, String reviewToken,
                                    KnowledgeComparison.Result comparison) {}
    private final JdbcTemplate db;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public KnowledgeService(JdbcTemplate db) { this(db, Clock.system(ZoneId.of("Asia/Shanghai"))); }
    KnowledgeService(JdbcTemplate db, Clock clock) { this.db = db; this.clock = clock; }

    public List<Document> list(int owner) {
        owner(owner);
        return db.query("SELECT * FROM knowledge_document WHERE owner_id=? ORDER BY created_at DESC,id LIMIT 100",
                (r, n) -> new Document(r.getString("id"), r.getString("series_id"), r.getString("title"),
                        r.getString("source_url"), r.getString("publisher"), r.getString("source_version"),
                        r.getDate("valid_from").toLocalDate(), r.getDate("valid_until").toLocalDate(),
                        r.getString("content"), r.getString("status"), r.getInt("revision")), owner);
    }

    private void lock(int owner) {
        owner(owner);
        db.update("INSERT IGNORE INTO knowledge_space(owner_id) VALUES (?)", owner);
        db.queryForObject("SELECT owner_id FROM knowledge_space WHERE owner_id=? FOR UPDATE", Integer.class, owner);
    }

    public KnowledgeComparison.Result compare(int owner, String beforeId, String afterId) {
        List<Document> documents = list(owner);
        Document before = find(documents, beforeId), after = find(documents, afterId);
        if (!before.seriesId.equals(after.seriesId)) throw bad("只能比较同一资料的不同版本");
        return KnowledgeComparison.compare(before, after);
    }

    public PublicationReview review(int owner, String id) {
        List<Document> documents = list(owner);
        Document candidate = find(documents, id);
        List<Document> replaced = documents.stream().filter(d -> d.seriesId.equals(candidate.seriesId)
                && !d.id.equals(id) && d.status.equals("PUBLISHED")).sorted(Comparator.comparing(Document::id)).toList();
        LocalDate today = LocalDate.now(clock);
        boolean eligible = !candidate.status.equals("PUBLISHED") && !today.isBefore(candidate.validFrom) && !today.isAfter(candidate.validUntil);
        String message = candidate.status.equals("PUBLISHED") ? "此版本已经发布" : !eligible ? "此版本未生效或已过期，不能发布"
                : replaced.isEmpty() ? "发布后此版本参与有效资料检索" : "确认发布将下架下列已发布版本，旧正文仍保留";
        String fingerprint = owner + ":" + today + ":" + candidate.id + ":" + candidate.revision + ":" + candidate.status;
        for (Document d : replaced) fingerprint += ":" + d.id + ":" + d.revision;
        String token;
        try {
            token = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        return new PublicationReview(candidate, replaced, today, eligible, message, token,
                replaced.size() == 1 ? KnowledgeComparison.compare(replaced.get(0), candidate) : null);
    }

    @Transactional
    public Document publishReviewed(int owner, String id, String reviewToken) {
        lock(owner);
        PublicationReview current = review(owner, id);
        if (reviewToken == null || !current.reviewToken.equals(reviewToken))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "核对期间资料状态或日期已变化，请重新预览");
        if (!current.eligible) throw bad(current.message);
        return transition(owner, id, "PUBLISHED", current.candidate.revision);
    }

    private static Document find(List<Document> documents, String id) {
        return documents.stream().filter(d -> d.id.equals(id)).findFirst().orElseThrow(KnowledgeService::missing);
    }

    @Transactional
    public Document create(int owner, Draft draft) {
        if (draft == null) throw bad("资料不能为空");
        String title = required(draft.title, 160), publisher = required(draft.publisher, 160);
        String version = required(draft.sourceVersion, 80), content = required(draft.content, 20000);
        String url = draft.sourceUrl == null ? "" : draft.sourceUrl.trim();
        if (!url.isEmpty()) {
            try {
                URI uri = URI.create(url);
                if (url.length() > 1000 || !List.of("https", "http").contains(uri.getScheme())
                        || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw bad("来源地址仅支持不含账号信息的 HTTP/HTTPS 链接"); }
        }
        if (draft.validFrom == null || draft.validUntil == null || draft.validUntil.isBefore(draft.validFrom)
                || draft.validFrom.getYear() < 1000 || draft.validUntil.getYear() > 9999)
            throw bad("请填写有效的生效日期和截止日期");
        String id;
        if (draft.requestId == null) id = UUID.randomUUID().toString(); // Legacy clients.
        else {
            if (!draft.requestId.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) throw bad("无效的保存请求编号");
            id = UUID.nameUUIDFromBytes((owner + ":knowledge:" + draft.requestId.toLowerCase(Locale.ROOT))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        }
        lock(owner);
        List<Document> documents = list(owner);
        String requestedSeries = draft.seriesId == null || draft.seriesId.isBlank() ? id : draft.seriesId;
        for (Document existing : documents) {
            if (!existing.id.equals(id)) continue;
            if (existing.title.equals(title) && existing.publisher.equals(publisher) && existing.sourceUrl.equals(url)
                    && existing.sourceVersion.equals(version) && existing.content.equals(content)
                    && existing.validFrom.equals(draft.validFrom) && existing.validUntil.equals(draft.validUntil)
                    && existing.seriesId.equals(requestedSeries)) return existing;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求编号已用于其他内容，请使用新的保存请求");
        }
        if (documents.size() >= 100) throw bad("当前个人资料库上限为100个版本");
        String series = draft.seriesId;
        if (series == null || series.isBlank()) series = id;
        else {
            String requested = series;
            if (documents.stream().noneMatch(d -> d.seriesId.equals(requested))) throw missing();
            if (documents.stream().anyMatch(d -> d.seriesId.equals(requested) && d.sourceVersion.equals(version)))
                throw bad("同一资料不能使用重复版本号");
        }
        db.update("INSERT INTO knowledge_document(id,owner_id,series_id,title,source_url,publisher,source_version,valid_from,valid_until,content,status,revision) VALUES (?,?,?,?,?,?,?,?,?,?,?,1)",
                id, owner, series, title, url, publisher, version, draft.validFrom, draft.validUntil, content, "DRAFT");
        return new Document(id, series, title, url, publisher, version, draft.validFrom, draft.validUntil, content, "DRAFT", 1);
    }

    @Transactional
    public Document transition(int owner, String id, String status, int expectedRevision) {
        if (status == null || !List.of("PUBLISHED", "WITHDRAWN").contains(status)) throw bad("不支持的资料状态");
        lock(owner);
        Document current = list(owner).stream().filter(d -> d.id.equals(id)).findFirst().orElseThrow(KnowledgeService::missing);
        if (current.revision != expectedRevision) throw new ResponseStatusException(HttpStatus.CONFLICT, "资料已更新，请刷新后重试");
        if (current.status.equals(status)) return current;
        if (status.equals("PUBLISHED")) {
            LocalDate today = LocalDate.now(clock);
            if (today.isBefore(current.validFrom) || today.isAfter(current.validUntil)) throw bad("未生效或已过期资料不能发布");
            db.update("UPDATE knowledge_document SET status='WITHDRAWN',revision=revision+1 WHERE owner_id=? AND series_id=? AND status='PUBLISHED' AND id<>?", owner, current.seriesId, id);
        }
        db.update("UPDATE knowledge_document SET status=?,revision=revision+1 WHERE id=? AND owner_id=?", status, id, owner);
        return list(owner).stream().filter(d -> d.id.equals(id)).findFirst().orElseThrow();
    }

    public SearchResult search(int owner, String question) {
        String query = required(question, 1000);
        Set<String> terms = terms(query);
        LocalDate today = LocalDate.now(clock);
        record Scored(Citation citation, double score) {}
        List<Scored> candidates = new ArrayList<>();
        for (Document d : list(owner)) {
            if (!d.status.equals("PUBLISHED") || today.isBefore(d.validFrom) || today.isAfter(d.validUntil)) continue;
            String[] paragraphs = d.content.split("\\r?\\n+");
            for (int i = 0; i < paragraphs.length; i++) {
                String paragraph = paragraphs[i].trim();
                if (paragraph.isEmpty()) continue;
                // Bounded verbatim slices; every citation identifies its paragraph and slice.
                for (int offset = 0; offset < paragraph.length();) {
                    int end = Math.min(offset + 500, paragraph.length());
                    if (end < paragraph.length() && Character.isHighSurrogate(paragraph.charAt(end - 1))
                            && Character.isLowSurrogate(paragraph.charAt(end))) end--;
                    String quote = paragraph.substring(offset, end);
                    int sliceStart = offset;
                    offset = end;
                    Set<String> words = terms(quote);
                    long matches = terms.stream().filter(words::contains).count();
                    if (matches == 0) continue;
                    double relevance = (double)matches / Math.max(1, terms.size());
                    if (relevance < .25) continue;
                    candidates.add(new Scored(new Citation(d.id + ":" + (i + 1) + ":" + sliceStart,
                            d.id, d.title, d.sourceUrl, d.publisher, d.sourceVersion, d.validFrom, d.validUntil, i + 1, quote), relevance));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(s -> s.citation.id));
        List<Citation> hits = candidates.stream().limit(3).map(Scored::citation).toList();
        return new SearchResult("extractive", hits.isEmpty() ? "没有检索到当前有效的相关资料，无法据此确认答案。"
                : "以下为相关原文，不是已核验结论；若来源互相冲突，请核对发布方。", hits);
    }

    static Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        var matcher = java.util.regex.Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (Character.UnicodeScript.of(token.codePointAt(0)) == Character.UnicodeScript.HAN) {
                for (int i = 0; i + 1 < token.length(); i++) result.add(token.substring(i, i + 2));
            } else result.add(token);
        }
        result.removeAll(Set.of("请问", "什么", "怎么", "如何", "是否", "可以", "我们", "你们", "the", "is", "a"));
        return result;
    }
    private static String required(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw bad("必填内容为空或超过长度限制");
        return value.trim();
    }
    private static void owner(int owner) { if (owner <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在或无权访问"); }
}
