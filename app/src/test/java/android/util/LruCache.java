package android.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<K, V> {
    private final LinkedHashMap<K, V> map;
    private final int maxSize;

    public LruCache(int maxSize) {
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LruCache.this.maxSize;
            }
        };
    }

    public synchronized V get(K key) {
        if (key == null) {
            
            throw new NullPointerException("key == null");
        }
        return map.get(key);
    }

    public synchronized V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }
        return map.put(key, value);
    }

    public synchronized V remove(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }
        return map.remove(key);
    }

    public synchronized final int size() {
        return map.size();
    }
}
