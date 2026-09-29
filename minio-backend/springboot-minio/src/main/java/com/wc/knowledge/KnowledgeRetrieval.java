package com.wc.knowledge;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded lexical retrieval over already authorised, current documents.
 * Normalisation is for matching only: citations always contain original text.
 * This is a heuristic, not semantic search or a factual confidence score.
 */
final class KnowledgeRetrieval {
    private static final int WINDOW = 500, OVERLAP = 100;
    private static final Pattern WORDS = Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+");
    private static final Set<String> STOP = Set.of("请问", "什么", "怎么", "如何", "是否", "可以", "我们", "你们", "the", "is", "a");
    private record Passage(KnowledgeService.Citation citation, Set<String> matches, String normalised) {}
    private record Ranked(Passage passage, double score) {}

    private KnowledgeRetrieval() {}

    static String normalise(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    static Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        var matcher = WORDS.matcher(normalise(text));
        while (matcher.find()) {
            String token = matcher.group();
            if (Character.UnicodeScript.of(token.codePointAt(0)) == Character.UnicodeScript.HAN) {
                int[] points = token.codePoints().toArray();
                for (int i = 0; i + 1 < points.length; i++) result.add(new String(points, i, 2));
            } else result.add(token);
        }
        result.removeAll(STOP);
        return result;
    }

    static List<KnowledgeService.Citation> search(List<KnowledgeService.Document> documents, String question) {
        Set<String> query = terms(question);
        if (query.isEmpty()) return List.of();
        Map<String, Integer> frequency = new HashMap<>();
        List<Passage> passages = new ArrayList<>();
        for (var document : documents) {
            Set<String> documentTerms = new HashSet<>();
            String[] paragraphs = document.content().split("\\r?\\n+");
            for (int index = 0; index < paragraphs.length; index++) {
                String paragraph = paragraphs[index].trim();
                for (int start = 0; start < paragraph.length();) {
                    int end = Math.min(start + WINDOW, paragraph.length());
                    if (end < paragraph.length() && Character.isHighSurrogate(paragraph.charAt(end - 1))
                            && Character.isLowSurrogate(paragraph.charAt(end))) end--;
                    String quote = paragraph.substring(start, end);
                    Set<String> matches = terms(quote);
                    matches.retainAll(query);
                    documentTerms.addAll(matches);
                    // Keep the previous minimum lexical coverage gate, not an invented confidence level.
                    if (!matches.isEmpty() && (double) matches.size() / query.size() >= .25) {
                        var citation = new KnowledgeService.Citation(document.id() + ":" + (index + 1) + ":" + start,
                                document.id(), document.title(), document.sourceUrl(), document.publisher(),
                                document.sourceVersion(), document.validFrom(), document.validUntil(), index + 1, quote);
                        passages.add(new Passage(citation, matches, normalise(quote)));
                    }
                    if (end == paragraph.length()) break;
                    start = end - OVERLAP;
                    if (start > 0 && Character.isLowSurrogate(paragraph.charAt(start))
                            && Character.isHighSurrogate(paragraph.charAt(start - 1))) start--;
                }
            }
            // A repeated term counts once per eligible document, not once per repeated paragraph.
            documentTerms.forEach(term -> frequency.merge(term, 1, Integer::sum));
        }
        Map<String, Double> weights = new HashMap<>();
        query.forEach(term -> weights.put(term, 1 + Math.log1p((double) documents.size() / (1 + frequency.getOrDefault(term, 0)))));
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        String phrase = normalise(question).trim();
        List<Ranked> ranked = passages.stream().map(p -> new Ranked(p,
                p.matches.stream().mapToDouble(weights::get).sum() / total
                        + (p.normalised.contains(phrase) ? .25 : 0)))
                .sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparing(r -> r.passage.citation.id())).toList();
        List<Passage> selected = new ArrayList<>();
        for (Ranked candidate : ranked) {
            Passage passage = candidate.passage;
            // Identical body text in different documents retains distinct provenance.
            boolean duplicate = selected.stream().anyMatch(previous ->
                    previous.citation.documentId().equals(passage.citation.documentId())
                            && previous.citation.quote().equals(passage.citation.quote()));
            if (!duplicate) selected.add(passage);
            if (selected.size() == 3) break;
        }
        return selected.stream().map(Passage::citation).toList();
    }
}
