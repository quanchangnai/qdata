package quan.data.field;

import org.pcollections.Empty;
import org.pcollections.PMap;
import quan.data.*;

import java.util.*;

/**
 * Created by quanchangnai on 2019/5/20.
 */
@SuppressWarnings({"unchecked", "NullableProblems"})
public final class MapField<K, V> extends Node implements Map<K, V>, Field {

    private PMap<K, V> origin = Empty.map();

    private Delegate<K, V> delegate = new Delegate<>(this);

    public MapField(Data<?> owner, int position) {
        _setOwner(owner, position, false);
    }

    public Map<K, V> getDelegate() {
        return delegate;
    }

    @Override
    public void commit(Object log) {
        this.origin = (PMap<K, V>) log;
    }

    @Override
    public void _setChildrenLogOwner(Data<?> owner, int position) {
        for (V value : getCurrent().values()) {
            if (value instanceof Bean) {
                _setLogOwner((Bean) value, owner, position);
            }
        }
    }


    public PMap<K, V> getCurrent() {
        return getCurrent(Transaction.get());
    }

    public PMap<K, V> getCurrent(Transaction transaction) {
        if (transaction != null) {
            PMap<K, V> log = (PMap<K, V>) _getFieldLog(transaction, this);
            if (log != null) {
                return log;
            }
        }
        return origin;
    }

    @Override
    public int size() {
        return getCurrent().size();
    }

    @Override
    public boolean isEmpty() {
        return getCurrent().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return getCurrent().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return getCurrent().containsValue(value);
    }

    @Override
    public V get(Object key) {
        return getCurrent().get(key);
    }

    @Override
    public V put(K key, V value) {
        Validations.validateMapKey(key);
        Validations.validateCollectionValue(value);

        Transaction transaction = Transaction.get();
        if (transaction != null) {
            PMap<K, V> oldMap = getCurrent(transaction);
            V oldValue = oldMap.get(key);
            PMap<K, V> newMap = oldMap.plus(key, value);

            Data<?> owner = _getLogOwner(transaction);
            int position = _getLogPosition();
            if (value instanceof Bean) {
                _setLogOwner((Bean) value, owner, position);
            }
            if (oldValue instanceof Bean) {
                _setLogOwner((Bean) oldValue, null, 0);
            }
            _setFieldLog(transaction, this, newMap, owner, position);

            return oldValue;
        } else if (Transaction.isOptional()) {
            return plus(key, value);
        } else {
            Validations.transactionError();
            return null;
        }
    }

    public V plus(K key, V value) {
        Validations.validateMapKey(key);
        Validations.validateCollectionValue(value);

        V oldValue = origin.get(key);
        origin = origin.plus(key, value);

        if (value instanceof Bean) {
            _setOwner((Bean) value, _getOwner(), _getPosition());
        }

        if (oldValue instanceof Bean) {
            _setOwner((Bean) oldValue, null, 0);
        }

        return oldValue;
    }

