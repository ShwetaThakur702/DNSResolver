import java.util.*;

/**
 * QuickDNS Demo Application
 * 
 * Demonstrates the capabilities of the QuickDNS resolver including:
 * - Domain addition and resolution
 * - Cache performance with TTL
 * - LRU eviction behavior
 * - Performance statistics
 * - Bulk operations
 */
public class QuickDNSDemo {
    
    public static void main(String[] args) {
        System.out.println("=== QuickDNS High-Performance DNS Resolver Demo ===\n");
        
        // Initialize resolver with small cache for demonstration
        QuickDNS resolver = new QuickDNS(5, 10); // 5 entries, 10 seconds TTL
        
        try {
            // Demo 1: Basic Operations
            basicOperationsDemo(resolver);
            
            // Demo 2: Cache Performance
            cachePerformanceDemo(resolver);
            
            // Demo 3: TTL Expiry
            ttlExpiryDemo(resolver);
            
            // Demo 4: LRU Eviction
            lruEvictionDemo(resolver);
            
            // Demo 5: Bulk Operations
            bulkOperationsDemo(resolver);
            
            // Demo 6: Performance Statistics
            performanceStatsDemo(resolver);
            
        } finally {
            // Clean shutdown
            resolver.shutdown();
        }
        
        System.out.println("\n=== Demo Complete ===");
    }
    
    /**
     * Demonstrates basic domain operations
     */
    private static void basicOperationsDemo(QuickDNS resolver) {
        System.out.println("1. BASIC OPERATIONS DEMO");
        System.out.println("========================");
        
        // Add some popular domains
        resolver.addDomain("google.com", "142.250.191.14");
        resolver.addDomain("github.com", "140.82.114.4");
        resolver.addDomain("stackoverflow.com", "151.101.1.69");
        resolver.addDomain("youtube.com", "142.250.191.46");
        
        System.out.println("Added 4 domains to resolver");
        
        // Test resolution
        System.out.println("\nResolving domains:");
        String[] testDomains = {"google.com", "github.com", "nonexistent.com"};
        
        for (String domain : testDomains) {
            String ip = resolver.resolve(domain);
            System.out.printf("  %-20s -> %s\n", domain, 
                ip != null ? ip : "NOT FOUND");
        }
        
        // Test domain existence
        System.out.println("\nChecking domain existence:");
        System.out.printf("  google.com exists: %b\n", resolver.containsDomain("google.com"));
        System.out.printf("  missing.com exists: %b\n", resolver.containsDomain("missing.com"));
        
        // Update domain
        System.out.println("\nUpdating google.com IP address:");
        resolver.updateDomain("google.com", "8.8.8.8");
        System.out.printf("  google.com -> %s\n", resolver.resolve("google.com"));
        
        System.out.println();
    }
    
    /**
     * Demonstrates cache performance benefits
     */
    private static void cachePerformanceDemo(QuickDNS resolver) {
        System.out.println("2. CACHE PERFORMANCE DEMO");
        System.out.println("=========================");
        
        String domain = "performance-test.com";
        resolver.addDomain(domain, "192.168.1.100");
        
        // Measure resolution times
        long startTime, endTime;
        int iterations = 1000;
        
        // First resolution (from Trie)
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            resolver.resolve(domain);
        }
        endTime = System.nanoTime();
        
        long avgTimeNanos = (endTime - startTime) / iterations;
        System.out.printf("Average resolution time: %.2f microseconds\n", 
            avgTimeNanos / 1000.0);
        
        // Show cache hit ratio
        DNSStats stats = resolver.getStats();
        System.out.printf("Cache hit ratio: %.2f%%\n", stats.cacheStats.hitRatio);
        System.out.printf("Total cache hits: %d\n", stats.cacheHits);
        System.out.printf("Total trie hits: %d\n", stats.trieHits);
        
