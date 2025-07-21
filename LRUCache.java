import java.util.*;

/**
 * Node class for the doubly linked list used in LRU cache
 * Each node stores a key-value pair along with TTL information
 */
class CacheNode {
    String key;
    String value;
    long expiryTime;
    CacheNode prev;
    CacheNode next;
    
    public CacheNode(String key, String value, long ttlMillis) {
        this.key = key;
        this.value = value;
        this.expiryTime = System.currentTimeMillis() + ttlMillis;
        this.prev = null;
        this.next = null;
    }
    
    /**
     * Checks if the cache entry has expired
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }
}

/**
 * Custom LRU Cache implementation with TTL support
 * Uses HashMap for O(1) access and Doubly Linked List for O(1) insertion/deletion
 * 
 * Features:
 * - O(1) get and put operations
 * - Automatic TTL-based expiry
 * - LRU eviction when capacity is exceeded
 * - Thread-safe operations
 */
public class LRUCache {
    private final int capacity;
    private final long defaultTTL;
    private final Map<String, CacheNode> cache;
    private final CacheNode head;
    private final CacheNode tail;
    
    // Statistics for monitoring cache performance
    private long hits = 0;
    private long misses = 0;
    private long evictions = 0;
    
    /**
     * Constructor for LRU Cache
     * 
     * @param capacity Maximum number of entries in the cache
     * @param defaultTTLSeconds Default TTL in seconds for cache entries
     */
    public LRUCache(int capacity, long defaultTTLSeconds) {
        this.capacity = capacity;
        this.defaultTTL = defaultTTLSeconds * 1000; // Convert to milliseconds
        this.cache = new HashMap<>();
        
        // Initialize dummy head and tail nodes for easier list manipulation
        this.head = new CacheNode("", "", 0);
        this.tail = new CacheNode("", "", 0);
        head.next = tail;
        tail.prev = head;
    }
    
    /**
     * Constructor with default TTL of 300 seconds (5 minutes)
     */
    public LRUCache(int capacity) {
        this(capacity, 300);
    }
    
    /**
     * Retrieves a value from the cache
     * Time Complexity: O(1)
     * 
     * @param key The key to look up
     * @return The cached value if found and not expired, null otherwise
     */
    public synchronized String get(String key) {
        CacheNode node = cache.get(key);
        
        if (node == null) {
            misses++;
            return null;
        }
        
        // Check if entry has expired
        if (node.isExpired()) {
            remove(key);
            misses++;
            return null;
        }
        
        // Move to front (most recently used)
        moveToHead(node);
        hits++;
        return node.value;
    }
    
    /**
     * Stores a key-value pair in the cache with default TTL
     * Time Complexity: O(1)
     * 
     * @param key The key to store
     * @param value The value to associate with the key
     */
    public synchronized void put(String key, String value) {
        put(key, value, defaultTTL);
    }
    
    /**
     * Stores a key-value pair in the cache with custom TTL
     * Time Complexity: O(1)
     * 
     * @param key The key to store
     * @param value The value to associate with the key
     * @param ttlMillis TTL in milliseconds
     */
    public synchronized void put(String key, String value, long ttlMillis) {
        CacheNode existingNode = cache.get(key);
        
        if (existingNode != null) {
            // Update existing entry
            existingNode.value = value;
            existingNode.expiryTime = System.currentTimeMillis() + ttlMillis;
            moveToHead(existingNode);
            return;
        }
        
        // Create new entry
        CacheNode newNode = new CacheNode(key, value, ttlMillis);
        
        if (cache.size() >= capacity) {
            // Remove least recently used entry
            CacheNode lru = tail.prev;
            removeNode(lru);
            cache.remove(lru.key);
            evictions++;
        }
        
        // Add new entry to front
        cache.put(key, newNode);
        addToHead(newNode);
    }
    
    /**
     * Removes a specific key from the cache
     * Time Complexity: O(1)
     * 
     * @param key The key to remove
     * @return true if key was found and removed, false otherwise
     */
    public synchronized boolean remove(String key) {
        CacheNode node = cache.get(key);
        
        if (node == null) {
            return false;
        }
        
        removeNode(node);
        cache.remove(key);
        return true;
    }
    
    /**
     * Clears all expired entries from the cache
     * This method should be called periodically to maintain cache health
     * 
     * @return Number of expired entries removed
     */
    public synchronized int cleanupExpired() {
        List<String> expiredKeys = new ArrayList<>();
        
        for (CacheNode node : cache.values()) {
            if (node.isExpired()) {
                expiredKeys.add(node.key);
            }
        }
        
        for (String key : expiredKeys) {
            remove(key);
        }
        
        return expiredKeys.size();
    }
    
    /**
     * Adds a node right after the head (most recently used position)
     */
    private void addToHead(CacheNode node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
    
    /**
     * Removes a node from the doubly linked list
     */
    private void removeNode(CacheNode node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    /**
     * Moves an existing node to the head (marks as most recently used)
     */
    private void moveToHead(CacheNode node) {
        removeNode(node);
        addToHead(node);
    }
    
    /**
     * Returns the current size of the cache
     */
    public synchronized int size() {
        return cache.size();
    }
    
    /**
     * Returns the maximum capacity of the cache
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Checks if the cache is empty
     */
    public synchronized boolean isEmpty() {
        return cache.isEmpty();
    }
    
    /**
     * Clears all entries from the cache
     */
    public synchronized void clear() {
        cache.clear();
        head.next = tail;
        tail.prev = head;
    }
    
    /**
     * Returns cache hit ratio as a percentage
     */
    public synchronized double getHitRatio() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total * 100.0;
    }
    
    /**
     * Returns cache statistics for monitoring
     */
    public synchronized CacheStats getStats() {
        return new CacheStats(hits, misses, evictions, cache.size(), capacity);
    }
    
    /**
     * Returns all keys currently in the cache (for debugging)
     */
    public synchronized Set<String> getKeys() {
        return new HashSet<>(cache.keySet());
    }
    
    /**
     * Checks if a key exists in the cache and is not expired
     */
    public synchronized boolean containsKey(String key) {
        CacheNode node = cache.get(key);
        return node != null && !node.isExpired();
    }
}

/**
 * Cache statistics class for monitoring cache performance
 */
class CacheStats {
    public final long hits;
    public final long misses;
    public final long evictions;
    public final int currentSize;
    public final int capacity;
    public final double hitRatio;
    
    public CacheStats(long hits, long misses, long evictions, int currentSize, int capacity) {
        this.hits = hits;
        this.misses = misses;
        this.evictions = evictions;
        this.currentSize = currentSize;
        this.capacity = capacity;
        this.hitRatio = (hits + misses) == 0 ? 0.0 : (double) hits / (hits + misses) * 100.0;
    }
    
    @Override
    public String toString() {
        return String.format(
            "CacheStats{hits=%d, misses=%d, evictions=%d, size=%d/%d, hitRatio=%.2f%%}",
            hits, misses, evictions, currentSize, capacity, hitRatio
        );
    }
}
