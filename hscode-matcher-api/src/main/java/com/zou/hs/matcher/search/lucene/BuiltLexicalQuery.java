package com.zou.hs.matcher.search.lucene;

import org.apache.lucene.search.Query;

/** Parsed query for lexical search and count of tokens used in fuzzy SHOULD clauses. */
record BuiltLexicalQuery(Query query, int fuzzyTokenCount) {}
