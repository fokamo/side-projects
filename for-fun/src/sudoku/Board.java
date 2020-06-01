package sudoku;

// for drawing
import java.awt.Graphics;

// for arrays that can change size
import java.util.ArrayList;

// for dealing with files
import java.io.File;
import java.io.IOException;

// for writing to files
import java.io.BufferedWriter;
import java.io.FileWriter;

// for reading from files
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * A Sudoku board which
 * <ul>
 * 	<li>maintains all its Tiles organized by rows and groups</li>
 * 	<li>can save itself</li>
 * 	<li>can load a state from a file</li>
 * 	<li>can set specific Tiles to numbers,</li>
 * 	<li>and dynamically calculate possibilities for all other Tiles</li>
 * 	<li>can undo back to start</li>
 * 	<li>and can draw itself</li>
 * </ul>
 * @author faith
 */
public class Board {
	/**
	 * all Tiles, organized by row
	 */
	private Tile[][] rows;
	/**
	 * all Tiles, organized by group
	 */
	private Tile[][] groups;
	
	/**
	 * a currently-active Tile
	 */
	private Tile active;
	
	/**
	 * all Tiles that have been changed, saved for undoing purposes
	 */
	private ArrayList<Tile> changed;
	/**
	 * a Writer to record moves made
	 */
	private BufferedWriter moveWriter;
	
	/**
	 * the # of rows (of groups, and within groups)
	 */
	public static final int ROWS = 2;
	/**
	 * the # of columns (of groups, and within groups)
	 */
	public static final int COLS = 3;
	/**
	 * the side length of the overall grid
	 */
	public static final int SIZE = ROWS * COLS;
	
	/**
	 * the File with save data
	 */
	public static final File saveFile = new File("src/sudoku/saved-states/save.txt");
	/**
	 * the File with move data
	 */
	public static final File moveFile = new File("src/sudoku/saved-states/moves.txt");
	
	/**
	 * Initializes a default Board with no numbers
	 */
	public Board() {
		// set up the matrices of Tiles
		rows = new Tile[SIZE][SIZE];
		groups = new Tile[SIZE][SIZE];
		
		// loop over all cells of the matrix that need Tiles
		for (int row = 0; row < SIZE; ++row) for (int col = 0; col < SIZE; ++col) {
			// stick at Tile into rows
			rows[row][col] = new Tile(row, col);
			// copy the Tile into groups
			groups[getGroup(row, col)]
					[(row % ROWS) * COLS + col % COLS] = rows[row][col];
		}
		
		// initialize the list of changed
		changed = new ArrayList<Tile>();
		try {moveWriter = new BufferedWriter(new FileWriter(moveFile));}
		catch (IOException e) {
			System.out.println("Cannot save moves");
			moveWriter = null;
		}
	}

	/**
	 * Calculates the group number (0 <= # < SIZE)
	 * @param row the row of the Tile
	 * @param col the column of the Tile
	 * @return the group # of this Tile
	 */
	private int getGroup(int row, int col) {
		return (row / COLS) * ROWS + col / COLS;
	}
	
	/**
	 * Draws the board
	 * @param window the window to draw on
	 */
	public void draw(Graphics window) {
		// draw each Tile
		for (Tile[] row : rows) for (Tile tile : row)
			tile.draw(window);
	}
	
	/**
	 * @param num the single number to set the active Tile to
	 * @return whether the setting was successful
	 */
	public boolean setNum(int num) {
		// use general setNum on active
		return setNum(active, num);
	}

	/**
	 * @param tile the Tile to check
	 * @param num the number to check for
	 * @return whether this Tile can "see" - i.e. is affected by - this number
	 */
	private boolean canSee(Tile tile, int num) {
		// save row and column
		int row = tile.getRow();
		int col = tile.getCol();
		
		// check in this column
		for (int ro = 0; ro < SIZE; ++ro)
			// if this number is here, can see
			if (rows[ro][col].getNum() == num) return true;
		// similar loop for row
		for (Tile other : rows[row])
			if (other.getNum() == num) return true;
		// similar loop for group
		for (Tile other : groups[getGroup(row, col)])
			if (other.getNum() == num) return true;
		
		// or if not seen anywhere, cannot see
		return false;
	}
	
	/**
	 * @param tile the Tile to set the number of
	 * @param num the single number to set the active Tile to
	 * @return whether the setting was successful
	 */
	private boolean setNum(Tile tile, int num) {
		// unsuccessful if tile is null or cannot be set
		if (tile == null || !tile.couldBe(num)) return false;
		
		// set the number, and record the move
		tile.setNum(num);
		writeMove("R" + (tile.getRow() + 1) + "C" + (tile.getCol() + 1) + ":" + num);
		// update the list of moves
		changed.add(active);
		// recalculate possibilities
		removePos(tile, num);
		return true;
	}
	
