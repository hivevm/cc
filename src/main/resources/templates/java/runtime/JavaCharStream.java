// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/resources/templates/JavaCharStream.template, src/main/resources/templates/SimpleCharStream.template

//@if(JAVA_PACKAGE)
package __JAVA_PACKAGE__;
//@fi

/**
 * An implementation of interface CharStream, where the stream is assumed to contain only ASCII
 * characters (with java-like unicode escape processing).
 */
class JavaCharStream {

	private static final int BUFFER_SIZE = 4096;

//@if(JAVA_UNICODE_ESCAPE)
	static int hexval(char c) throws java.io.IOException {
		return switch (c) {
			case '0' -> 0;
			case '1' -> 1;
			case '2' -> 2;
			case '3' -> 3;
			case '4' -> 4;
			case '5' -> 5;
			case '6' -> 6;
			case '7' -> 7;
			case '8' -> 8;
			case '9' -> 9;
			case 'a', 'A' -> 10;
			case 'b', 'B' -> 11;
			case 'c', 'C' -> 12;
			case 'd', 'D' -> 13;
			case 'e', 'E' -> 14;
			case 'f', 'F' -> 15;
			default -> throw new java.io.IOException(); // Should never come here
		};
	}
//@fi

	/**
	 * Position in buffer.
	 */
	private int   bufpos = -1;
	private int   bufsize;
	private int   available;
	private int   tokenBegin;
	private int[] bufline;
	private int[] bufcolumn;

	private int column = 0;
	private int line   = 1;

	private boolean prevCharIsCR = false;
	private boolean prevCharIsLF = false;

	private final Provider inputStream;

	private char[]  nextCharBuf;
	private char[]  buffer;
	private int     maxNextCharInd  = 0;
	private int     nextCharInd     = -1;
	private int     inBuf           = 0;
	private int     tabSize         = 1;
	private boolean trackLineColumn = true;

	public void setTabSize(int i) {
		this.tabSize = i;
	}

	public int getTabSize(int i) {
		return this.tabSize;
	}

	protected void ExpandBuff(boolean wrapAround) {
		char[] newbuffer = new char[this.bufsize + 2048];
//@if(KEEP_LINE_COLUMN)
		int[] newbufline = new int[this.bufsize + 2048];
		int[] newbufcolumn = new int[this.bufsize + 2048];
//@fi
		try {
			if (wrapAround) {
				System.arraycopy(this.buffer, this.tokenBegin, newbuffer, 0,
						this.bufsize - this.tokenBegin);
				System.arraycopy(this.buffer, 0, newbuffer, this.bufsize - this.tokenBegin,
						this.bufpos);
				this.buffer = newbuffer;
//@if(KEEP_LINE_COLUMN)
				System.arraycopy(this.bufline, this.tokenBegin, newbufline, 0,
						this.bufsize - this.tokenBegin);
				System.arraycopy(this.bufline, 0, newbufline, this.bufsize - this.tokenBegin,
						this.bufpos);
				this.bufline = newbufline;

				System.arraycopy(this.bufcolumn, this.tokenBegin, newbufcolumn, 0,
						this.bufsize - this.tokenBegin);
				System.arraycopy(this.bufcolumn, 0, newbufcolumn, this.bufsize - this.tokenBegin,
						this.bufpos);
				this.bufcolumn = newbufcolumn;
//@fi
				this.bufpos += (this.bufsize - this.tokenBegin);
			} else {
				System.arraycopy(this.buffer, this.tokenBegin, newbuffer, 0,
						this.bufsize - this.tokenBegin);
				this.buffer = newbuffer;
//@if(KEEP_LINE_COLUMN)
				System.arraycopy(this.bufline, this.tokenBegin, newbufline, 0,
						this.bufsize - this.tokenBegin);
				this.bufline = newbufline;

				System.arraycopy(this.bufcolumn, this.tokenBegin, newbufcolumn, 0,
						this.bufsize - this.tokenBegin);
				this.bufcolumn = newbufcolumn;
//@fi
				this.bufpos -= this.tokenBegin;
			}
		} catch (Exception t) {
			throw new RuntimeException(t.getMessage());
		}

		this.available = (this.bufsize += 2048);
		this.tokenBegin = 0;
	}

