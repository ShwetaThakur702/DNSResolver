import java.util.*;

/**
 * TrieNode represents a single node in the Trie data structure
 * Each node can have multiple children and stores whether it's an end of a domain
 */
class TrieNode {
    Map<Character, TrieNode> children;
    boolean isEndOfDomain;
    String ipAddress;
    
    public TrieNode() {
        this.children = new HashMap<>();
        this.isEndOfDomain = false;
        this.ipAddress = null;
    }
}

/**
 * DomainTrie implements a Trie data structure optimized for domain name storage and lookup
 * Provides O(m) insertion and search where m is the length of the domain name
 */
public class DomainTrie {
    private TrieNode root;
    
    public DomainTrie() {
        this.root = new TrieNode();
    }
    
    /**
     * Inserts a domain and its corresponding IP address into the Trie
     * Time Complexity: O(m) where m is the length of the domain
     * Space Complexity: O(m) in worst case
     * 
     * @param domain The domain name to insert (e.g., "google.com")
     * @param ipAddress The IP address associated with the domain
     */
    public void insert(String domain, String ipAddress) {
        if (domain == null || domain.isEmpty()) {
            return;
        }
        
        TrieNode current = root;
        // Convert to lowercase for case-insensitive lookup
        domain = domain.toLowerCase();
        
        // Traverse through each character of the domain
        for (char c : domain.toCharArray()) {
            current.children.putIfAbsent(c, new TrieNode());
            current = current.children.get(c);
        }
        
        // Mark end of domain and store IP address
        current.isEndOfDomain = true;
        current.ipAddress = ipAddress;
    }
    
    /**
     * Searches for a domain in the Trie and returns its IP address
     * Time Complexity: O(m) where m is the length of the domain
     * 
     * @param domain The domain name to search for
     * @return The IP address if found, null otherwise
     */
    public String search(String domain) {
        if (domain == null || domain.isEmpty()) {
            return null;
        }
        
        TrieNode current = root;
        domain = domain.toLowerCase();
        
        // Traverse through each character
        for (char c : domain.toCharArray()) {
            if (!current.children.containsKey(c)) {
                return null; // Domain not found
            }
            current = current.children.get(c);
        }
        
        // Return IP address if this is end of a valid domain
        return current.isEndOfDomain ? current.ipAddress : null;
    }
    
    /**
     * Removes a domain from the Trie
     * Time Complexity: O(m) where m is the length of the domain
     * 
     * @param domain The domain name to remove
     * @return true if domain was found and removed, false otherwise
     */
    public boolean remove(String domain) {
        if (domain == null || domain.isEmpty()) {
            return false;
        }
        
        domain = domain.toLowerCase();
        return removeHelper(root, domain, 0);
    }
    
    /**
     * Helper method for recursive domain removal
     * Removes nodes that are no longer needed after domain deletion
     */
    private boolean removeHelper(TrieNode current, String domain, int index) {
        if (index == domain.length()) {
            // Reached end of domain
            if (!current.isEndOfDomain) {
                return false; // Domain doesn't exist
            }
            
            current.isEndOfDomain = false;
            current.ipAddress = null;
            
            // Return true if current node has no children (can be deleted)
            return current.children.isEmpty();
        }
        
        char c = domain.charAt(index);
        TrieNode node = current.children.get(c);
        
        if (node == null) {
            return false; // Domain doesn't exist
        }
        
        boolean shouldDeleteChild = removeHelper(node, domain, index + 1);
        
        if (shouldDeleteChild) {
            current.children.remove(c);
            // Return true if current node can be deleted
            return !current.isEndOfDomain && current.children.isEmpty();
        }
        
        return false;
    }
    
    /**
     * Returns all domains stored in the Trie with their IP addresses
     * Useful for debugging and testing
     * 
     * @return List of domain-IP pairs
     */
    public List<String> getAllDomains() {
        List<String> domains = new ArrayList<>();
        getAllDomainsHelper(root, new StringBuilder(), domains);
        return domains;
    }
    
    /**
     * Helper method for collecting all domains using DFS
     */
    private void getAllDomainsHelper(TrieNode node, StringBuilder prefix, List<String> domains) {
        if (node.isEndOfDomain) {
            domains.add(prefix.toString() + " -> " + node.ipAddress);
        }
        
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            prefix.append(entry.getKey());
            getAllDomainsHelper(entry.getValue(), prefix, domains);
            prefix.deleteCharAt(prefix.length() - 1);
        }
    }
    
    /**
     * Checks if the Trie contains a specific domain
     * 
     * @param domain The domain to check
     * @return true if domain exists, false otherwise
     */
    public boolean contains(String domain) {
        return search(domain) != null;
    }
    
    /**
     * Returns the number of domains stored in the Trie
     * 
     * @return Count of domains
     */
    public int size() {
        return countDomains(root);
    }
    
    /**
     * Helper method to count total domains in the Trie
     */
    private int countDomains(TrieNode node) {
        int count = node.isEndOfDomain ? 1 : 0;
        
        for (TrieNode child : node.children.values()) {
            count += countDomains(child);
        }
        
        return count;
    }
}
