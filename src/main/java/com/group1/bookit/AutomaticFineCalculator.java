//AutomaticFineCalculator() = This method helps lock this calculator tool so the computer does not try to build unnecessary copies of it.;
//How it works: It acts like a digital padlock on a blueprint. Since this file is just a collection of math rules for calculating fines, it stops the system from creating empty, standalone copies of the calculator.
//Why is it used: It keeps the program's memory clean and organized by ensuring this calculator is only used as a shared, invisible background helper.
//
//Assessment(int days, BigDecimal amount) = This method helps package the final late fee results into a secure, tamper-proof container.;
//How it works: It takes the calculated number of days a book is late and the total money owed, and seals them together in a digital envelope that cannot be changed once it is created.
//Why is it used: It guarantees that once the computer calculates the late fee, no other part of the system can accidentally change the math before the final price gets saved to the database.
//
//assess(LocalDateTime dueUtc, LocalDateTime returnedUtc, LocalDateTime serverNowUtc) = This method helps calculate exactly how much money a member owes for keeping a book past its due date.;
//How it works: It checks the date the book was supposed to be returned against the day it was actually brought back (or today's date if it is still missing). It counts the exact number of days late according to the Philippine calendar and multiplies that by the daily penalty rate of 20 PHP.
//Why is it used: It acts as the library's automated accountant, ensuring that late fees are charged fairly, accurately, and consistently without a human librarian having to do calendar math.
//
//valid(LocalDateTime value, String label) = This method helps check that a given date and time are real and make sense to the computer.;
//How it works: It looks at a provided date and makes sure it isn't completely blank or absurdly far in the past or future (like the year 500 or the year 12000), which would crash the database's storage rules.
//Why is it used: It prevents bad data, missing information, or computer glitches from feeding impossible dates into the financial calculator and breaking the library's accounting system.
//
//manila(LocalDateTime utc) = This method helps translate a global computer time into the local calendar date in the Philippines.;
//How it works: It takes the universal global time (UTC) used by the database and shifts it to the Asia/Manila time zone to figure out exactly what calendar day it is locally.
//Why is it used: It ensures that fines are calculated based on when the library actually opened or closed its doors in the Philippines, rather than unfairly charging someone a late fee based on the clock of a computer server on the other side of the world.
package com.group1.bookit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class AutomaticFineCalculator {
    public static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Manila");
    public static final BigDecimal DAILY_RATE = new BigDecimal("20.00");
    private AutomaticFineCalculator() {}

    public record Assessment(int days, BigDecimal amount) {}

    public static Assessment assess(LocalDateTime dueUtc, LocalDateTime returnedUtc,
                                    LocalDateTime serverNowUtc) {
        valid(dueUtc, "Due date");
        valid(serverNowUtc, "Server time");
        if (returnedUtc != null) valid(returnedUtc, "Return date");
        LocalDateTime end = returnedUtc == null || returnedUtc.isAfter(serverNowUtc)
                ? serverNowUtc : returnedUtc;
        LocalDate due = manila(dueUtc);
        LocalDate last = manila(end);
        long days = Math.max(0, ChronoUnit.DAYS.between(due, last));
        BigDecimal amount = DAILY_RATE.multiply(BigDecimal.valueOf(days));
        if (days > Integer.MAX_VALUE || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException("The automatic fine exceeds the database's supported range.");
        }
        return new Assessment((int) days, amount);
    }

    static void valid(LocalDateTime value, String label) {
        Objects.requireNonNull(value, label + " is required.");
        if (value.getYear() < 1000 || value.getYear() > 9999) {
            throw new IllegalArgumentException(label + " is outside MySQL DATETIME's supported range.");
        }
        manila(value);
    }

    private static LocalDate manila(LocalDateTime utc) {
        LocalDate local = utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(LIBRARY_ZONE).toLocalDate();
        if (local.getYear() < 1000 || local.getYear() > 9999) {
            throw new IllegalArgumentException("The Manila calendar date is outside the EXPECTED range.");
        }
        return local;
    }
}
