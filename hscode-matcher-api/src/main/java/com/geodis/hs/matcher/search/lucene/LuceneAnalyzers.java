package com.geodis.hs.matcher.search.lucene;

import com.geodis.hs.matcher.domain.Language;
import java.util.EnumMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;

/**
 * Lucene analyzers with language-specific stemming. Stop words are <strong>disabled</strong>: terms
 * like {@code other}, {@code and}, or {@code the} appear constantly in HS/CN descriptions and must
 * remain searchable (default {@link EnglishAnalyzer} would drop {@code other} entirely).
 */
public final class LuceneAnalyzers {

    private static final CharArraySet NO_STOPS = CharArraySet.EMPTY_SET;

    private LuceneAnalyzers() {}

    public static Analyzer forLanguage(Language language) {
        return switch (language) {
            case FR -> new FrenchAnalyzer(NO_STOPS);
            case EN -> new EnglishAnalyzer(NO_STOPS);
            case DE -> new GermanAnalyzer(NO_STOPS);
        };
    }

    public static Map<Language, Analyzer> createAll() {
        EnumMap<Language, Analyzer> m = new EnumMap<>(Language.class);
        for (Language lang : Language.values()) {
            m.put(lang, forLanguage(lang));
        }
        return m;
    }
}