	protected void FillBuff() throws java.io.IOException {
		int i;
		if (this.maxNextCharInd == JavaCharStream.BUFFER_SIZE) {
			this.maxNextCharInd = this.nextCharInd = 0;
		}

		try {
			if ((i = this.inputStream.read(this.nextCharBuf, this.maxNextCharInd,
					JavaCharStream.BUFFER_SIZE - this.maxNextCharInd)) == -1) {
				this.inputStream.close();
				throw new java.io.IOException();
			} else {
				this.maxNextCharInd += i;
			}
		} catch (java.io.IOException e) {
			if (this.bufpos != 0) {
				--this.bufpos;
				backup(0);
//@if(KEEP_LINE_COLUMN)
			} else {
				this.bufline[this.bufpos] = this.line;
				this.bufcolumn[this.bufpos] = this.column;
//@fi
			}
			throw e;
		}
	}

	protected char ReadByte() throws java.io.IOException {
		if (++this.nextCharInd >= this.maxNextCharInd) {
			FillBuff();
		}

		return this.nextCharBuf[this.nextCharInd];
	}

	/**
	 * @return starting character for token, a code point (ADR-0029).
	 */
	public int BeginToken() throws java.io.IOException {
		if (this.inBuf > 0) {
			--this.inBuf;

			if (++this.bufpos == this.bufsize) {
				this.bufpos = 0;
			}

			this.tokenBegin = this.bufpos;
			return withLowSurrogate(this.buffer[this.bufpos]);
		}

		this.tokenBegin = 0;
		this.bufpos = -1;

		return readChar();
	}

	protected void AdjustBuffSize() {
		if (this.available == this.bufsize) {
			if (this.tokenBegin > 2048) {
				this.bufpos = 0;
				this.available = this.tokenBegin;
			} else {
				ExpandBuff(false);
			}
		} else if (this.available > this.tokenBegin) {
			this.available = this.bufsize;
		} else if ((this.tokenBegin - this.available) < 2048) {
			ExpandBuff(true);
		} else {
			this.available = this.tokenBegin;
		}
	}
//@if(KEEP_LINE_COLUMN)

	protected void UpdateLineColumn(char c) {
		// A column is a character, not a UTF-16 unit (ADR-0029): the second half of a surrogate
		// pair stays in the column of the first.
		if (Character.isLowSurrogate(c)) {
			this.bufline[this.bufpos] = this.line;
			this.bufcolumn[this.bufpos] = this.column;
			return;
		}
		this.column++;

		if (this.prevCharIsLF) {
			this.prevCharIsLF = false;
			this.line += (this.column = 1);
		} else if (this.prevCharIsCR) {
			this.prevCharIsCR = false;
			if (c == '\n') {
				this.prevCharIsLF = true;
			} else {
				this.line += (this.column = 1);
			}
		}

		switch (c) {
			case '\r':
				this.prevCharIsCR = true;
				break;
			case '\n':
				this.prevCharIsLF = true;
				break;
			case '\t':
				this.column--;
				this.column += (this.tabSize - (this.column % this.tabSize));
				break;
			default:
				break;
		}

		this.bufline[this.bufpos] = this.line;
		this.bufcolumn[this.bufpos] = this.column;
	}
//@fi

	/**
	 * Reads a character: a Unicode code point, from the two UTF-16 units of a surrogate pair when
	 * it is one. The lexer counts characters, not units (ADR-0029).
	 */
	public int readChar() throws java.io.IOException {
		return withLowSurrogate(readUnit());
	}

	/**
	 * The code point that starts with {@code unit}: a high surrogate followed by a low one is one
	 * character. A lone surrogate is returned as it is; no character list matches it.
	 */
	private int withLowSurrogate(char unit) throws java.io.IOException {
		if (!Character.isHighSurrogate(unit)) {
			return unit;
		}
		char low;
		try {
			low = readUnit();
		} catch (java.io.IOException e) {
			return unit; // the input ends after a lone high surrogate
		}
		if (Character.isLowSurrogate(low)) {
			return Character.toCodePoint(unit, low);
		}
		backupUnits(1);
		return unit;
	}

