package com.googlecode.totallylazy.predicates;

import com.googlecode.totallylazy.Value;

public class EqualsPredicate<T> extends LogicalPredicate<T> implements Value<T> {
    private final T value;

    public EqualsPredicate(T value) {
        this.value = value;
    }

    public boolean matches(T other) {
        return value.equals(other);
    }

    public T value() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
