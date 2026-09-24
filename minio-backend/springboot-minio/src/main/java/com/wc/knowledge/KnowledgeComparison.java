package com.wc.knowledge;

import java.util.*;

/** Exact line comparison, not semantic conflict detection. Work is bounded for untrusted documents. */
public final class KnowledgeComparison {
    private KnowledgeComparison() {}
    public record FieldChange(String field, String before, String after) {}
    public record LineChange(String kind, Integer beforeLine, Integer afterLine, String text) {}
    public record Result(String beforeId, String afterId, List<FieldChange> fields,
                         List<LineChange> lines, boolean coarse, int added, int removed) {}

    public static Result compare(KnowledgeService.Document before, KnowledgeService.Document after) {
        List<FieldChange> fields = new ArrayList<>();
        field(fields, "标题", before.title(), after.title());
        field(fields, "发布单位", before.publisher(), after.publisher());
        field(fields, "来源链接", before.sourceUrl(), after.sourceUrl());
        field(fields, "版本号", before.sourceVersion(), after.sourceVersion());
        field(fields, "生效日期", before.validFrom().toString(), after.validFrom().toString());
        field(fields, "截止日期", before.validUntil().toString(), after.validUntil().toString());
        String[] a = before.content().split("\\r?\\n", -1), b = after.content().split("\\r?\\n", -1);
        List<LineChange> lines = new ArrayList<>();
        // Preserve all text even when a detailed LCS matrix would be too expensive.
        boolean coarse = (long) a.length * b.length > 250_000;
        if (coarse) {
            int prefix = 0, suffix = 0;
            while (prefix < Math.min(a.length, b.length) && a[prefix].equals(b[prefix])) {
                lines.add(new LineChange("UNCHANGED", prefix + 1, prefix + 1, a[prefix])); prefix++;
            }
            while (suffix < Math.min(a.length, b.length) - prefix && a[a.length - 1 - suffix].equals(b[b.length - 1 - suffix])) suffix++;
            for (int i = prefix; i < a.length - suffix; i++) lines.add(new LineChange("REMOVED", i + 1, null, a[i]));
            for (int j = prefix; j < b.length - suffix; j++) lines.add(new LineChange("ADDED", null, j + 1, b[j]));
            for (int i = 0; i < suffix; i++) lines.add(new LineChange("UNCHANGED", a.length - suffix + i + 1, b.length - suffix + i + 1, a[a.length - suffix + i]));
        } else {
            int[][] lcs = new int[a.length + 1][b.length + 1];
            for (int i = a.length - 1; i >= 0; i--)
                for (int j = b.length - 1; j >= 0; j--)
                    lcs[i][j] = a[i].equals(b[j]) ? 1 + lcs[i + 1][j + 1] : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            int i = 0, j = 0;
            while (i < a.length || j < b.length) {
                if (i < a.length && j < b.length && a[i].equals(b[j])) {
                    lines.add(new LineChange("UNCHANGED", i + 1, j + 1, a[i])); i++; j++;
                } else if (i < a.length && (j == b.length || lcs[i + 1][j] >= lcs[i][j + 1])) {
                    lines.add(new LineChange("REMOVED", i + 1, null, a[i++]));
                } else lines.add(new LineChange("ADDED", null, j + 1, b[j++]));
            }
        }
        return new Result(before.id(), after.id(), List.copyOf(fields), List.copyOf(lines), coarse,
                (int) lines.stream().filter(l -> l.kind.equals("ADDED")).count(),
                (int) lines.stream().filter(l -> l.kind.equals("REMOVED")).count());
    }
    private static void field(List<FieldChange> fields, String name, String before, String after) {
        if (!Objects.equals(before, after)) fields.add(new FieldChange(name, before, after));
    }
}