	/**
	 * Reads one UTF-16 unit.
	 */
	private char readUnit() throws java.io.IOException {
		if (this.inBuf > 0) {
			--this.inBuf;

			if (++this.bufpos == this.bufsize) {
				this.bufpos = 0;
			}

			return this.buffer[this.bufpos];
		}

		char c;

		if (++this.bufpos == this.available) {
			AdjustBuffSize();
		}

		this.buffer[this.bufpos] = c = ReadByte();
//@if(JAVA_UNICODE_ESCAPE)
		// Java's Unicode escapes are decoded only when the grammar asks for it (ADR-0029).
		if (c == '\\') {
//@if(KEEP_LINE_COLUMN)
			UpdateLineColumn(c);
//@fi

			int backSlashCnt = 1;

			for (; ; ) // Read all the backslashes
			{
				if (++this.bufpos == this.available) {
					AdjustBuffSize();
				}

				try {
					if ((this.buffer[this.bufpos] = c = ReadByte()) != '\\') {
//@if(KEEP_LINE_COLUMN)
						UpdateLineColumn(c);
//@fi
						// found a non-backslash char.
						if ((c == 'u') && ((backSlashCnt & 1) == 1)) {
							if (--this.bufpos < 0) {
								this.bufpos = this.bufsize - 1;
							}

							break;
						}

						backupUnits(backSlashCnt);
						return '\\';
					}
				} catch (java.io.IOException e) {
					// We are returning one backslash so we should only backup (count-1)
					if (backSlashCnt > 1) {
						backupUnits(backSlashCnt - 1);
					}

					return '\\';
				}

//@if(KEEP_LINE_COLUMN)
				UpdateLineColumn(c);
//@fi
				backSlashCnt++;
			}

			// Here, we have seen an odd number of backslash's followed by a 'u'
			try {
				while ((c = ReadByte()) == 'u') {
//@if(KEEP_LINE_COLUMN)
					++this.column;
//@else
					;
//@fi
				}

				this.buffer[this.bufpos] =
						c = (char) ((JavaCharStream.hexval(c) << 12) | (
								JavaCharStream.hexval(ReadByte()) << 8)
								| (JavaCharStream.hexval(ReadByte()) << 4) | JavaCharStream.hexval(
								ReadByte()));

//@if(KEEP_LINE_COLUMN)
				this.column += 4;
//@fi
			} catch (java.io.IOException e) {
//@if(KEEP_LINE_COLUMN)
				throw new RuntimeException(
						"Invalid escape character at line " + this.line + " column " + this.column
								+ ".");
//@else
				throw new RuntimeException("Invalid escape character in input");
//@fi
			}

			if (backSlashCnt == 1) {
				return c;
			} else {
				backupUnits(backSlashCnt - 1);
				return '\\';
			}
		}
//@fi
//@if(KEEP_LINE_COLUMN)
		UpdateLineColumn(c);
//@fi
		return c;
	}

	/**
	 * @see #getEndColumn
	 * @deprecated
	 */
	@Deprecated
	public int getColumn() {
//@if(KEEP_LINE_COLUMN)
		return this.bufcolumn[this.bufpos];
//@else
		return -1;
//@fi
	}

	/**
	 * @see #getEndLine
	 * @deprecated
	 */
	@Deprecated
	public int getLine() {
//@if(KEEP_LINE_COLUMN)
		return this.bufline[this.bufpos];
//@else
		return -1;
//@fi
	}

	/**
	 * Get end column.
	 */
	public int getEndColumn() {
//@if(KEEP_LINE_COLUMN)
		return this.bufcolumn[this.bufpos];
//@else
		return -1;
//@fi
	}

	/**
	 * Get end line.
	 */
	public int getEndLine() {
//@if(KEEP_LINE_COLUMN)
		return this.bufline[this.bufpos];
//@else
		return -1;
//@fi
	}

	/**
	 * @return column of token start
	 */
	public int getBeginColumn() {
//@if(KEEP_LINE_COLUMN)
		return this.bufcolumn[this.tokenBegin];
//@else
		return -1;
//@fi
	}

	/**
	 * @return line number of token start
	 */
	public int getBeginLine() {
//@if(KEEP_LINE_COLUMN)
		return this.bufline[this.tokenBegin];
//@else
		return -1;
//@fi
	}

	/**
	 * Retreat.
	 */
	/**
	 * Backs up by {@code amount} characters, as the lexer counts them: a character beyond the BMP
	 * is two units in the buffer (ADR-0029).
	 */
	public void backup(int amount) {
		backupUnits(unitsOf(amount));
	}

