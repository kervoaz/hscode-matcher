package com.geodis.hs.matcher.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class HierarchyContextTest {

    @Test
    void siblings_nonNullListPreserved() {
        HsEntry chapter = new HsEntry("87", 2, "Vehicles", Language.EN, null);
        HsEntry heading = new HsEntry("8703", 4, "Motor cars", Language.EN, "87");
        HsEntry s1 = new HsEntry("870321", 6, "Sub 1", Language.EN, "8703");
        HsEntry s2 = new HsEntry("870322", 6, "Sub 2", Language.EN, "8703");

        HierarchyContext ctx = new HierarchyContext(chapter, heading, List.of(s1, s2));

        assertThat(ctx.chapter()).isSameAs(chapter);
        assertThat(ctx.heading()).isSameAs(heading);
        assertThat(ctx.siblings()).containsExactly(s1, s2);
    }

    @Test
    void nullSiblings_becomesEmptyList() {
        HsEntry chapter = new HsEntry("01", 2, "Animals", Language.FR, null);
        HsEntry heading = new HsEntry("0101", 4, "Horses", Language.FR, "01");

        HierarchyContext ctx = new HierarchyContext(chapter, heading, null);

        assertThat(ctx.siblings()).isEmpty();
    }
}
