package com.parabbits.tajniakiserver.words;

import java.util.ArrayList;
import java.util.List;

public class ListUtils {

    public static <T> List<List<T>> splitListBySize(List<T> list, int[] sizes) {
        List<List<T>> result = new ArrayList<>();
        int lastIndex = 0;
        for (Integer size : sizes) {
            int startIndex = lastIndex;
            lastIndex = startIndex + size;
            List<T> subList = list.subList(startIndex, lastIndex);
            result.add(subList);
        }
        List<T> rest = list.subList(lastIndex, list.size());
        result.add(rest);
        return result;
    }
}