	private void backupUnits(int amount) {
		this.inBuf += amount;
		if ((this.bufpos -= amount) < 0) {
			this.bufpos += this.bufsize;
		}
	}

	/** The UTF-16 units the last {@code chars} characters read take in the buffer. */
	private int unitsOf(int chars) {
		int units = 0;
		int pos = this.bufpos;
		for (int c = 0; c < chars; c++) {
			char unit = this.buffer[pos];
			units++;
			if (--pos < 0) {
				pos += this.bufsize;
			}
			if (Character.isLowSurrogate(unit) && Character.isHighSurrogate(this.buffer[pos])) {
				units++;
				if (--pos < 0) {
					pos += this.bufsize;
				}
			}
		}
		return units;
	}

	/**
	 * Constructor.
	 */
	public JavaCharStream(Provider dstream) {
		this.inputStream = dstream;
//@if(KEEP_LINE_COLUMN)
		this.line = 1;
		this.column = 0;
//@fi
		this.available = this.bufsize = JavaCharStream.BUFFER_SIZE;
		this.buffer = new char[JavaCharStream.BUFFER_SIZE];
//@if(KEEP_LINE_COLUMN)
		this.bufline = new int[JavaCharStream.BUFFER_SIZE];
		this.bufcolumn = new int[JavaCharStream.BUFFER_SIZE];
//@fi
		this.nextCharBuf = new char[JavaCharStream.BUFFER_SIZE];
	}

	/**
	 * @return token image as String
	 */
	public String GetImage() {
		if (this.bufpos >= this.tokenBegin) {
			return new String(this.buffer, this.tokenBegin, (this.bufpos - this.tokenBegin) + 1);
		} else {
			return new String(this.buffer, this.tokenBegin, this.bufsize - this.tokenBegin)
					+ new String(this.buffer, 0, this.bufpos + 1);
		}
	}

	/**
	 * @return suffix
	 */
	public char[] GetSuffix(int chars) {
		int len = unitsOf(chars); // the lexer counts characters (ADR-0029)
		char[] ret = new char[len];

		if ((this.bufpos + 1) >= len) {
			System.arraycopy(this.buffer, (this.bufpos - len) + 1, ret, 0, len);
		} else {
			System.arraycopy(this.buffer, this.bufsize - (len - this.bufpos - 1), ret, 0,
					len - this.bufpos - 1);
			System.arraycopy(this.buffer, 0, ret, len - this.bufpos - 1, this.bufpos + 1);
		}

		return ret;
	}

	/**
	 * Set buffers back to null when finished.
	 */
	public void Done() {
		this.nextCharBuf = null;
		this.buffer = null;
//@if(KEEP_LINE_COLUMN)
		this.bufline = null;
		this.bufcolumn = null;
//@fi
	}
//@if(KEEP_LINE_COLUMN)

	/**
	 * Method to adjust line and column numbers for the start of a token.
	 */
	public void adjustBeginLineColumn(int newLine, int newCol) {
		int start = this.tokenBegin;
		int len;

		if (this.bufpos >= this.tokenBegin) {
			len = (this.bufpos - this.tokenBegin) + this.inBuf + 1;
		} else {
			len = (this.bufsize - this.tokenBegin) + this.bufpos + 1 + this.inBuf;
		}

		int i = 0, j = 0, k = 0;
		int nextColDiff = 0, columnDiff = 0;

		while ((i < len) && (this.bufline[j = start % this.bufsize] == this.bufline[k =
				++start % this.bufsize])) {
			this.bufline[j] = newLine;
			nextColDiff = (columnDiff + this.bufcolumn[k]) - this.bufcolumn[j];
			this.bufcolumn[j] = newCol + columnDiff;
			columnDiff = nextColDiff;
			i++;
		}

		if (i < len) {
			this.bufline[j] = newLine++;
			this.bufcolumn[j] = newCol + columnDiff;

			while (i++ < len) {
				if (this.bufline[j = start % this.bufsize] != this.bufline[++start
						% this.bufsize]) {
					this.bufline[j] = newLine++;
				} else {
					this.bufline[j] = newLine;
				}
			}
		}

		this.line = this.bufline[j];
		this.column = this.bufcolumn[j];
	}

	boolean getTrackLineColumn() {
		return this.trackLineColumn;
	}

	void setTrackLineColumn(boolean tlc) {
		this.trackLineColumn = tlc;
	}
//@fi
}
