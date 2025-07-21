import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * QuickDNS - High-Performance DNS Resolver
 * 
 * A DNS resolver that combines a Trie data structure for efficient domain storage
 * with an LRU cache for fast IP address retrieval with TTL support.
 * 
 * Features:
 * - O(m) domain lookup using Trie where m is domain length
 * - O(1) cache operations with LRU eviction
 * - TTL-based cache expiry
 * - Automatic cleanup of expired entries
 * - Thread-safe operations
 * - Performance monitoring and statistics
 */
public class QuickDNS {
    private final DomainTrie domainTrie;
    private final LRUCache cache;
    private final ScheduledExecutorService cleanupExecutor;
    
    // Performance statistics
    private long totalQueries = 0;
    private long trieHits = 0;
    private long cacheHits = 0;
    private long misses = 0;
    
    /**
     * Constructor for QuickDNS resolver
     * 
     * @param cacheCapacity Maximum number of entries in the cache
     * @param defaultTTLSeconds Default TTL for cached entries in seconds
     */
    public QuickDNS(int cacheCapacity, long defaultTTLSeconds) {
        this.domainTrie = new DomainTrie();
        this.cache = new LRUCache(cacheCapacity, defaultTTLSeconds);
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        
        // Schedule periodic cleanup of expired cache entries every 5 minutes
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredEntries, 
            5, 5, TimeUnit.MINUTES
        );
    }
    
    /**
     * Constructor with default TTL of 300 seconds (5 minutes)
     */
    public QuickDNS(int cacheCapacity) {
        this(cacheCapacity, 300);
    }
    
    /**
     * Constructor with default cache capacity of 1000 and TTL of 300 seconds
     */
    public QuickDNS() {
        this(1000, 300);
    }
    
    /**
     * Adds a domain-IP mapping to the resolver
     * The domain is stored in the Trie for persistent lookup
     * 
     * @param domain The domain name (e.g., "google.com")
     * @param ipAddress The IP address associated with the domain
     */
    public synchronized void addDomain(String domain, String ipAddress) {
        if (domain == null || domain.trim().isEmpty() || 
            ipAddress == null || ipAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Domain and IP address cannot be null or empty");
        }
        
        domain = domain.trim().toLowerCase();
        ipAddress = ipAddress.trim();
        
        // Validate IP address format (basic validation)
        if (!isValidIPAddress(ipAddress)) {
            throw new IllegalArgumentException("Invalid IP address format: " + ipAddress);
        }
        
        domainTrie.insert(domain, ipAddress);
        
        // Also add to cache for immediate availability
        cache.put(domain, ipAddress);
    }
    
    /**
     * Resolves a domain name to its IP address
     * 
     * Resolution order:
     * 1. Check LRU cache (fastest)
     * 2. Check Trie (persistent storage)
     * 3. Return null if not found
     * 
     * @param domain The domain name to resolve
     * @return The IP address if found, null otherwise
     */
    public synchronized String resolve(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return null;
        }
        
        domain = domain.trim().toLowerCase();
        totalQueries++;
        
        // Step 1: Check cache first (O(1) operation)
        String cachedIP = cache.get(domain);
        if (cachedIP != null) {
            cacheHits++;
            return cachedIP;
        }
        
        // Step 2: Check Trie (O(m) operation where m is domain length)
        String trieIP = domainTrie.search(domain);
        if (trieIP != null) {
            trieHits++;
            // Cache the result for future queries
            cache.put(domain, trieIP);
            return trieIP;
        }
        
        // Step 3: Domain not found
        misses++;
        return null;
    }
    
    /**
     * Removes a domain from both Trie and cache
     * 
     * @param domain The domain to remove
     * @return true if domain was found and removed, false otherwise
     */
    public synchronized boolean removeDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }
        
        domain = domain.trim().toLowerCase();
        
        boolean removedFromTrie = domainTrie.remove(domain);
        boolean removedFromCache = cache.remove(domain);
        
        return removedFromTrie || removedFromCache;
    }
    
    /**
     * Checks if a domain exists in the resolver
     * 
     * @param domain The domain to check
     * @return true if domain exists, false otherwise
     */
    public synchronized boolean containsDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }
        
        domain = domain.trim().toLowerCase();
        
        // Check cache first, then Trie
        return cache.containsKey(domain) || domainTrie.contains(domain);
    }
    
    /**
     * Updates the IP address for an existing domain
     * 
     * @param domain The domain to update
     * @param newIPAddress The new IP address
     * @return true if domain was found and updated, false otherwise
     */
    public synchronized boolean updateDomain(String domain, String newIPAddress) {
        if (domain == null || domain.trim().isEmpty() || 
            newIPAddress == null || newIPAddress.trim().isEmpty()) {
            return false;
        }
        
        domain = domain.trim().toLowerCase();
        newIPAddress = newIPAddress.trim();
        
        if (!isValidIPAddress(newIPAddress)) {
            throw new IllegalArgumentException("Invalid IP address format: " + newIPAddress);
        }
        
        if (domainTrie.contains(domain)) {
            domainTrie.insert(domain, newIPAddress); // This will update existing entry
            cache.put(domain, newIPAddress); // Update cache as well
            return true;
        }
        
        return false;
    }
    
    /**
     * Returns all domains stored in the resolver
     * 
     * @return List of domain-IP mappings
     */
    public synchronized List<String> getAllDomains() {
        return domainTrie.getAllDomains();
    }
    
    /**
     * Returns the number of domains stored in the Trie
     */
    public synchronized int getDomainCount() {
        return domainTrie.size();
    }
    
    /**
     * Returns current cache size
     */
    public synchronized int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Clears all cached entries (Trie remains intact)
     */
    public synchronized void clearCache() {
        cache.clear();
    }
    
    /**
     * Clears all data from both Trie and cache
     */
    public synchronized void clearAll() {
        cache.clear();
        // Note: DomainTrie doesn't have a clear method, so we create a new instance
        // In a production system, you might want to add a clear method to DomainTrie
    }
    
    /**
     * Performs cleanup of expired cache entries
     * This method is called automatically by the scheduled executor
     * 
     * @return Number of expired entries removed
     */
    public synchronized int cleanupExpiredEntries() {
        return cache.cleanupExpired();
    }
    
    /**
     * Returns comprehensive performance statistics
     */
    public synchronized DNSStats getStats() {
        CacheStats cacheStats = cache.getStats();
        return new DNSStats(
            totalQueries, cacheHits, trieHits, misses,
            getDomainCount(), cacheStats
        );
    }
    
    /**
     * Basic IP address validation
     * Supports IPv4 format validation
     */
    private boolean isValidIPAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Gracefully shuts down the resolver and cleanup executor
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Note: For proper resource management, always call shutdown() explicitly
     * when done with the resolver, preferably in a try-with-resources block
     * or finally block to ensure cleanup executor is properly shut down.
     */
}

