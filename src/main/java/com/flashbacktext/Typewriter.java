package com.flashbacktext;

/**
 * Works out which letters of a Text track are showing between two keyframes.
 * Typing always runs at an even speed across the gap (it ignores the
 * keyframe's easing), so the letters arrive exactly when the next keyframe is
 * reached. If the next keyframe has different text, the old text is deleted
 * back and the new text typed in during the gap.
 */
public final class Typewriter {
	/** Text to draw and how many of its visible letters to show. */
	public record Shown(String text, int letters) {
	}

	private Typewriter() {
	}

	/** Visible letters (formatting codes like "&c" or "§c" and line breaks don't count). */
	public static int length(String text) {
		return stripCodes(text).replace("\n", "").length();
	}

	static String stripCodes(String text) {
		return text == null ? "" : text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
	}

	/** Letters showing exactly on a keyframe. */
	public static int lettersAt(String text, float reveal) {
		return Math.round(Math.max(0f, Math.min(1f, reveal)) * length(text));
	}

	/**
	 * Typing from the left keyframe's text to the right one's: letters that
	 * differ are deleted one by one, then the right keyframe's letters are
	 * typed in. Every step takes the same time, so the whole change fills the
	 * gap between the keyframes evenly. Shared whole words ("Hello world" and
	 * "Hello there") stay, and text that only grows or shrinks at the end
	 * ("Hello" and "Hello world") just types or deletes the difference.
	 *
	 * @param amount 0 at the left keyframe, 1 at the right one, in real time (no easing)
	 */
	public static Shown between(String left, float leftReveal, String right, float rightReveal, double amount) {
		left = left == null ? "" : left;
		right = right == null ? "" : right;
		int from = lettersAt(left, leftReveal);
		int to = lettersAt(right, rightReveal);
		int keep = Math.min(sharedLetters(left, right), Math.min(from, to));
		int deleting = from - keep;
		int typing = to - keep;
		int steps = deleting + typing;
		if (steps == 0) {
			return new Shown(left, from);
		}
		double step = Math.max(0.0, Math.min(1.0, amount)) * steps + 1e-6;
		if (step < deleting) {
			return new Shown(left, from - (int) Math.floor(step));
		}
		return new Shown(right, keep + (int) Math.floor(step - deleting));
	}

	/**
	 * Letters both texts start with that can stay on screen: everything if one
	 * text just carries on from the other, otherwise only whole shared words,
	 * so a different word is deleted completely and typed again.
	 */
	static int sharedLetters(String left, String right) {
		String a = stripCodes(left), b = stripCodes(right);
		int n = Math.min(a.length(), b.length());
		int i = 0;
		while (i < n && a.charAt(i) == b.charAt(i)) {
			i++;
		}
		if (i < n) {
			// The texts part ways inside a word: keep only up to the last space or line break
			int boundary = Math.max(a.lastIndexOf(' ', i - 1), a.lastIndexOf('\n', i - 1));
			i = boundary + 1;
		}
		String kept = a.substring(0, i);
		return kept.length() - (kept.length() - kept.replace("\n", "").length());
	}

}
