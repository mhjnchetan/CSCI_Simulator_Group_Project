/**
 * 
 */
package edu.gwu.seas.csci;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Stack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reads input and loads it into {@link Memory} via the CPU cache.
 * 
 * TODO: Document the InstructionFormat switching.
 * 
 * @author Alex Remily
 */
public class InstructionLoader implements Loader {

	private static final Logger logger = LogManager
			.getLogger(InstructionLoader.class.getName());

	public static final byte JUMP_INDIRECTION_ADDR = 8;
	public static final byte BOOT_PROGRAM_LOADING_ADDR = 24;
	public static final byte GENERAL_PROGRAM_LOADING_ADDR = 100;

	/**
	 * Get a reference to the CPU for access to read and write methods.
	 */
	private CPU cpu = CPU.getInstance();

	/**
	 * Provide a reference to the Computer's context.
	 */
	private Context context = Context.getInstance();

	/**
	 * Used to write instructions into Word objects in memory.
	 */
	private InstructionWriter writer = new InstructionWriter();

	/**
	 * Contains the contents of ROM.
	 */
	private BufferedReader reader = null;

	/**
	 * Keeps track of jump labels.
	 */
	private static ArrayList<LabelEntry> labelTable;

	/**
	 * Used to target memory addresses for writing.
	 */
	private short memory_location = 0;

	/**
	 * InstructionLoader is constructed with the boot program as the default
	 * target of the reader.
	 */
	public InstructionLoader() {
		InputStream in = getClass().getResourceAsStream("/boot.txt");
		reader = new BufferedReader(new InputStreamReader(in));
	}