        System.out.println();
    }
    
    /**
     * Demonstrates TTL expiry behavior
     */
    private static void ttlExpiryDemo(QuickDNS resolver) {
        System.out.println("3. TTL EXPIRY DEMO");
        System.out.println("==================");
        
        String domain = "ttl-test.com";
        resolver.addDomain(domain, "10.0.0.1");
        
        // Resolve immediately
        System.out.printf("Immediate resolution: %s\n", resolver.resolve(domain));
        System.out.printf("Cache size: %d\n", resolver.getCacheSize());
        
        // Wait for TTL to expire (our resolver has 10 second TTL)
        System.out.println("Waiting 11 seconds for TTL expiry...");
        try {
            Thread.sleep(11000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Resolve after expiry - should hit Trie, not cache
        System.out.printf("After TTL expiry: %s\n", resolver.resolve(domain));
        
        // Cleanup expired entries
        int cleaned = resolver.cleanupExpiredEntries();
        System.out.printf("Cleaned up %d expired entries\n", cleaned);
        System.out.printf("Cache size after cleanup: %d\n", resolver.getCacheSize());
        
        System.out.println();
    }
    
    /**
     * Demonstrates LRU eviction when cache is full
     */
    private static void lruEvictionDemo(QuickDNS resolver) {
        System.out.println("4. LRU EVICTION DEMO");
        System.out.println("====================");
        
        // Clear cache to start fresh
        resolver.clearCache();
        
        // Fill cache to capacity (5 entries)
        System.out.println("Filling cache to capacity (5 entries):");
        for (int i = 1; i <= 5; i++) {
            String domain = "site" + i + ".com";
            String ip = "192.168.1." + i;
            resolver.addDomain(domain, ip);
            System.out.printf("  Added %s -> %s\n", domain, ip);
        }
        
        System.out.printf("Cache size: %d\n", resolver.getCacheSize());
        
        // Access some entries to change LRU order
        System.out.println("\nAccessing site1.com and site3.com to make them recently used:");
        resolver.resolve("site1.com");
        resolver.resolve("site3.com");
        
        // Add one more entry to trigger eviction
        System.out.println("\nAdding site6.com (should evict least recently used):");
        resolver.addDomain("site6.com", "192.168.1.6");
        
        // Check which entries remain in cache
        System.out.println("\nChecking cache contents:");
        for (int i = 1; i <= 6; i++) {
            String domain = "site" + i + ".com";
            String ip = resolver.resolve(domain);
            boolean inCache = resolver.getCacheSize() > 0; // Simplified check
            System.out.printf("  %s -> %s %s\n", domain, ip, 
                inCache ? "(likely in cache)" : "");
        }
        
        System.out.printf("Final cache size: %d\n", resolver.getCacheSize());
        System.out.println();
    }
    
    /**
     * Demonstrates bulk operations and performance
     */
    private static void bulkOperationsDemo(QuickDNS resolver) {
        System.out.println("5. BULK OPERATIONS DEMO");
        System.out.println("=======================");
        
        // Add many domains
        int domainCount = 100;
        System.out.printf("Adding %d domains...\n", domainCount);
        
        long startTime = System.currentTimeMillis();
        for (int i = 1; i <= domainCount; i++) {
            resolver.addDomain("bulk" + i + ".com", "10.0." + (i/256) + "." + (i%256));
        }
        long endTime = System.currentTimeMillis();
        
        System.out.printf("Added %d domains in %d ms\n", domainCount, endTime - startTime);
        System.out.printf("Total domains in resolver: %d\n", resolver.getDomainCount());
        
        // Bulk resolution test
        System.out.println("\nTesting bulk resolution performance:");
        startTime = System.currentTimeMillis();
        int resolved = 0;
        
        for (int i = 1; i <= domainCount; i++) {
            String ip = resolver.resolve("bulk" + i + ".com");
            if (ip != null) resolved++;
        }
        
        endTime = System.currentTimeMillis();
        System.out.printf("Resolved %d/%d domains in %d ms\n", 
            resolved, domainCount, endTime - startTime);
        System.out.printf("Average resolution time: %.2f ms per domain\n", 
            (double)(endTime - startTime) / domainCount);
        
        System.out.println();
    }
    
    /**
     * Shows comprehensive performance statistics
     */
    private static void performanceStatsDemo(QuickDNS resolver) {
        System.out.println("6. PERFORMANCE STATISTICS");
        System.out.println("=========================");
        
        DNSStats stats = resolver.getStats();
        System.out.println(stats);
        
        // Show all domains (limited output)
        List<String> allDomains = resolver.getAllDomains();
        System.out.printf("\nTotal domains stored: %d\n", allDomains.size());
        
        if (allDomains.size() <= 10) {
            System.out.println("All domains:");
            for (String domain : allDomains) {
                System.out.println("  " + domain);
            }
        } else {
            System.out.println("First 10 domains:");
            for (int i = 0; i < 10; i++) {
                System.out.println("  " + allDomains.get(i));
            }
            System.out.println("  ... and " + (allDomains.size() - 10) + " more");
        }
        
        System.out.println();
    }
    
    /**
     * Utility method to pause execution
     */
    private static void pause(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
