package com.geodis.hs.matcher.embed;

import com.geodis.hs.matcher.domain.HsEntry;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Builds one text block per HS chapter (2 digits) for embedding: title + sample subheading lines. */
public final class ChapterPortraitTextBuilder {

    private static final int MAX_PORTRAIT_CHARS = 4500;
    private static final int MAX_SAMPLES_PER_CHAPTER = 45;

    private ChapterPortraitTextBuilder() {}

    public static Map<String, String> chapterPortraits(NomenclatureRegistry registry) {
        Map<String, String> titles = new TreeMap<>();
        Map<String, List<String>> samples = new HashMap<>();
        for (HsEntry e : registry.entries()) {
            if (e.code().length() < 2) {
                continue;
            }
            String ch = e.code().substring(0, 2);
            if (e.level() == 2) {
                titles.put(ch, e.description());
            } else if (e.level() >= 4) {
                samples.computeIfAbsent(ch, k -> new ArrayList<>()).add(e.description());
            }
        }
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, String> te : titles.entrySet()) {
            String ch = te.getKey();
            String title = te.getValue();
            StringBuilder sb = new StringBuilder(title);
            Set<String> seen = new LinkedHashSet<>();
            List<String> raw = samples.getOrDefault(ch, List.of());
            int added = 0;
            for (String line : raw) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String norm = line.strip();
                if (norm.length() < 3 || seen.contains(norm)) {
                    continue;
                }
                seen.add(norm);
                sb.append(' ').append(norm);
                added++;
                if (added >= MAX_SAMPLES_PER_CHAPTER || sb.length() >= MAX_PORTRAIT_CHARS) {
                    break;
                }
            }
            String s = sb.toString();
            if (s.length() > MAX_PORTRAIT_CHARS) {
                s = s.substring(0, MAX_PORTRAIT_CHARS);
            }
            out.put(ch, s);
        }
        return out;
    }
}
