package android.text;

/**
 * Local unit tests run against stub Android classes that throw. The billing library calls
 * TextUtils.isEmpty while parsing product details, so tests get this one real method.
 */
public class TextUtils {
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }
}
