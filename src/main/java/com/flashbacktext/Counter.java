package com.flashbacktext;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Counter numbers: what replaces {n} in the text. */
public final class Counter {
	public static final String TOKEN = "{n}";
	public static final String[] STYLES = {"1,000", "1000", "1.0K", "00:42"};

	private Counter() {
	}

	public static String format(double value, int style, int decimals) {
		decimals = Math.max(0, Math.min(6, decimals));
		switch (style) {
			case 1:
				return pattern(decimals, false).format(round(value, decimals));
			case 2: {
				double abs = Math.abs(value);
				String[] units = {"", "K", "M", "B", "T"};
				int unit = 0;
				while (abs >= 1000 && unit < units.length - 1) {
					abs /= 1000;
					unit++;
				}
				int places = unit == 0 ? decimals : Math.max(1, decimals);
				return (value < 0 ? "-" : "") + pattern(places, false).format(round(abs, places)) + units[unit];
			}
			case 3: {
				long total = (long) Math.floor(Math.abs(value));
				long hours = total / 3600, minutes = (total / 60) % 60, seconds = total % 60;
				String sign = value < 0 ? "-" : "";
				return hours > 0 ? String.format(Locale.ROOT, "%s%d:%02d:%02d", sign, hours, minutes, seconds)
						: String.format(Locale.ROOT, "%s%02d:%02d", sign, minutes, seconds);
			}
			default:
				return pattern(decimals, true).format(round(value, decimals));
		}
	}

	/** Puts the number in place of {n}; drops a {n} cut in half by the typewriter. */
	public static String apply(String shown, String number) {
		if (shown.endsWith("{n")) {
			shown = shown.substring(0, shown.length() - 2);
		} else if (shown.endsWith("{")) {
			shown = shown.substring(0, shown.length() - 1);
		}
		return shown.replace(TOKEN, number);
	}

	private static double round(double v, int decimals) {
		double f = Math.pow(10, decimals);
		return Math.round(v * f) / f;
	}

	private static DecimalFormat pattern(int decimals, boolean grouping) {
		StringBuilder p = new StringBuilder(grouping ? "#,##0" : "0");
		if (decimals > 0) {
			p.append('.').append("0".repeat(decimals));
		}
		return new DecimalFormat(p.toString(), DecimalFormatSymbols.getInstance(Locale.ROOT));
	}
}
