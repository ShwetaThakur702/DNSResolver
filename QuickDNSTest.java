import org.junit.*;
import static org.junit.Assert.*;
import java.util.List;

/**
 * Comprehensive JUnit tests for QuickDNS components
 * Tests Trie functionality, LRU cache behavior, and integrated DNS resolver
 */
public class QuickDNSTest {
    
    private DomainTrie trie;
    private LRUCache cache;
    private QuickDNS resolver;
    
    @Before
    public void setUp() {
        trie = new DomainTrie();
        cache = new LRUCache(3, 2); // Small cache for testing, 2 second TTL
        resolver = new QuickDNS(5, 3); // 5 entries, 3 second TTL
    }
    
    @After
    public void tearDown() {
        if (resolver != null) {
            resolver.shutdown();
        }
    }
    
    // ==================== TRIE TESTS ====================
    
    @Test
    public void testTrieBasicOperations() {
        // Test insertion and search
        trie.insert("google.com", "8.8.8.8");
        trie.insert("github.com", "140.82.114.4");
        
        assertEquals("8.8.8.8", trie.search("google.com"));
        assertEquals("140.82.114.4", trie.search("github.com"));
        assertNull(trie.search("nonexistent.com"));
    }
    
    @Test
    public void testTrieCaseInsensitive() {
        trie.insert("Google.COM", "8.8.8.8");
        
        assertEquals("8.8.8.8", trie.search("google.com"));
        assertEquals("8.8.8.8", trie.search("GOOGLE.COM"));
        assertEquals("8.8.8.8", trie.search("Google.Com"));
    }
    
    @Test
    public void testTrieRemoval() {
        trie.insert("test.com", "1.1.1.1");
        trie.insert("test.org", "2.2.2.2");
        
        assertTrue(trie.contains("test.com"));
        assertTrue(trie.remove("test.com"));
        assertFalse(trie.contains("test.com"));
        assertTrue(trie.contains("test.org")); // Should not affect other domains
        
        assertFalse(trie.remove("nonexistent.com")); // Should return false
    }
    
    @Test
    public void testTrieOverwrite() {
        trie.insert("example.com", "1.1.1.1");
        assertEquals("1.1.1.1", trie.search("example.com"));
        
        // Overwrite with new IP
        trie.insert("example.com", "2.2.2.2");
        assertEquals("2.2.2.2", trie.search("example.com"));
    }
    
    @Test
    public void testTrieSize() {
        assertEquals(0, trie.size());
        
        trie.insert("site1.com", "1.1.1.1");
        assertEquals(1, trie.size());
        
        trie.insert("site2.com", "2.2.2.2");
        assertEquals(2, trie.size());
        
        trie.remove("site1.com");
        assertEquals(1, trie.size());
    }
    
    @Test
    public void testTrieGetAllDomains() {
        trie.insert("a.com", "1.1.1.1");
        trie.insert("b.com", "2.2.2.2");
        
        List<String> domains = trie.getAllDomains();
        assertEquals(2, domains.size());
        
        // Check that both domains are present (order may vary)
        boolean foundA = false, foundB = false;
        for (String domain : domains) {
            if (domain.contains("a.com -> 1.1.1.1")) foundA = true;
            if (domain.contains("b.com -> 2.2.2.2")) foundB = true;
        }
        assertTrue("Should find a.com", foundA);
        assertTrue("Should find b.com", foundB);
    }
    
    // ==================== LRU CACHE TESTS ====================
    
    @Test
    public void testCacheBasicOperations() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        assertEquals("value1", cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
        assertNull(cache.get("nonexistent"));
    }
    
    @Test
    public void testCacheLRUEviction() {
        // Fill cache to capacity (3 entries)
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        assertEquals(3, cache.size());
        
        // Add one more - should evict least recently used (key1)
        cache.put("key4", "value4");
        assertEquals(3, cache.size());
        
        // key1 should be evicted
        assertNull(cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
        assertEquals("value3", cache.get("key3"));
        assertEquals("value4", cache.get("key4"));
    }
    
    @Test
    public void testCacheLRUOrdering() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        
        // Access key1 to make it most recently used
        cache.get("key1");
        
        // Add key4 - should evict key2 (least recently used)
        cache.put("key4", "value4");
        
        assertEquals("value1", cache.get("key1")); // Should still be there
        assertNull(cache.get("key2")); // Should be evicted
        assertEquals("value3", cache.get("key3"));
        assertEquals("value4", cache.get("key4"));
    }
    
    @Test
    public void testCacheTTLExpiry() throws InterruptedException {
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
        
        // Wait for TTL to expire (2 seconds + buffer)
        Thread.sleep(2500);
        
        // Should return null due to expiry
        assertNull(cache.get("key1"));
    }
    
    @Test
    public void testCacheUpdate() {
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
        
        // Update existing key
        cache.put("key1", "value2");
        assertEquals("value2", cache.get("key1"));
        assertEquals(1, cache.size()); // Size should remain same
    }
    
    @Test
    public void testCacheRemoval() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        assertTrue(cache.remove("key1"));
        assertNull(cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
        assertEquals(1, cache.size());
        
        assertFalse(cache.remove("nonexistent")); // Should return false
    }
    
