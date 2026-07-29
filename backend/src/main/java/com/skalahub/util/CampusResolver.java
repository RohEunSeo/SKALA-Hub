// classNum("N반")에서 층(4층/5층)을 파생 - users.campus 컬럼은 전원 "판교"로 동일해 층 구분에 쓸 수 없음
package com.skalahub.util;

public final class CampusResolver {

    private CampusResolver() {}

    // 1~5반 -> 4층, 6~10반 -> 5층
    public static String resolveFloor(String classNum) {
        if (classNum == null) {
            return null;
        }
        String digits = classNum.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        int n = Integer.parseInt(digits);
        if (n >= 1 && n <= 5) {
            return "4층";
        }
        if (n >= 6 && n <= 10) {
            return "5층";
        }
        return null;
    }
}
