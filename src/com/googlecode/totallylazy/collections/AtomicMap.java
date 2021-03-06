package com.googlecode.totallylazy.collections;

import com.googlecode.totallylazy.Atomic;
import com.googlecode.totallylazy.Maps;
import com.googlecode.totallylazy.Option;
import com.googlecode.totallylazy.Pair;
import com.googlecode.totallylazy.Segment;
import com.googlecode.totallylazy.Unchecked;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static com.googlecode.totallylazy.Atomic.constructors.atomic;
import static com.googlecode.totallylazy.Pair.pair;
import static com.googlecode.totallylazy.Sequences.sequence;
import static com.googlecode.totallylazy.Unchecked.cast;

public class AtomicMap<K, V> implements ConcurrentMap<K, V> {
    private final Atomic<PersistentMap<K, V>> atomic;

    private AtomicMap(Atomic<PersistentMap<K, V>> atomic) {
        this.atomic = atomic;
    }

    public static <K, V> AtomicMap<K, V> atomicMap(PersistentMap<K, V> map) {return new AtomicMap<K, V>(atomic(map));}

    private PersistentMap<K, V> map() {return atomic.value();}

    private K key(Object key) {return cast(key);}

    @Override
    public int size() {
        return map().size();
    }

    @Override
    public boolean isEmpty() {
        return map().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map().contains(key(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    @Override
    public V get(Object key) {
        return map().lookup(key(key)).getOrNull();
    }

    @Override
    public V put(final K key, final V value) {
        return atomic.modifyReturn(map -> AtomicMap.this.put(map, key, value));
    }

    private Pair<PersistentMap<K, V>, V> put(PersistentMap<K, V> map, K key, V value) {
        return PersistentMap.methods.put(map, key, value).
                second(Option.functions.<V>getOrNull());
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> m) {
        atomic.modify(map -> Maps.pairs(m).<Pair<K, V>>unsafeCast().fold(map, Segment.functions.<Pair<K, V>, PersistentMap<K, V>>cons()));
    }

    @Override
    public V remove(final Object key) {
        return atomic.modifyReturn(map -> PersistentMap.methods.remove(map, key(key)).
                second(Option.functions.<V>getOrNull()));
    }

    @Override
    public void clear() {
        atomic.modify(PersistentMap<K, V>::empty);
    }

    @Override
    public Set<K> keySet() {
        return map().keys().toSet();
    }

    @Override
    public Collection<V> values() {
        return map().values().toList();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return Maps.entrySet(map());
    }

    @Override
    public V putIfAbsent(final K key, final V value) {
        return atomic.modifyReturn(map -> {
            if (!map.contains(key)) return put(map, key, value);
            return pair(map, map.lookup(key).getOrNull());
        });
    }

    @Override
    public boolean remove(final Object rawKey, final Object value) {
        return atomic.modifyReturn(map -> {
            K key = key(rawKey);
            if (map.lookup(key).contains(Unchecked.<V>cast(value))) return pair(map.delete(key), true);
            return pair(map, false);
        });
    }

    @Override
    public boolean replace(final K rawKey, final V oldValue, final V newValue) {
        return atomic.modifyReturn(map -> {
            K key = key(rawKey);
            if (map.lookup(key).contains(Unchecked.<V>cast(oldValue))) return pair(map.insert(key, newValue), true);
            return pair(map, false);
        });
    }

    @Override
    public V replace(final K key, final V value) {
        return atomic.modifyReturn(map -> {
            if (map.contains(key)) return AtomicMap.this.put(map, key, value);
            return pair(map, null);
        });
    }
}
