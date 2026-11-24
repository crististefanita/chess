package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Defensive factories for the experimental engine, without post-API26 collection APIs. */
final class HardCollections {
    private HardCollections() { }

    @SafeVarargs
    static <T> List<T> list(T... elements) {
        Objects.requireNonNull(elements);
        var result = new ArrayList<T>(elements.length);
        for (T element : elements) result.add(Objects.requireNonNull(element));
        return Collections.unmodifiableList(result);
    }

    static <T> List<T> copyList(Collection<? extends T> elements) {
        Objects.requireNonNull(elements);
        var result = new ArrayList<T>(elements.size());
        for (T element : elements) result.add(Objects.requireNonNull(element));
        return Collections.unmodifiableList(result);
    }

    @SafeVarargs
    static <T> Set<T> set(T... elements) {
        Objects.requireNonNull(elements);
        var result = new LinkedHashSet<T>();
        for (T element : elements) {
            if (!result.add(Objects.requireNonNull(element))) throw new IllegalArgumentException("Repeated set element");
        }
        return Collections.unmodifiableSet(result);
    }
}