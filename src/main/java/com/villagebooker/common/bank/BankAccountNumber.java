package com.villagebooker.common.bank;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Serbian bank account number: 3-digit bank code, 13-digit account, 2 control digits (ISO 7064 MOD 97-10).
// Accepted input: "160-5100000123456-65", the same with the middle part shortened (leading zeros dropped,
// as banks often print it: "205-9011003562-09"), or 18 digits without dashes. Spaces are ignored.
// Stored form is always the full "XXX-XXXXXXXXXXXXX-XX".
public final class BankAccountNumber {

    private static final Pattern WITH_DASHES = Pattern.compile("(\\d{3})-(\\d{1,13})-(\\d{2})");
    private static final Pattern DIGITS_ONLY = Pattern.compile("(\\d{3})(\\d{13})(\\d{2})");
    private static final BigInteger MOD = BigInteger.valueOf(97);

    private BankAccountNumber() {
    }

    // empty if the format is wrong or the control digits do not match
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String compact = input.replace(" ", "");
        Matcher m = WITH_DASHES.matcher(compact);
        if (!m.matches()) {
            m = DIGITS_ONLY.matcher(compact);
            if (!m.matches()) {
                return Optional.empty();
            }
        }
        String bank = m.group(1);
        String account = "0".repeat(13 - m.group(2).length()) + m.group(2);
        String control = m.group(3);

        // with the control digits appended, a valid number gives remainder 1
        if (!new BigInteger(bank + account + control).mod(MOD).equals(BigInteger.ONE)) {
            return Optional.empty();
        }
        return Optional.of(bank + "-" + account + "-" + control);
    }
}
