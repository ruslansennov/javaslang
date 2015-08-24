/*     / \____  _    ______   _____ / \____   ____  _____
 *    /  \__  \/ \  / \__  \ /  __//  \__  \ /    \/ __  \   Javaslang
 *  _/  // _\  \  \/  / _\  \\_  \/  // _\  \  /\  \__/  /   Copyright 2014-2015 Daniel Dietrich
 * /___/ \_____/\____/\_____/____/\___\_____/_/  \_/____/    Licensed under the Apache License, Version 2.0
 */
package javaslang.collection;

import javaslang.Lazy;
import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.Value;
import javaslang.control.None;
import javaslang.control.Option;
import javaslang.control.Some;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.*;
import java.util.stream.Collector;

/**
 * An immutable {@code HashSet} implementation.
 *
 * @param <T> Component type
 * @since 2.0.0
 */
public final class HashSet<T> implements Set<T>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final HashSet<?> EMPTY = new HashSet<>(HashArrayMappedTrie.empty());

    private final HashArrayMappedTrie<T, Object> tree;
    // TODO: get rid of this (mid-term)
    private final transient Lazy<List<T>> list;
    private final transient Lazy<Integer> hash;

    private HashSet(HashArrayMappedTrie<T, Object> tree) {
        this.tree = tree;
        this.list = Lazy.of(() -> List.ofAll(() -> tree.iterator().map(t -> t._1)));
        this.hash = Lazy.of(() -> Traversable.hash(tree::iterator));
    }

    @SuppressWarnings("unchecked")
    public static <T> HashSet<T> empty() {
        return (HashSet<T>) EMPTY;
    }

    /**
     * Returns a {@link java.util.stream.Collector} which may be used in conjunction with
     * {@link java.util.stream.Stream#collect(java.util.stream.Collector)} to obtain a {@link javaslang.collection.Set}s.
     *
     * @param <T> Component type of the List.
     * @return A javaslang.collection.List Collector.
     */
    public static <T> Collector<T, ArrayList<T>, Set<T>> collector() {
        final Supplier<ArrayList<T>> supplier = ArrayList::new;
        final BiConsumer<ArrayList<T>, T> accumulator = ArrayList::add;
        final BinaryOperator<ArrayList<T>> combiner = (left, right) -> {
            left.addAll(right);
            return left;
        };
        final Function<ArrayList<T>, Set<T>> finisher = HashSet::ofAll;
        return Collector.of(supplier, accumulator, combiner, finisher);
    }

    /**
     * Returns a singleton {@code HashSet}, i.e. a {@code HashSet} of one element.
     *
     * @param element An element.
     * @param <T>     The component type
     * @return A new HashSet instance containing the given element
     */
    static <T> HashSet<T> of(T element) {
        return HashSet.<T> empty().add(element);
    }

    /**
     * <p>
     * Creates a HashSet of the given elements.
     * </p>
     *
     * <pre>
     * <code>  HashSet.of(1, 2, 3, 4)
     * </pre>
     *
     * @param <T>      Component type of the HashSet.
     * @param elements Zero or more elements.
     * @return A set containing the given elements.
     * @throws NullPointerException if {@code elements} is null
     */
    @SafeVarargs
    static <T> HashSet<T> of(T... elements) {
        Objects.requireNonNull(elements, "elements is null");
        HashSet<T> result = HashSet.empty();
        for (T element : elements) {
            result = result.add(element);
        }
        return result;
    }

    /**
     * Creates a HashSet of the given entries.
     *
     * @param entries Set entries
     * @param <T>     The value type
     * @return A new HashSet containing the given entries
     */
    @SuppressWarnings("unchecked")
    public static <T> HashSet<T> ofAll(Iterable<? extends T> entries) {
        Objects.requireNonNull(entries, "entries is null");
        if (entries instanceof HashSet) {
            return (HashSet<T>) entries;
        } else {
            HashArrayMappedTrie<T, Object> tree = HashArrayMappedTrie.empty();
            for (T entry : entries) {
                tree = tree.put(entry, entry);
            }
            return tree.isEmpty() ? empty() : new HashSet<>(tree);
        }
    }

    @Override
    public HashSet<T> add(T element) {
        return new HashSet<>(tree.put(element, element));
    }

    @Override
    public Iterator<T> iterator() {
        return tree.iterator().map(t -> t._1);
    }

    @Override
    public HashSet<T> clear() {
        return empty();
    }

    @Override
    public boolean contains(T element) {
        return tree.get(element).isDefined();
    }

    @Override
    public HashSet<T> distinct() {
        return this;
    }

    @Override
    public HashSet<T> distinctBy(Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator, "comparator is null");
        return HashSet.ofAll(list.get().distinctBy(comparator));
    }

    @Override
    public <U> HashSet<T> distinctBy(Function<? super T, ? extends U> keyExtractor) {
        Objects.requireNonNull(keyExtractor, "keyExtractor is null");
        return HashSet.ofAll(list.get().distinctBy(keyExtractor));
    }

    @Override
    public HashSet<T> drop(int n) {
        if (n <= 0) {
            return this;
        } else {
            return HashSet.ofAll(() -> iterator().drop(n));
        }
    }

    @Override
    public HashSet<T> dropRight(int n) {
        if (n <= 0) {
            return this;
        } else {
            return HashSet.ofAll(list.get().dropRight(n));
        }
    }

    @Override
    public HashSet<T> dropWhile(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        final List<T> dropped = list.get().dropWhile(predicate);
        return dropped.length() == list.get().length() ? this : HashSet.ofAll(dropped);
    }

    @Override
    public HashSet<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        final List<T> filtered = list.get().filter(predicate);
        return filtered.length() == list.get().length() ? this : HashSet.ofAll(filtered);
    }

    @Override
    public HashSet<T> findAll(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return filter(predicate);
    }

    @Override
    public Option<T> findLast(Predicate<? super T> predicate) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public <U> HashSet<U> flatMap(Function<? super T, ? extends Iterable<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        if (isEmpty()) {
            return empty();
        } else {
            HashSet<U> set = empty();
            for (T t : this) {
                for (U u : mapper.apply(t)) {
                    set = set.add(u);
                }
            }
            return set;
        }
    }

    @Override
    public <U> HashSet<U> flatMapM(Function<? super T, ? extends Value<? extends U>> mapper) {
        return flatMap(mapper);
    }

    @Override
    public HashSet<Object> flatten() {
        return flatMap(t -> (t instanceof Iterable) ? HashSet.ofAll((Iterable<?>) t).flatten() : HashSet.of(t));
    }

    @Override
    public <U> U foldRight(U zero, BiFunction<? super T, ? super U, ? extends U> f) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public <C> Map<C, HashSet<T>> groupBy(Function<? super T, ? extends C> classifier) {
        return foldLeft(HashMap.empty(), (map, t) -> {
            final C key = classifier.apply(t);
            final HashSet<T> values = map.get(key).map(ts -> ts.add(t)).orElse(HashSet.of(t));
            return map.put(key, values);
        });
    }

    @Override
    public boolean hasDefiniteSize() {
        return true;
    }

    @Override
    public T head() {
        if (tree.isEmpty()) {
            throw new NoSuchElementException("head of empty set");
        }
        return iterator().next();
    }

    @Override
    public Option<T> headOption() {
        return iterator().headOption();
    }

    @Override
    public HashSet<T> init() {
        if (isEmpty()) {
            throw new UnsupportedOperationException();
        }
        return HashSet.ofAll(list.get().init());
    }

    @Override
    public Option<HashSet<T>> initOption() {
        Option<List<T>> opt = list.get().initOption();
        return opt.isDefined() ? new Some<>(HashSet.ofAll(opt.get())) : None.instance();
    }

    @Override
    public boolean isEmpty() {
        return tree.isEmpty();
    }

    @Override
    public boolean isTraversableAgain() {
        return true;
    }

    @Override
    public int length() {
        return tree.size();
    }

    @Override
    public <U> HashSet<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        HashSet<U> result = HashSet.empty();
        for (T t : this) {
            result = result.add(mapper.apply(t));
        }
        return result;
    }

    @Override
    public <U> HashSet<U> mapM(Function<? super T, ? extends U> mapper) {
        return map(mapper);
    }

    @Override
    public String mkString(CharSequence delimiter,
                           CharSequence prefix,
                           CharSequence suffix) {
        final StringBuilder builder = new StringBuilder(prefix);
        forEach(t -> builder.append(String.valueOf(t)).append(String.valueOf(delimiter)));
        if (!isEmpty()) {
            builder.delete(builder.length() - delimiter.length(), builder.length());
        }
        return builder.append(suffix).toString();
    }

    @Override
    public Tuple2<HashSet<T>, HashSet<T>> partition(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        HashSet<T> first = HashSet.empty();
        HashSet<T> second = HashSet.empty();
        for (T t : this) {
            if (predicate.test(t)) {
                first = first.add(t);
            } else {
                second = second.add(t);
            }
        }
        return Tuple.of(first, second);
    }

    @Override
    public HashSet<T> peek(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action is null");
        return HashSet.ofAll(list.get().peek(action));
    }

    @Override
    public T reduceRight(BiFunction<? super T, ? super T, ? extends T> op) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public HashSet<T> remove(T element) {
        final HashArrayMappedTrie<T, Object> newTree = tree.remove(element);
        return newTree == tree ? this : new HashSet<>(newTree);
    }

    @Override
    public HashSet<T> removeAll(Iterable<? extends T> elements) {
        Objects.requireNonNull(elements, "elements is null");
        HashArrayMappedTrie<T, Object> trie = tree;
        for (T element : elements) {
            trie = trie.remove(element);
        }
        return trie == tree ? this : new HashSet<>(trie);
    }

    @Override
    public HashSet<T> replace(T currentElement, T newElement) {
        if (tree.containsKey(currentElement)) {
            return remove(currentElement).add(newElement);
        } else {
            return this;
        }
    }

    @Override
    public HashSet<T> replaceAll(T currentElement, T newElement) {
        return replace(currentElement, newElement);
    }

    @Override
    public HashSet<T> replaceAll(UnaryOperator<T> operator) {
        Objects.requireNonNull(operator, "operator is null");
        HashSet<T> result = HashSet.empty();
        for (T element : this) {
            result = result.add(operator.apply(element));
        }
        return result;
    }

    @Override
    public HashSet<T> retainAll(Iterable<? extends T> elements) {
        Objects.requireNonNull(elements, "elements is null");
        final HashSet<T> keeped = HashSet.ofAll(elements);
        HashSet<T> result = HashSet.empty();
        for (T element : this) {
            if (keeped.contains(element)) {
                result = result.add(element);
            }
        }
        return result;
    }

    @Override
    public HashSet<? extends Set<T>> sliding(int size) {
        return sliding(size, 1);
    }

    @Override
    public HashSet<HashSet<T>> sliding(int size, int step) {
        // TODO: may be removed by iterator().sliding(...) in order to remove list
        final List<HashSet<T>> l = list.get().sliding(size, step).map(HashSet::ofAll);
        return HashSet.ofAll(l);
    }

    @Override
    public Tuple2<HashSet<T>, HashSet<T>> span(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        Tuple2<List<T>, List<T>> t = list.get().span(predicate);
        return Tuple.of(HashSet.ofAll(t._1), HashSet.ofAll(t._2));
    }

    @Override
    public HashSet<T> tail() {
        if (tree.isEmpty()) {
            throw new UnsupportedOperationException("tail of empty set");
        }
        return remove(head());
    }

    @Override
    public Option<HashSet<T>> tailOption() {
        if (tree.isEmpty()) {
            return None.instance();
        } else {
            return new Some<>(tail());
        }
    }

    @Override
    public HashSet<T> take(int n) {
        if (tree.size() <= n) {
            return this;
        }
        return HashSet.ofAll(() -> iterator().take(n));
    }

    @Override
    public HashSet<T> takeRight(int n) {
        if (tree.size() <= n) {
            return this;
        }
        return HashSet.ofAll(list.get().takeRight(n));
    }

    @Override
    public HashSet<T> takeWhile(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        List<T> taken = list.get().takeWhile(predicate);
        return taken.length() == list.get().length() ? this : HashSet.ofAll(taken);
    }

    @Override
    public <T1, T2> Tuple2<HashSet<T1>, HashSet<T2>> unzip(Function<? super T, Tuple2<? extends T1, ? extends T2>> unzipper) {
        Objects.requireNonNull(unzipper, "unzipper is null");
        Tuple2<List<T1>, List<T2>> t = list.get().unzip(unzipper);
        return Tuple.of(HashSet.ofAll(t._1), HashSet.ofAll(t._2));
    }

    @Override
    public <U> HashSet<Tuple2<T, U>> zip(Iterable<U> that) {
        Objects.requireNonNull(that, "that is null");
        return HashSet.ofAll(list.get().zip(that));
    }

    @Override
    public <U> HashSet<Tuple2<T, U>> zipAll(Iterable<U> that, T thisElem, U thatElem) {
        Objects.requireNonNull(that, "that is null");
        return HashSet.ofAll(list.get().zipAll(that, thisElem, thatElem));
    }

    @Override
    public HashSet<Tuple2<T, Integer>> zipWithIndex() {
        return HashSet.ofAll(list.get().zipWithIndex());
    }

    @Override
    public int hashCode() {
        return hash.get();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof HashSet) {
            final HashSet<?> that = (HashSet<?>) o;
            return this.iterator().equals(that.iterator());
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return mkString(", ", "HashSet(", ")");
    }

    /**
     * <p>
     * {@code writeReplace} method for the serialization proxy pattern.
     * </p>
     * <p>
     * The presence of this method causes the serialization system to emit a SerializationProxy instance instead of
     * an instance of the enclosing class.
     * </p>
     *
     * @return A SerialiationProxy for this enclosing class.
     */
    private Object writeReplace() {
        return new SerializationProxy<>(this.list.get());
    }

    /**
     * <p>
     * {@code readObject} method for the serialization proxy pattern.
     * </p>
     * Guarantees that the serialization system will never generate a serialized instance of the enclosing class.
     *
     * @param stream An object serialization stream.
     * @throws java.io.InvalidObjectException This method will throw with the message "Proxy required".
     */
    private void readObject(ObjectInputStream stream) throws InvalidObjectException {
        throw new InvalidObjectException("Proxy required");
    }

    /**
     * A serialization proxy which, in this context, is used to deserialize immutable, linked Lists with final
     * instance fields.
     *
     * @param <T> The component type of the underlying list.
     */
    // DEV NOTE: The serialization proxy pattern is not compatible with non-final, i.e. extendable,
    // classes. Also, it may not be compatible with circular object graphs.
    private static final class SerializationProxy<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        // the instance to be serialized/deserialized
        private transient List<T> list;

        /**
         * Constructor for the case of serialization, called by {@link HashSet#writeReplace()}.
         * <p/>
         * The constructor of a SerializationProxy takes an argument that concisely represents the logical state of
         * an instance of the enclosing class.
         *
         * @param list a Cons
         */
        SerializationProxy(List<T> list) {
            this.list = list;
        }

        /**
         * Write an object to a serialization stream.
         *
         * @param s An object serialization stream.
         * @throws java.io.IOException If an error occurs writing to the stream.
         */
        private void writeObject(ObjectOutputStream s) throws IOException {
            s.defaultWriteObject();
            s.writeInt(list.length());
            for (List<T> l = list; !l.isEmpty(); l = l.tail()) {
                s.writeObject(l.head());
            }
        }

        /**
         * Read an object from a deserialization stream.
         *
         * @param s An object deserialization stream.
         * @throws ClassNotFoundException If the object's class read from the stream cannot be found.
         * @throws InvalidObjectException If the stream contains no list elements.
         * @throws IOException            If an error occurs reading from the stream.
         */
        private void readObject(ObjectInputStream s) throws ClassNotFoundException, IOException {
            s.defaultReadObject();
            final int size = s.readInt();
            if (size < 0) {
                throw new InvalidObjectException("No elements");
            }
            List<T> temp = List.empty();
            for (int i = 0; i < size; i++) {
                @SuppressWarnings("unchecked")
                final T element = (T) s.readObject();
                temp = temp.prepend(element);
            }
            list = temp.reverse();
        }

        /**
         * <p>
         * {@code readResolve} method for the serialization proxy pattern.
         * </p>
         * Returns a logically equivalent instance of the enclosing class. The presence of this method causes the
         * serialization system to translate the serialization proxy back into an instance of the enclosing class
         * upon deserialization.
         *
         * @return A deserialized instance of the enclosing class.
         */
        private Object readResolve() {
            return HashSet.ofAll(list);
        }
    }
}
