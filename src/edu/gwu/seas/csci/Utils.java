package edu.gwu.seas.csci;

import java.util.BitSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Utils class contains a variety of utility methods that various other
 * classes make use of. It contains various conversion methods - for instance,
 * to convert back and forth a BitSet and its numeric equivalent. It also
 * includes deep copy methods to copy the contents of one Register or BitSet to
 * another.
 * 
 * @author Nick Capurso, Alex Remily
 */
public class Utils {

	private static Context context = Context.getInstance();
	private static InstructionWriter writer = new InstructionWriter();
	static final Logger logger = LogManager.getLogger(CPU.class.getName());

	/**
	 * Converts a BitSet to its numeric equivalent (unsigned), stored in a byte.
	 * The return value can be used for numeric based comparisons.
	 * 
	 * @param set
	 *            The BitSet to be converted.
	 * @param numBits
	 *            The number of bits in the BitSet (BitSet.length method is not
	 *            sufficient due to its implementation). You can get
	 *            special-register lengths in InstructionBitFormats.java.
	 * @return The numeric value represented by the BitSet.
	 */
	public static byte convertToUnsignedByte(final BitSet set, final int numBits) {
		byte value = 0;

		for (int i = numBits - 1; i >= 0; i--)
			value += set.get(i) ? (byte) (1 << (numBits - 1 - i)) : 0;
		return value;
	}

	/**
	 * Converts a BitSet to its numeric equivalent (signed), stored in a int.
	 * Can be used for values expected to be greater than a byte (i.e.
	 * addresses).
	 * 
	 * @param set
	 *            The BitSet to be converted.
	 * @param numBits
	 *            The number of bits in the BitSet (BitSet.length method is not
	 *            sufficient due to its implementation). You can get
	 *            special-register lengths in InstructionBitFormats.java.
	 * @return The numeric value represented by the BitSet.
	 */
	public static int convertToInt(final BitSet set, final int numBits) {
		int value = 0;

		// Check if converting a BitSet which contains a negative value (MSB
		// set)
		if (set.get(0)) {
			BitSet temp = new BitSet(numBits);

			// Make a copy of the input set
			bitsetDeepCopy(set, numBits, temp, numBits);

			// Return 2's complement conversion (flip all the bits and add 1)
			temp.flip(0, numBits);
			return 0 - (convertToInt(temp, numBits) + 1);
		}

		// Positive conversion, check each bit and shift to get the appropriate
		// power of 2
		// for the bit position
		for (int i = numBits - 1; i >= 0; i--)
			value += set.get(i) ? (1 << (numBits - 1 - i)) : 0;

		return value;
	}

	/**
	 * Determines whether the n<sup>th</sup> bit in a byte is set, with zero
	 * being the LSB and 7 being the MSB.
	 * 
	 * @param b
	 *            The byte under test.
	 * @param bit
	 *            The bit (0-7) to test.
	 * @return True if the bit is set, false otherwise.
	 */
	public static boolean isBitSet(byte b, byte bit) {
		return (b & (1 << bit)) != 0;
	}

	/**
	 * Copies the contents of one BitSet to another.
	 * 
	 * @param source
	 *            The source BitSet.
	 * @param sourceBits
	 *            The number of bits represented by the source.
	 * @param destination
	 *            The destination BitSet.
	 * @param destinationBits
	 *            The number of bits represented by the destination.
	 */
	public static void bitsetDeepCopy(BitSet source, int sourceBits,
			BitSet destination, int destinationBits) {
		if (sourceBits <= destinationBits) {
			destination.clear();
			for (int i = destinationBits - sourceBits, j = 0; i < destinationBits; i++, j++)
				destination.set(i, source.get(j));

		} else {
			// Truncate
			for (int i = sourceBits - destinationBits, j = 0; i < sourceBits; i++, j++)
				destination.set(j, source.get(i));
		}
	}

	/**
	 * Prints the binary representation of a BitSet.
	 * 
	 * @param name
	 *            The name of the BitSet (i.e. "OPCODE", "ADDR", etc.)
	 * @param set
	 *            The BitSet to print.
	 * @param numBits
	 *            The number of bits in the BitSet.
	 */
	public static void bitsetToString(final String name, final BitSet set,
			final int numBits) {
		System.out.println(name + " contains: ");
		for (int i = 0; i < numBits; i++)
			System.out.print(set.get(i) == false ? "0" : "1");
		System.out.println();
	}
	
	/**
	 * Prints the binary representation of a BitSet.
	 * 
	 * @param name
	 *            The name of the BitSet (i.e. "OPCODE", "ADDR", etc.)
	 * @param set
	 *            The BitSet to print.
	 * @param numBits
	 *            The number of bits in the BitSet.
	 */
	public static String WordToString(final BitSet set, final int numBits) {
		String result = "";
		for (int i = 0; i < numBits; i++)
			result += (set.get(i) == false ? "0" : "1");
		return result;
	}

	/**
	 * TODO: Comment Me.
	 * 
	 * @param source
	 * @param target
	 * @param num_bits_to_copy
	 * @param bit_index
	 */
	public static void byteToBitSetDeepCopy(byte source, Word target,
			byte num_bits_to_copy, byte bit_index) {
		for (byte i = 0; i < num_bits_to_copy; i++) {
			if (Utils.isBitSet(source, i))
				target.set(bit_index - i);
		}
	}

	/**
	 * Converts a int value into a BitSet. If the value cannot fit into a BitSet
	 * of size setSize, it should be truncated.
	 * 
	 * @param value
	 *            The value to convert into a BitSet representation.
	 * @param setSize
	 *            The number of bits the BitSet will hold.
	 * @return
	 */
	public static BitSet intToBitSet(int value, int setSize) {
		BitSet set = new BitSet(setSize);
		// Check if value is negative (if so, BitSet needs to contain the 2's
		// complement)
		if (value < 0) {
			int absoluteVal = 0 - value;

			// Instead of flipping bits and adding one, first subtract one then
			// flip bits (makes it easier to code)
			BitSet temp = intToBitSet(absoluteVal - 1, setSize);

			// Flip all the bits
			temp.flip(0, setSize);
			return temp;
		}

		// If positive, essentially set the BitSet bit-by-bit
		for (int i = setSize - 1; i >= 0; i--) {
			set.set(i, (value & 1) == 1 ? true : false);
			value >>>= 1;
		}
		return set;
	}

	/**
	 * Converts a Register (casted to it's superclass, BitSet) to a Word (since
	 * subclasses can't be casted to subclasses)
	 * 
	 * @param set
	 *            The Register
	 * @param numBits
	 *            The number of bits represented by the Register (a Word is
	 *            18-bits)
	 * @return
	 */
	public static Word registerToWord(BitSet set, int numBits) {
		Word word = new Word();
		if (numBits <= 18) {
			for (int i = 18 - numBits, j = 0; i < 18; i++, j++)
				word.set(i, set.get(j));

		} else {
			// Truncate
			for (int i = numBits - 18, j = 0; i < numBits; i++, j++)
				word.set(j, set.get(i));
		}
		return word;
	}
}
