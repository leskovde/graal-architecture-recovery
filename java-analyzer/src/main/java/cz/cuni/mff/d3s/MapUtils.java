package cz.cuni.mff.d3s;

import java.util.*;

public class MapUtils {
    public static <K, V extends Comparable<? super V>> List<Map.Entry<K, V>> sortByValueDescending(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        return list;
    }
}
