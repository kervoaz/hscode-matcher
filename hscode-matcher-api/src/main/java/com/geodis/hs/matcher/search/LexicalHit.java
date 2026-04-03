package com.geodis.hs.matcher.search;

import com.geodis.hs.matcher.domain.HsEntry;

/** Single lexical (Lucene) search hit before hybrid merge. */
public record LexicalHit(HsEntry entry, float score) {}