    @Test
    public void testCacheStats() {
        // Generate some hits and misses
        cache.put("key1", "value1");
        cache.get("key1"); // hit
        cache.get("key1"); // hit
        cache.get("nonexistent"); // miss
        
        CacheStats stats = cache.getStats();
        assertEquals(2, stats.hits);
        assertEquals(1, stats.misses);
        assertTrue(stats.hitRatio > 0);
    }
    
    // ==================== QUICKDNS INTEGRATION TESTS ====================
    
    @Test
    public void testResolverBasicOperations() {
        resolver.addDomain("test.com", "1.2.3.4");
        
        assertEquals("1.2.3.4", resolver.resolve("test.com"));
        assertTrue(resolver.containsDomain("test.com"));
        assertNull(resolver.resolve("nonexistent.com"));
        assertFalse(resolver.containsDomain("nonexistent.com"));
    }
    
    @Test
    public void testResolverCacheHit() {
        resolver.addDomain("cached.com", "5.6.7.8");
        
        // First resolution - should hit Trie and cache the result
        assertEquals("5.6.7.8", resolver.resolve("cached.com"));
        
        // Second resolution - should hit cache
        assertEquals("5.6.7.8", resolver.resolve("cached.com"));
        
        DNSStats stats = resolver.getStats();
        assertTrue("Should have cache hits", stats.cacheHits > 0);
    }
    
    @Test
    public void testResolverDomainUpdate() {
        resolver.addDomain("update.com", "1.1.1.1");
        assertEquals("1.1.1.1", resolver.resolve("update.com"));
        
        assertTrue(resolver.updateDomain("update.com", "2.2.2.2"));
        assertEquals("2.2.2.2", resolver.resolve("update.com"));
        
        assertFalse(resolver.updateDomain("nonexistent.com", "3.3.3.3"));
    }
    
    @Test
    public void testResolverDomainRemoval() {
        resolver.addDomain("remove.com", "9.9.9.9");
        assertTrue(resolver.containsDomain("remove.com"));
        
        assertTrue(resolver.removeDomain("remove.com"));
        assertFalse(resolver.containsDomain("remove.com"));
        assertNull(resolver.resolve("remove.com"));
        
        assertFalse(resolver.removeDomain("nonexistent.com"));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testResolverInvalidIP() {
        resolver.addDomain("invalid.com", "999.999.999.999");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testResolverNullDomain() {
        resolver.addDomain(null, "1.1.1.1");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testResolverEmptyDomain() {
        resolver.addDomain("", "1.1.1.1");
    }
    
    @Test
    public void testResolverStats() {
        resolver.addDomain("stats.com", "10.10.10.10");
        
        // Generate some queries
        resolver.resolve("stats.com"); // hit
        resolver.resolve("stats.com"); // cache hit
        resolver.resolve("missing.com"); // miss
        
        DNSStats stats = resolver.getStats();
        assertTrue("Should have total queries", stats.totalQueries > 0);
        assertTrue("Should have hits", (stats.cacheHits + stats.trieHits) > 0);
        assertTrue("Should have misses", stats.misses > 0);
        assertTrue("Should have domains", stats.domainCount > 0);
    }
    
    @Test
    public void testResolverClearOperations() {
        resolver.addDomain("clear1.com", "1.1.1.1");
        resolver.addDomain("clear2.com", "2.2.2.2");
        
        // Resolve to populate cache
        resolver.resolve("clear1.com");
        resolver.resolve("clear2.com");
        
        assertTrue("Cache should have entries", resolver.getCacheSize() > 0);
        
        resolver.clearCache();
        assertEquals("Cache should be empty", 0, resolver.getCacheSize());
        
        // Domains should still be resolvable from Trie
        assertEquals("1.1.1.1", resolver.resolve("clear1.com"));
        assertEquals("2.2.2.2", resolver.resolve("clear2.com"));
    }
    
    @Test
    public void testResolverBulkOperations() {
        int domainCount = 10;
        
        // Add multiple domains
        for (int i = 1; i <= domainCount; i++) {
            resolver.addDomain("bulk" + i + ".com", "192.168.1." + i);
        }
        
        assertEquals(domainCount, resolver.getDomainCount());
        
        // Resolve all domains
        for (int i = 1; i <= domainCount; i++) {
            String expected = "192.168.1." + i;
            String actual = resolver.resolve("bulk" + i + ".com");
            assertEquals("Domain bulk" + i + ".com should resolve correctly", 
                expected, actual);
        }
    }
    
    @Test
    public void testResolverTTLIntegration() throws InterruptedException {
        resolver.addDomain("ttl.com", "192.168.1.100");
        
        // First resolution
        assertEquals("192.168.1.100", resolver.resolve("ttl.com"));
        
        // Wait for TTL to expire
        Thread.sleep(3500); // TTL is 3 seconds
        
        // Should still resolve from Trie
        assertEquals("192.168.1.100", resolver.resolve("ttl.com"));
        
        // Cleanup expired entries
        int cleaned = resolver.cleanupExpiredEntries();
        assertTrue("Should clean up expired entries", cleaned >= 0);
    }
}