	/**
	 * InstructionLoader is instantiated using a file that contains elements to
	 * be written into memory. If you provide a fully qualified file path as the
	 * first argument, such as that returned from a JFileChooser, then set the
	 * second argument to true. If you only provide a file name without the full
	 * path, set the second argument to false and the method will check the bin
	 * directory of the running program for the name of the file you provided in
	 * argument one.
	 * 
	 * @param file
	 *            The file name (fully-qualified or not) of the file with the
	 *            elements to be written into memory. If fully-qualified, set
	 *            the fully-qualified argument to "true". If you are providing
	 *            only the file name without a file path, then set the
	 *            fully-qualified argument to "false" and the method will check
	 *            the bin directory of the running program.
	 * @param fully_qualified
	 *            true of the file parameter represents a fully-qualified file
	 *            name; false if the file is packed in the bin directory of the
	 *            program jar file.
	 */
	public InstructionLoader(String file, boolean fully_qualified) {
		InputStream in;
		/*
		 * This is a hack to get the paragraph for program 2 loaded into memory
		 * along with the program instructions themselves.
		 * 
		 * TODO: Find a better way to load program data.
		 */
		if (file.contains("program2.txt")) {
			System.out.println("Loading paragraph into memory");
			InputStream in2 = getClass().getResourceAsStream("/paragraph.txt");
			BufferedReader paragraphReader = new BufferedReader(
					new InputStreamReader(in2));
			int c = 0;
			int memoryLoc = 1000;

			try {
				while ((c = paragraphReader.read()) != -1) {
					Word word = Utils.registerToWord(Utils.intToBitSet(c, 18),
							18);
					cpu.writeToMemory(word, memoryLoc++);
				}
				Word word = Utils.registerToWord(Utils.intToBitSet(4, 18), 18);
				cpu.writeToMemory(word, memoryLoc);
			} catch (IOException e) {

			}
		}
		/*
		 * The fully-qualified parameter should probably be replaced by an
		 * overloaded one-arg constructor.
		 */
		if (fully_qualified) {
			try {
				in = new FileInputStream(file);
				reader = new BufferedReader(new InputStreamReader(in));
			} catch (FileNotFoundException e) {
				logger.error(e);
			}
		} else {
			in = getClass().getResourceAsStream("/" + file);
			reader = new BufferedReader(new InputStreamReader(in));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.gwu.seas.csci.Loader#load(java.lang.Object)
	 */
	@Override
	public void load(BufferedReader reader) throws ParseException {
		labelTable = new ArrayList<LabelEntry>();
		try {
			String temp = null;
			while ((temp = reader.readLine()) != null) {
				if (temp.equals("") || temp.charAt(0) == '/') {
					logger.debug("Ignoring line: blank or a comment");
					continue;
				}
				if (temp.indexOf('/') != -1) {
					logger.debug("End-of-line comment found, ignoring");
					temp = temp.substring(0, temp.indexOf('/')).trim();
				}
				logger.debug("Checking for jump labels by looking for the colon.");
				if (temp.indexOf(':') != -1) {
					String label = temp.substring(0, temp.indexOf(':')).trim();
					int labelIndex = 0;
					logger.debug("Checking if the label is already in the label table.");
					if ((labelIndex = searchLabelTable(label)) == -1) {
						labelTable.add(new LabelEntry(label, memory_location));
						logger.debug("Found new label: " + label
								+ " at address " + memory_location);
					} else {
						// If the label is already in the table, but has an
						// associated address of 0, then there are forward
						// references that need to be resolved now.
						if (labelTable.get(labelIndex).address == 0) {
							labelTable.get(labelIndex).address = memory_location;

							LabelEntry entry = labelTable.get(labelIndex);
							Stack<Short> references = entry.forwardReferences;

							// Loop until all forward references have been
							// resolved
							while (!references.isEmpty()) {
								short address = references.pop();
								Word word = cpu.readFromMemory(address);// memory.read(address);

								// If the address if above 127, then we need to
								// do indirection through address 8
								if (entry.address >= 128) {
									// Set jump indirection address
									Utils.byteToBitSetDeepCopy(
											JUMP_INDIRECTION_ADDR,
											word,
											InstructionBitFormats.LD_STR_ADDR_SIZE,
											InstructionBitFormats.LD_STR_ADDR_END);
									// Set indirection flag
									Utils.byteToBitSetDeepCopy(
											(byte) 1,
											word,
											InstructionBitFormats.LD_STR_I_SIZE,
											InstructionBitFormats.LD_STR_I_END);
									logger.debug("Resolving forward reference at address: "
											+ address
											+ ", jump address = "
											+ entry.address
											+ ", using indirection");
								} else {
									// Else, just update the address field in
									// the instruction with a forward reference
									Utils.byteToBitSetDeepCopy(
											(byte) entry.address,
											word,
											InstructionBitFormats.LD_STR_ADDR_SIZE,
											InstructionBitFormats.LD_STR_ADDR_END);
									logger.debug("Resolving forward reference at address: "
											+ address
											+ ", jump address = "
											+ entry.address);
								}

								logger.debug("Writing word " + word
										+ " to memory address " + address + ".");
								cpu.writeToMemory(word, address);
							}
						} else {
							ParseException e = new ParseException(
									"Error: Duplicate Label: " + label, 0);
							logger.error(e);
							throw e;
						}
					}
					continue;
				}

				Word word = instructionToWord(temp);

				if (word != null)
					cpu.writeToMemory(word, memory_location++);
			}
			logger.debug("Final instruction loaded at memory location "
					+ memory_location + ".");
			reader.close();
		} catch (IOException e) {
			throw new ParseException(e.getMessage(), 0);
		}
	}

	@Override
	public void load() throws NullPointerException, ParseException,
			IllegalArgumentException {
		memory_location = isAddressEmpty(BOOT_PROGRAM_LOADING_ADDR) ? BOOT_PROGRAM_LOADING_ADDR
				: GENERAL_PROGRAM_LOADING_ADDR;
		this.load(reader);
	}

	public void load(int memAddr) throws NullPointerException, ParseException,
			IllegalArgumentException {
		memory_location = (short) memAddr;
		this.load(reader);
	}

	public Word instructionToWord(String instruction) {
		String temp = instruction;
		String labelCheck;
		byte opcode, general_register, index_register, address, indirection, register_x, register_y, count, lr, al, devid;
		Word word = new Word();
		int labelIndex = 0;

		try {
			logger.debug("Reading the opcode key string from the instruction string.");
			String opcodeKeyString = temp.substring(0, 3).trim();
			logger.debug("Determining the instruction's format from the computer context.");
			Context.InstructionFormat instruction_format = context
					.getInstructionFormats().get(opcodeKeyString);
			logger.debug("Testing for a valid InstructionClass object.");
			if (instruction_format == null)
				return null;

			String instruction_elements[] = temp.split(",");
			for (int i = 0; i < instruction_elements.length; i++)
				instruction_elements[i] = instruction_elements[i].trim();

			opcode = general_register = index_register = address = indirection = register_x = register_y = count = lr = al = devid = 0;
			logger.debug("Translating opcode key string into binary opcode.");
			opcode = context.getOpCodeBytes().get(opcodeKeyString);

			logger.debug("Switching on instruction_format to determine parse logic.");
			switch (instruction_format) {
			case ONE:
				logger.debug("Executing parse logic on for case InsructionFormat.ONE.");
				if (opcodeKeyString.equals("JZ"))
					general_register = Byte.parseByte(temp.substring(3, 4));
				else
					general_register = Byte.parseByte(temp.substring(4, 5));

				index_register = Byte.parseByte(instruction_elements[1]);
				labelCheck = instruction_elements[2];
				logger.debug("Testing the address field of the instruction string for a label.");
				if (Character.isAlphabetic(labelCheck.charAt(0))) {
					logger.debug("Label found.  Checking the label table for the label.");
					if ((labelIndex = searchLabelTable(labelCheck)) != -1) {
						logger.debug("Label found on the table.  Testing the label address for indirection.");
						if (labelTable.get(labelIndex).address >= 128) {
							address = JUMP_INDIRECTION_ADDR;
						} else {
							address = (byte) labelTable.get(labelIndex).address;
						}
						logger.debug("Adding this instruction to the label's list of total references.");
						labelTable.get(labelIndex).references
								.add(memory_location);
						logger.debug("Testing the instruction for a forward reference.");
						if (address == 0) {
							labelTable.get(labelIndex).forwardReferences
									.push(memory_location);
							logger.debug("Found another forward reference for label: "
									+ labelCheck
									+ " at address: "
									+ memory_location);
						} else {
							logger.debug("Label: " + labelCheck
									+ " translated to address " + address);
						}
					} else {
						logger.debug("Label not found on label table.");
						labelTable.add(new LabelEntry(labelCheck, (byte) 0,
								memory_location));
						logger.debug("Creating new label: " + labelCheck
								+ " for forward reference at address: "
								+ memory_location);
					}
				} else {
					address = Byte.parseByte(labelCheck);
				}
				logger.debug("Testing instuction string for indirection element.");
				if (instruction_elements.length < 4)
					indirection = 0;
				else
					indirection = Byte.parseByte(instruction_elements[3]);

				logger.debug("Testing if indirection is necessary to do the label jump.");
				if (address == JUMP_INDIRECTION_ADDR)
					indirection = 1;
				break;
			case TWO:
				logger.debug("Executing parse logic on for case InsructionFormat.TWO.");
				index_register = Byte.parseByte(temp.substring(4, 5));
				labelCheck = instruction_elements[1];
				logger.debug("Testing the address field of the instruction string for a label.");
				if (Character.isAlphabetic(labelCheck.charAt(0))) {
					logger.debug("Label found.  Checking the label table for the label.");
					if ((labelIndex = searchLabelTable(labelCheck)) != -1) {
						logger.debug("Label found on the table.  Testing the label address for indirection.");
						if (labelTable.get(labelIndex).address >= 128) {
							address = JUMP_INDIRECTION_ADDR;
						} else {
							address = (byte) labelTable.get(labelIndex).address;
						}
						logger.debug("Adding this instruction to the label's list of total references.");
						labelTable.get(labelIndex).references
								.add(memory_location);
						logger.debug("Testing the instruction for a forward reference.");
						if (address == 0) {
							labelTable.get(labelIndex).forwardReferences
									.push(memory_location);
							logger.debug("Found another forward reference for label: "
									+ labelCheck
									+ " at address: "
									+ memory_location);
						} else {
							logger.debug("Label: " + labelCheck
									+ " translated to address " + address);
						}
					} else {
						logger.debug("Label not found on label table.");
						labelTable.add(new LabelEntry(labelCheck, (byte) 0,
								memory_location));
						logger.debug("Creating new label: " + labelCheck
								+ " for forward reference at address: "
								+ memory_location);
					}
				} else {
					address = Byte.parseByte(labelCheck);
				}
				logger.debug("Testing instuction string for indirection element.");
				if (instruction_elements.length < 3)
					indirection = 0;
				else
					indirection = Byte.parseByte(instruction_elements[2]);
				logger.debug("Testing if indirection is necessary to do the label jump.");
				if (address == JUMP_INDIRECTION_ADDR)
					indirection = 1;
				break;
			case THREE:
				logger.debug("Executing parse logic on for case InsructionFormat.THREE.");
				general_register = Byte.parseByte(temp.substring(4, 5));
				address = Byte.parseByte(instruction_elements[1]);
				break;
			case FOUR:
				logger.debug("Executing parse logic on for case InsructionFormat.FOUR.");
				// Immed portion is optional
				try {
					address = Byte.parseByte(temp.substring(4, 5));
				} catch (NumberFormatException e) {
					address = 0;
				} catch (StringIndexOutOfBoundsException e2) {
					address = 0;
				}
				break;
			case FIVE:
				logger.debug("Executing parse logic on for case InsructionFormat.FIVE.");
				register_x = Byte.parseByte(temp.substring(4, 5));
				break;
			case SIX:
				logger.debug("Executing parse logic on for case InsructionFormat.SIX.");
				register_x = Byte.parseByte(temp.substring(4, 5));
				register_y = Byte.parseByte(instruction_elements[1]);
				break;
			case SEVEN:
				logger.debug("Executing parse logic on for case InsructionFormat.SEVEN.");
				general_register = Byte.parseByte(temp.substring(4, 5));
				count = Byte.parseByte(instruction_elements[1]);
				lr = Byte.parseByte(instruction_elements[2]);
				al = Byte.parseByte(instruction_elements[3]);
				break;
			case EIGHT:
				logger.debug("Executing parse logic on for case InsructionFormat.EIGHT.");
				if (opcodeKeyString.equals("IN"))
					general_register = Byte.parseByte(temp.substring(3, 4));
				else
					general_register = Byte.parseByte(temp.substring(4, 5));
				devid = Byte.parseByte(instruction_elements[1]);
				break;
			default:
				break;
			}

			logger.debug("Switching on instruction_format to determine write logic.");
			switch (instruction_format) {
			case ONE:
			case TWO:
			case THREE:
			case FOUR:
				logger.debug("Writing: opcode = " + opcode + ", R = "
						+ general_register + ", X = " + index_register
						+ ", I = " + indirection + ", ADDR = " + address);
				writer.writeLoadStoreFormatInstruction(word, opcode,
						general_register, index_register, indirection, address);
				break;
			case FIVE:
			case SIX:
				logger.debug("Writing: opcode = " + opcode + ", RX = "
						+ register_x + ", RY = " + register_y);
				writer.writeXYArithInstruction(word, opcode, register_x,
						register_y);
				break;
			case SEVEN:
				logger.debug("Writing: opcode = " + opcode + ", R = "
						+ general_register + ", COUNT = " + count + ", LR = "
						+ lr + ", AL = " + al);
				writer.writeShiftInstruction(word, opcode, general_register,
						count, lr, al);
				break;
			case EIGHT:
				logger.debug("Writing: opcode= " + opcode + ", R= "
						+ general_register + ", DEVID = " + devid);
				writer.writeIOInstruction(word, opcode, general_register, devid);
				break;
			default:
				break;
			}
			return word;
		} catch (Exception e) {
			logger.error("Illegal Operation Code.", e);
			return null;
		}
	}

	/**
	 * Searches the label table for the given string.
	 * 
	 * @param searchString
	 * @return Index of the label within the table, or -1 if not found
	 */
	public int searchLabelTable(String searchString) {
		for (int i = 0; i < labelTable.size(); i++)
			if (labelTable.get(i).label.equals(searchString))
				return i;
		return -1;
	}

	/**
	 * Returns the jump address based on an instruction's location in memory.
	 * Essentially searches the references for each label, and returns the
	 * address of the label which the passed address references.
	 * 
	 * Ex. An instruction at memory location 50 references "LABEL". The address
	 * of "LABEL" will be returned.
	 * 
	 * @param instructionAddress
	 *            The address of the instruction.
	 * @return The address of the label (i.e. the jump address)
	 */
	public static short getJumpAddrFromReference(short instructionAddress) {
		short jumpAddr = 0;

		OuterLoop: for (int i = 0; i < labelTable.size(); i++) {
			ArrayList<Short> references = labelTable.get(i).references;
			for (int j = 0; j < references.size(); j++) {
				if (references.get(j) == instructionAddress) {
					jumpAddr = labelTable.get(i).address;
					break OuterLoop;
				}
			}
		}
		return jumpAddr;
	}

	/**
	 * Tests a memory address for contents.
	 * 
	 * @param address
	 *            The memory location to test for contents.
	 * @param cpu
	 *            The CPU that reads the memory address.
	 * @return true if the address is empty; false otherwise.
	 */
	public boolean isAddressEmpty(int address) {
		Word word = Memory.getInstance().read(address);
		return word.isEmpty();
	}

	/**
	 * Entry for the label table. Holds the label and its address. A stack is
	 * used to keep track of forward references, so they can be resolved when
	 * the label is finally found. An ArrayList is used to keep track of all
	 * references to the label - forward or backward.
	 */
	class LabelEntry {
		public String label;
		public short address;
		public Stack<Short> forwardReferences;
		public ArrayList<Short> references;

		public LabelEntry(String label, short address) {
			this.label = label;
			this.address = address;
			forwardReferences = new Stack<Short>();
			references = new ArrayList<Short>();
		}

		public LabelEntry(String label, short address, short forwardRefAddress) {
			this.label = label;
			this.address = address;
			forwardReferences = new Stack<Short>();
			forwardReferences.push(forwardRefAddress);
			references = new ArrayList<Short>();
			references.add(forwardRefAddress);
		}
	}
}
