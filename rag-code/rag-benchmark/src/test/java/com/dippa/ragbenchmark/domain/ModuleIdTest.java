package com.dippa.ragbenchmark.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleIdTest {

    @Test
    void parse_isCaseInsensitive() {
        assertThat(ModuleId.parse("m2")).isEqualTo(ModuleId.M2);
        assertThat(ModuleId.parse("M2")).isEqualTo(ModuleId.M2);
        assertThat(ModuleId.parse("M2")).isEqualTo(ModuleId.M2);
    }

    @Test
    void parse_withUnknownValue_throwsInvalidModuleException() {
        assertThatThrownBy(() -> ModuleId.parse("bogus"))
                .isInstanceOf(InvalidModuleException.class)
                .hasMessageContaining("bogus");
    }

    @Test
    void sortedNames_sortsRegardlessOfSetIterationOrder() {
        Set<ModuleId> modules = EnumSet.of(ModuleId.M4, ModuleId.M1, ModuleId.M3);

        assertThat(ModuleId.sortedNames(modules)).containsExactly("M1", "M3", "M4");
    }

    @Test
    void sortedNames_withEmptySet_returnsEmptyList() {
        assertThat(ModuleId.sortedNames(Set.of())).isEmpty();
    }
}
