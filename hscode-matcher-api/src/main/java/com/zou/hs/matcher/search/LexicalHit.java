package com.zou.hs.matcher.search;

import com.zou.hs.matcher.domain.HsEntry;

/** Single lexical (Lucene) search hit before hybrid merge. */
public record LexicalHit(HsEntry entry, float score) {}