    @Override
    @SuppressWarnings("SuspiciousMethodCalls")
    public V remove(Object key) {
        Transaction transaction = Transaction.get();
        if (transaction != null) {
            PMap<K, V> oldMap = getCurrent(transaction);
            V value = oldMap.get(key);
            if (value != null) {
                _setFieldLog(transaction, this, oldMap.minus(key), _getLogOwner(transaction), _getLogPosition(transaction));
                if (value instanceof Bean) {
                    _setLogOwner((Bean) value, null, 0);
                }
            }
            return value;
        } else if (Transaction.isOptional()) {
            V value = origin.get(key);
            if (value instanceof Bean) {
                _setOwner((Bean) value, null, 0);
            }
            return value;
        } else {
            Validations.transactionError();
            return null;
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        m.keySet().forEach(Validations::validateMapKey);
        m.values().forEach(Validations::validateCollectionValue);

        Transaction transaction = Transaction.get();
        if (transaction != null) {
            PMap<K, V> oldMap = getCurrent(transaction);
            Data<?> owner = _getLogOwner(transaction);
            int position = _getLogPosition();
            _setFieldLog(transaction, this, oldMap.plusAll(m), owner, position);

            for (K key : m.keySet()) {
                V newValue = m.get(key);
                if (newValue instanceof Bean) {
                    _setLogOwner((Bean) newValue, owner, position);
                }
                V oldValue = oldMap.get(key);
                if (oldValue != newValue && oldValue instanceof Bean) {
                    _setLogOwner((Bean) oldValue, null, 0);
                }
            }
        } else if (Transaction.isOptional()) {
            PMap<K, V> oldMap = origin;
            origin = oldMap.plusAll(m);
            Data<?> owner = _getOwner();
            int position = _getPosition();

            for (K key : m.keySet()) {
                V newValue = m.get(key);
                if (newValue instanceof Bean) {
                    _setOwner((Bean) newValue, owner, position);
                }
                V oldValue = oldMap.get(key);
                if (oldValue != newValue && oldValue instanceof Bean) {
                    _setOwner((Bean) oldValue, null, 0);
                }
            }
        } else {
            Validations.transactionError();
        }
    }

    @Override
    public void clear() {
        Transaction transaction = Transaction.get();
        PMap<K, V> oldMap = getCurrent(transaction);
        if (oldMap.isEmpty()) {
            return;
        }

        if (transaction != null) {
            _setChildrenLogOwner(null, 0);
            _setFieldLog(transaction, this, Empty.map(), _getLogOwner(transaction), _getLogPosition(transaction));
        } else if (Transaction.isOptional()) {
            for (V value : this.origin.values()) {
                if (value instanceof Bean) {
                    _setOwner((Bean) value, null, 0);
                }
            }
            if (!this.origin.isEmpty()) {
                this.origin = Empty.map();
            }
        } else {
            Validations.transactionError();
        }
    }

    private Set<K> keySet;

    @Override
    public Set<K> keySet() {
        if (keySet == null) {
            keySet = new AbstractSet<>() {
                @Override
                public int size() {
                    return MapField.this.size();
                }

                @Override
                public boolean isEmpty() {
                    return MapField.this.isEmpty();
                }

                @Override
                public void clear() {
                    MapField.this.clear();
                }

                @Override
                public boolean contains(Object k) {
                    return MapField.this.containsKey(k);
                }

                @Override
                public Iterator<K> iterator() {
                    return new Iterator<>() {
                        //entrySet iterator
                        private Iterator<Entry<K, V>> it = entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        @Override
                        public K next() {
                            return it.next().getKey();
                        }

                        @Override
                        public void remove() {
                            it.remove();
                        }
                    };
                }
            };
        }

        return keySet;
    }

    private Collection<V> values;

    @Override
    public Collection<V> values() {
        if (values == null) {
            values = new AbstractCollection<>() {
                @Override
                public int size() {
                    return MapField.this.size();
                }

                @Override
                public boolean isEmpty() {
                    return MapField.this.isEmpty();
                }

                @Override
                public void clear() {
                    MapField.this.clear();
                }

                @Override
                public boolean contains(Object v) {
                    return MapField.this.containsValue(v);
                }

                @Override
                public Iterator<V> iterator() {
                    return new Iterator<>() {
                        //entrySet iterator
                        private Iterator<Entry<K, V>> it = entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        @Override
                        public V next() {
                            return it.next().getValue();
                        }

                        @Override
                        public void remove() {
                            it.remove();
                        }
                    };
                }
            };
        }
        return values;
    }

    private Set<Entry<K, V>> entrySet;

    @Override
    public Set<Entry<K, V>> entrySet() {
        if (entrySet == null)
            entrySet = new AbstractSet<>() {
                @Override
                public int size() {
                    return MapField.this.size();
                }

                @Override
                public boolean isEmpty() {
                    return MapField.this.isEmpty();
                }

                @Override
                public void clear() {
                    MapField.this.clear();
                }

                @Override
                public boolean contains(final Object e) {
                    if (!(e instanceof Entry)) {
                        return false;
                    }
                    V value = get(((Entry<?, ?>) e).getKey());
                    return value != null && value.equals(((Entry<?, ?>) e).getValue());
                }

                @Override
                public Iterator<Entry<K, V>> iterator() {
                    return new Iterator<>() {
                        //entrySet iterator
                        private Iterator<Entry<K, V>> it = MapField.this.getCurrent().entrySet().iterator();

                        private Entry<K, V> current;

                        @Override
                        public void remove() {
                            if (current == null) {
                                throw new IllegalStateException();
                            }
                            MapField.this.remove(current.getKey());
                        }

                        @Override
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        @Override
                        public Entry<K, V> next() {
                            return current = it.next();
                        }

                    };
                }

            };
        return entrySet;
    }

    @Override
    public String toString() {
        return String.valueOf(getCurrent());
    }

    private static class Delegate<K, V> implements Map<K, V> {

        private MapField<K, V> field;

        public Delegate(MapField<K, V> field) {
            this.field = field;
        }

        @Override
        public int size() {
            return field.size();
        }

        @Override
        public boolean isEmpty() {
            return field.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return field.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return field.containsValue(value);
        }

        @Override
        public V get(Object key) {
            return field.get(key);
        }

        @Override
        public V put(K key, V value) {
            return field.put(key, value);
        }

        @Override
        public V remove(Object key) {
            return field.remove(key);
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> m) {
            field.putAll(m);
        }

        @Override
        public void clear() {
            field.clear();
        }

        @Override
        public Set<K> keySet() {
            return field.keySet();
        }

        @Override
        public Collection<V> values() {
            return field.values();
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return field.entrySet();
        }

        @Override
        public String toString() {
            return field.toString();
        }
    }

}
