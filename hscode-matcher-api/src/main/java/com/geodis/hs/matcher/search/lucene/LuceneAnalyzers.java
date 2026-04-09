package com.geodis.hs.matcher.search.lucene;

import com.geodis.hs.matcher.domain.Language;
import java.util.EnumMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.de.GermanLightStemFilter;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.fr.FrenchLightStemFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.ElisionFilter;

/**
 * Lucene analyzers with language-specific stemming. Stop words are <strong>disabled</strong>: terms
 * like {@code other}, {@code and}, or {@code the} appear constantly in HS/CN descriptions and must
 * remain searchable (default {@link org.apache.lucene.analysis.en.EnglishAnalyzer} would drop {@code
 * other} entirely).
 *
 * <p>After lower-casing, {@link ASCIIFoldingFilter} maps accented characters to ASCII so queries
 * such as {@code xeres} match indexed text like {@code xérès}.
 */
public final class LuceneAnalyzers {

    private static final CharArraySet NO_STOPS = CharArraySet.EMPTY_SET;

    private LuceneAnalyzers() {}

    public static Analyzer forLanguage(Language language) {
        return switch (language) {
            case FR -> frenchNomenclatureAnalyzer();
            case EN -> englishNomenclatureAnalyzer();
            case DE -> germanNomenclatureAnalyzer();
        };
    }

    /**
     * Same chain as {@link FrenchAnalyzer} with empty stopwords, plus {@link ASCIIFoldingFilter}
     * after {@link LowerCaseFilter} (indexing and query normalization).
     */
    private static Analyzer frenchNomenclatureAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                final Tokenizer source = new StandardTokenizer();
                TokenStream result = new ElisionFilter(source, FrenchAnalyzer.DEFAULT_ARTICLES);
                result = new LowerCaseFilter(result);
                result = new ASCIIFoldingFilter(result);
                result = new StopFilter(result, NO_STOPS);
                result = new FrenchLightStemFilter(result);
                return new TokenStreamComponents(source, result);
            }

            @Override
            protected TokenStream normalize(String fieldName, TokenStream in) {
                TokenStream result = new ElisionFilter(in, FrenchAnalyzer.DEFAULT_ARTICLES);
                result = new LowerCaseFilter(result);
                result = new ASCIIFoldingFilter(result);
                return result;
            }
        };
    }

    /**
     * Same chain as {@link org.apache.lucene.analysis.en.EnglishAnalyzer} with empty stopwords, plus
     * ASCII folding.
     */
    private static Analyzer englishNomenclatureAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                final Tokenizer source = new StandardTokenizer();
                TokenStream result = new EnglishPossessiveFilter(source);
                result = new LowerCaseFilter(result);
                result = new ASCIIFoldingFilter(result);
                result = new StopFilter(result, NO_STOPS);
                result = new PorterStemFilter(result);
                return new TokenStreamComponents(source, result);
            }

            @Override
            protected TokenStream normalize(String fieldName, TokenStream in) {
                TokenStream result = new LowerCaseFilter(in);
                result = new ASCIIFoldingFilter(result);
                return result;
            }
        };
    }

    /**
     * Same chain as {@link org.apache.lucene.analysis.de.GermanAnalyzer} with empty stopwords, plus
     * ASCII folding before {@link GermanNormalizationFilter}.
     */
    private static Analyzer germanNomenclatureAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                final Tokenizer source = new StandardTokenizer();
                TokenStream result = new LowerCaseFilter(source);
                result = new ASCIIFoldingFilter(result);
                result = new StopFilter(result, NO_STOPS);
                result = new SetKeywordMarkerFilter(result, CharArraySet.EMPTY_SET);
                result = new GermanNormalizationFilter(result);
                result = new GermanLightStemFilter(result);
                return new TokenStreamComponents(source, result);
            }

            @Override
            protected TokenStream normalize(String fieldName, TokenStream in) {
                TokenStream result = new LowerCaseFilter(in);
                result = new ASCIIFoldingFilter(result);
                result = new GermanNormalizationFilter(result);
                return result;
            }
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