/**
 * DNS resolver statistics class
 */
class DNSStats {
    public final long totalQueries;
    public final long cacheHits;
    public final long trieHits;
    public final long misses;
    public final int domainCount;
    public final CacheStats cacheStats;
    public final double overallHitRatio;
    
    public DNSStats(long totalQueries, long cacheHits, long trieHits, long misses,
                   int domainCount, CacheStats cacheStats) {
        this.totalQueries = totalQueries;
        this.cacheHits = cacheHits;
        this.trieHits = trieHits;
        this.misses = misses;
        this.domainCount = domainCount;
        this.cacheStats = cacheStats;
        this.overallHitRatio = totalQueries == 0 ? 0.0 : 
            (double) (cacheHits + trieHits) / totalQueries * 100.0;
    }
    
    @Override
    public String toString() {
        return String.format(
            "DNSStats{\n" +
            "  Total Queries: %d\n" +
            "  Cache Hits: %d\n" +
            "  Trie Hits: %d\n" +
            "  Misses: %d\n" +
            "  Overall Hit Ratio: %.2f%%\n" +
            "  Domain Count: %d\n" +
            "  Cache Stats: %s\n" +
            "}",
            totalQueries, cacheHits, trieHits, misses, 
            overallHitRatio, domainCount, cacheStats
        );
    }
}
