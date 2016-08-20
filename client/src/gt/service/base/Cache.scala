package gt.service.base

import rx.{Obs, Rx, Var}
import scala.collection.immutable.TreeMap
import util.intervaltree.IntervalTree

/**
  * A reactive local data cache.
  *
  * Caches are used to store data fetched from the server.
  *
  * The task of maintaining an up-to-date reactive representation of the data is
  * tedious. Caches provides a was to automatically store, index, update and retrieve
  * values as well as constructing default value when some data is missing.
  *
  * The main hash function is used to compute the primary indexing key. Only
  * a single element can be stored for a given primary key. If another value with
  * the same primary key is inserted, its values will overwrite the previous value.
  *
  * Caches can define additional indexes based on arbitrary criteria for faster access.
  *
  * @param hash the main index hash function
  * @tparam K the type of the main index key
  * @tparam V the type of values stored in the cache
  */
abstract class Cache[K, V <: AnyRef](hash: V => K) {
	/**
	  * Default value factory for newly allocated cells.
	  *
	  * The default implementation is undefined and instead throws an
	  * exception. The intent here is that concrete instances of a cache
	  * overrides this method to provide a appropriate default value for
	  * the item and optionally initiate some computation to obtain the
	  * actual value that should be associated with the key.
	  *
	  * The item can then be updated by calling the `update` method.
	  */
	def default: V = throw new UnsupportedOperationException("Default value factory is not defined")

	/**
	  * Default value key-sensible factory for newly allocated cells.
	  *
	  * The default implementation delegate to the key-less `default` method.
	  * If there is no need for the key to be known in order to generate the
	  * default value, concrete classes should override the key-less version
	  * of this method. Overriding this overload allows to use the requested
	  * key as part of the computation of the default value.
	  *
	  * @param key the key for which the value is being generated
	  */
	def default(key: K): V = default

	/** Collection of items in the cache */
	private var items = Map[K, Var[V]]()

	/** Sets of defined index for this cache */
	private var indexes = Set[BaseIndex]()

	/**
	  * Constructs a new cache cell for the given key and value.
	  *
	  * @param key   the computed key for this item
	  * @param value the current value of this item
	  */
	private def constructCell(key: K, value: V): Var[V] = {
		val cell = Var[V](value)
		items += (key -> cell)
		for (idx <- indexes) idx.register(cell)
		cell
	}

	/**
	  * Fetches the item with the requested key or creates it if it does not exist.
	  *
	  * The `default` factory method will be invoked with the requested key to
	  * create the default value. Its default implementation is undefined and
	  * instead throws an exception. You must be sure that it is possible to
	  * construct default instances before requesting a key that is not available
	  * in the cache.
	  *
	  * @param key the key to look up for in the cache
	  */
	def get(key: K): Rx[V] = items.get(key) match {
		case Some(cell) => cell
		case None => constructCell(key, default(key))
	}

	/**
	  * Updates the value of an item in the cache.
	  *
	  * The item is considered a new version of a previously stored item if both
	  * hashes are the same. If the item is not yet present in the cache, it is
	  * instead inserted.
	  *
	  * @param item the updated item
	  */
	def update(item: V): Unit = Rx.atomically {
		val key = hash(item)
		items.get(key) match {
			case Some(cell) => cell := item
			case None => constructCell(key, item)
		}
	}

	/** Removes an item by key from the cache */
	def removeKey(key: K): Unit = Rx.atomically {
		items.get(key) match {
			case None => // Ignore
			case Some(cell) =>
				for (idx <- indexes) idx.unregister(cell)
				Rx.invalidate(cell)
				items -= key
		}
	}

	/**
	  * Removes an item by value from the cache.
	  *
	  * This function recomputes the item's current key and is thus less
	  * efficient that the functionally equivalent `removeKey` if the key
	  * values is known.
	  *
	  * @param item the item to remove from the cache
	  */
	def remove(item: V): Unit = removeKey(hash(item))

	/**
	  * Removes every items in the cache matching the given predicate.
	  *
	  * @param pred the predicate to use
	  */
	def prune(pred: V => Boolean): Unit = {
		for ((key, item) <- items if pred(item.!)) removeKey(key)
	}

	/** Clears the cache */
	def clear(): Unit = Rx.atomically {
		for (idx <- indexes) idx.clear()
		items.values.foreach(Rx.kill)
		items = items.empty
	}

	/**
	  * Checks if the caches contains the given key.
	  *
	  * @param key the key to search
	  */
	def contains(key: K): Boolean = items.contains(key)

	/**
	  * Base trait for indexes.
	  *
	  * All indexes are expected to require notification on addition
	  * or removal of values from the cache. This trait defines dummy
	  * defaults for these operations allowing the implementing classes
	  * to compose the actual behavior of the index by using the cake
	  * pattern from various traits.
	  */
	abstract class BaseIndex {
		// Registers this index in the cache registry
		indexes += this

		/** Called when a new entry is added to the cache */
		private[Cache] def register(rx: Rx[V]): Unit = ()

		/** Called when an entry is removed from the cache */
		private[Cache] def unregister(rx: Rx[V]): Unit = ()

		/** Called when the cache is cleared, in this case, `unregister` is not called */
		private[Cache] def clear(): Unit = ()
	}

