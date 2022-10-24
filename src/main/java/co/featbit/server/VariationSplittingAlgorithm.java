package co.featbit.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class VariationSplittingAlgorithm {
    static boolean ifKeyBelongsPercentage(String key, double[] percentageRange) {
        try {
            double min = percentageRange[0];
            double max = percentageRange[1];
            if (min == 0D && max == 1D)
                return true;
            double percentage = percentageOfKey(key);
            return percentage >= min && percentage < max;
        } catch (Exception ex) {
            return false;
        }

    }

    static double percentageOfKey(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(key.getBytes(StandardCharsets.US_ASCII));
            byte[] digest = md5.digest();
            int magicNumber = Utils.intLEFromBytes(digest);
            return Math.abs((double) magicNumber / Integer.MIN_VALUE);
        } catch (Exception ex) {
            return 0D;
        }
    }
}
