package com.creditbureau.simulator.reporting;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Text formatting utilities for CLI credit report output.
 */
public class ReportFormatter {

    private static final NumberFormat INR_FORMAT = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) return "₹0.00";
        return INR_FORMAT.format(amount).replace("INR", "₹").trim();
    }

    public static String divider(char c, int length) {
        return String.valueOf(c).repeat(Math.max(0, length));
    }

    public static String header(String title) {
        int width = 72;
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        sb.append(divider('=', width)).append(System.lineSeparator());
        sb.append(center(title, width)).append(System.lineSeparator());
        sb.append(divider('=', width)).append(System.lineSeparator());
        return sb.toString();
    }

    public static String subHeader(String title) {
        int width = 72;
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        sb.append(title).append(System.lineSeparator());
        sb.append(divider('-', width)).append(System.lineSeparator());
        return sb.toString();
    }

    public static String center(String text, int width) {
        if (text.length() >= width) return text;
        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text;
    }
}

