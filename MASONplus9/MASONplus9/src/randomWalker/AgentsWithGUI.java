package randomWalker;

import java.awt.Color;

import observer.Observer;
import spaces.Spaces;
import sweep.SimStateSweep;
import sweep.GUIStateSweep;

/**
 * This is a template for creating basic user interfaces for you simulations in java.
 * The commented arrows indicated when changes should be made for your simulation.  This
 * template is for SparseGrid2D spaces.  For other spaces, change the relevant classes
 * to handle you space type.
 */


public class AgentsWithGUI extends GUIStateSweep{

	
	public static void main(String[] args) {
		initialize (/*RandomWalkerEnvironment.class*/Environment.class,Observer.class,AgentsWithGUI.class, 400, 400, Color.WHITE, Color.BLUE,true, Spaces.SPARSE);
	}

	
	public AgentsWithGUI(SimStateSweep state, int gridWidth, int gridHeight, Color backdrop, Color agentDefaultColor,
			boolean agentPortrayal) {
		super(state, gridWidth, gridHeight, backdrop, agentDefaultColor, agentPortrayal);
	}

}