	/**
	  * Base trait for Indexes that uses an hash function.
	  *
	  * Values will automatically be hashed and `insert` and `remove` functions
	  * will be called to accommodate from changes in cached values.
	  *
	  * @tparam H the type of the hash
	  */
	trait HashingIndex[H] extends BaseIndex {
		/** A local registry of observers associated with reactive values */
		private var observers = Map[Rx[V], Obs]()

		/** The hash function to use */
		protected val hash: V => H

		/** Called when an element is inserted or modified */
		protected def insert(hash: H, rx: Rx[V]): Unit

		/** Called when an element is removed or modified */
		protected def remove(hash: H, rx: Rx[V]): Unit

		/**
		  * Constructs the observer instance for a given reactive value.
		  *
		  * The observer will monitor changes of the reactive values and
		  * ensure that the index is keep in sync if the hash result for
		  * this item changes. There is no method `change`, instead `remove`
		  * and `insert` are called successively.
		  *
		  * The observer always keep a local snapshot of the previous hash
		  * value to be able to remove the previous entry even after the
		  * previous value is no longer accessible and thus hash can no
		  * longer be computed.
		  *
		  * @param currentHash the current hash of the value
		  * @param rx          the reactive value
		  */
		protected def observerFor(currentHash: H, rx: Rx[V]): Obs = new Obs {
			/** The previous hash value */
			private val previousHash: H = currentHash

			/** Called when the reactive value changes */
			protected def callback(): Unit = {
				val newHash = hash(rx.!)
				if (previousHash != newHash) {
					remove(previousHash, rx)
					insert(newHash, rx)
				}
			}
		}

		/**
		  * Registers the reactive value and its observable in the local registry.
		  *
		  * @param rx the reactive value that was just added to the cache
		  */
		private[Cache] override def register(rx: Rx[V]): Unit = {
			super.register(rx)
			val currentHash = hash(rx.!)
			val obs = observerFor(currentHash, rx)
			observers += (rx -> obs)
			rx ~> obs
			insert(currentHash, rx)
		}

		/**
		  * Removes the reactive value and its observer from the local registry.
		  *
		  * @param rx the reactive value that was just removed to the cache
		  */
		private[Cache] override def unregister(rx: Rx[V]): Unit = {
			super.unregister(rx)
			val obs = observers(rx)
			rx ~/> obs
			remove(hash(rx), rx)
			observers -= rx
		}

		/**
		  * Clears every values and observers from the local registry.
		  */
		private[Cache] override def clear(): Unit = {
			super.clear()
			for ((rx, obs) <- observers) rx ~/> obs
			observers = observers.empty
		}
	}

	/**
	  * TODO
	  *
	  * @param hash
	  * @tparam H
	  */
	class SimpleIndex[H: Ordering](protected val hash: V => H) extends BaseIndex with HashingIndex[H] {
		private val tree = Var(TreeMap[H, Var[Set[Rx[V]]]]())

		def get(key: H): Rx[Set[V]] = tree ~ (_.get(key)) ~ (_.map(_.!).getOrElse(Set.empty)) ~ (_.map(_.!))
		def from(lo: H): Rx[Set[V]] = tree ~ (_.from(lo).valuesIterator) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		def until(lo: H): Rx[Set[V]] = tree ~ (_.until(lo).valuesIterator) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		def range(lo: H, hi: H): Rx[Set[V]] = tree ~ (_.range(lo, hi).valuesIterator) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))

		protected def remove(hash: H, rx: Rx[V]): Unit = {
			val set = tree.!(hash)
			set ~= (_ - rx)
			if (set.isEmpty) tree ~= (_ - hash)
		}

		protected def insert(hash: H, rx: Rx[V]): Unit = {
			tree.get(hash) match {
				case Some(set) => set ~= (_ + rx)
				case None => tree ~= (_ + (hash -> Var(Set(rx))))
			}
		}

		private[Cache] override def clear(): Unit = {
			super.clear()
			tree := tree.!.empty
		}
	}

	/**
	  * TODO
	  *
	  * @param hash
	  * @tparam H
	  */
	class RangeIndex[H: Ordering](protected val hash: V => (H, H)) extends BaseIndex with HashingIndex[(H, H)] {
		private val tree = Var(IntervalTree[H, Var[Set[Rx[V]]]]())

		def overlapping(lo: H, up: H): Rx[Set[V]] = tree ~ (_.overlapping(lo, up)) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		def containing(lo: H, up: H): Rx[Set[V]] = tree ~ (_.containing(lo, up)) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		def contained(lo: H, up: H): Rx[Set[V]] = tree ~ (_.contained(lo, up)) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))

		protected def remove(hash: (H, H), rx: Rx[V]): Unit = {
			val (lo, up) = hash
			val set = tree.!(lo, up)
			set ~= (_ - rx)
			if (set.isEmpty) tree ~= (_.remove(lo, up))
		}

		protected def insert(hash: (H, H), rx: Rx[V]): Unit = {
			val (lo, up) = hash
			tree.get(lo, up) match {
				case Some(set) => set ~= (_ + rx)
				case None => tree ~= (_.insert(lo, up, Var(Set(rx))))
			}
		}

		private[Cache] override def clear(): Unit = {
			super.clear()
			tree := tree.!.empty
		}
	}
}