	/**
	 * @param center the tile to remove possibilities from outwards
	 * @param pos the possible number to remove
	 */
	private void removePos(Tile center, int pos) {
		// save row and column
		int row = center.getRow();
		int col = center.getCol();
		
		// loop though column
		for (int ro = 0; ro < SIZE; ++ro)
			// remove possibility
			rows[ro][col].removePos(pos);
		// similar loop for row
		for (Tile other : rows[row])
			other.removePos(pos);
		// similar loop for group
		for (Tile other : groups[getGroup(row, col)])
			other.removePos(pos);
	}
	
	/**
	 * @param center the tile to add possibilities from outwards
	 * @param pos the possible number to remove, if applicable
	 */
	private void addPos(Tile center, int pos) {
		// save row and column
		int row = center.getRow();
		int col = center.getCol();
		
		// loop through column
		for (int ro = 0; ro < SIZE; ++ro)
			// if this Tile has no single number and can't see pos any other way
			if (!rows[ro][col].hasNum() && !canSee(rows[ro][col], pos))
				// remove the possibility
				rows[ro][col].addPos(pos);
		
		// similar loop for row
		for (Tile other : rows[row])
			if (!other.hasNum() && !canSee(other, pos))
				other.addPos(pos);
		
		// similar loop for group
		for (Tile other : groups[getGroup(row, col)])
			if (!other.hasNum() && !canSee(other, pos))
				other.addPos(pos);
	}
	
	/**
	 * Undo the last move
	 */
	public void undo() {
		// if there are moves to undo
		if (!changed.isEmpty()) {
			// grab the last Tile changed
			Tile last = changed.remove(changed.size() - 1);
			// grab its number
			int old = last.getNum();
			
			// reset last Tile
			last.reset();
			// add in possibilities from removing this number
			addPos(last, old);
			// calculate possibilities for this Tile
			for (int i = 1; i <= SIZE; ++i)
				if (canSee(last, i)) last.removePos(i);
			// record the undo
			writeMove("undo");
		}
	}
	
	/**
	 * Write a move to the moves file
	 * @param str the move to write
	 */
	private void writeMove(String str) {
		// if everything exists
		if (str != null && moveWriter != null) {
			try {
				// try to write
				moveWriter.write(str);
				moveWriter.newLine();
			}
			// but don't worry at fail
			catch (IOException e) {}
		}
	}
	
	/**
	 * Sets a Tile as the active Tile
	 * @param row the row of the Tile to set
	 * @param col the column of this Tile to set
	 */
	public void setActive(int row, int col) {
		// if this are valid coordinates
		if (row >= 0 && row < SIZE && col >= 0 && col < SIZE) {
			// set this as active
			active = rows[row][col];
			// clear all highlights, then highlight only...
			for (Tile[] ro : rows) for (Tile tile : ro) tile.setHighlight(false);
			
			// ... the row,
			for (Tile tile : rows[row]) tile.setHighlight(true);
			// ... the column,
			for (int ro = 0; ro < SIZE; ++ro) rows[ro][col].setHighlight(true);
			// ... and the group
			for (Tile tile : groups[getGroup(row, col)]) tile.setHighlight(true);
		}
	}
	
	/**
	 * Saves the current state to a file
	 */
	public void save() {
		// point a writer at the save-file
		try(BufferedWriter writer = 
					new BufferedWriter(new FileWriter(saveFile))) {
			// loop over all rows
			for (Tile[] row : rows) {
				// loop over all Tiles, recording their numbers
				for (Tile tile : row) writer.write(tile.getNum() + " ");
				// newline after each row
				writer.newLine();
			}
		}
		// if something went wrong
		catch (Exception e) {
			// note and print
			System.out.println("Failed to save");
			e.printStackTrace();
		}
		
		// try to close the moveWriter
		try {if(moveWriter != null) moveWriter.close();}
		catch (Exception e) {}
	}
	
	/**
	 * Loads a state from the save-file
	 */
	public void load() {
		// point a Scanner at the save-file
		try(Scanner reader = new Scanner(saveFile)) {
			// loop over all numbers to read
			for (Tile[] row : rows) for (Tile tile : row) {
				// grab the number for this spot
				int val = reader.nextInt();
				// if it is a valid number, set for this Tile
				if (val != Tile.NO_NUM) setNum(tile, val);
			}
		}
		// if the Scanner ran out of data
		catch (NoSuchElementException e) {
			// note and reset Board
			System.out.println("Saved level has lost data; reverting back to default");
			for (Tile[] row : rows) for (Tile tile : row)
				tile.reset();
		}
		// if something else went wrong
		catch (Exception e) {
			// not and reset Board
			System.out.println("Could not load the saved level; reverting back to default");
			e.printStackTrace();
			for (Tile[] row : rows) for (Tile tile : row)
				tile.reset();
		}
		finally {
			// clear all "moves"
			changed.clear();
			// reset moves file
			try {moveWriter = new BufferedWriter(new FileWriter(moveFile));}
			catch (IOException e) {}
		}
	}
}