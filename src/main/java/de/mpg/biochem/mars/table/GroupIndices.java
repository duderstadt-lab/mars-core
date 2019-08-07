package de.mpg.biochem.mars.table;

public class GroupIndices {
	private int start, end;
	GroupIndices(int start, int end) {
		this.start = start;
		this.end = end;
	}
	public int getStart() {
		return start;
	}
	public int getEnd() {
		return end;
	}
}
