package viewers;

import java.util.ArrayList;

import graphics.Drawing;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JPanel;

import base.Level;
import base.Region;

import java.awt.Font;
import java.awt.Point;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class LevelViewer extends JPanel  {

	final static int UNDO_LENGTH = 50;

	// Values for editMode
	final static int EDIT_LEVEL=1;
	final static int EDIT_WARPS=2;
	
	int editMode = EDIT_LEVEL;
	boolean viewObjects=false;
	boolean viewSectors=false;
	boolean viewRegions=false;
	int selectedSector=0;
	
	MainFrame mainFrame;
	TileSetViewer tileSetViewer;
	
	public Region selectedRegion=null;
	public Level level;
	
	public Point cursorPos;

	// If false, objects are added instead
	boolean tileMode=true;

	boolean dragging=false; // For holding the left mouse button down
	boolean rectangleMode=false;
	Point rectangleStart = new Point();

	int draggingObject=0; // For moving an object

	boolean ctrlPressed=false;

	// Undo buffers
	ArrayList<byte[]> tileBuffer;
	ArrayList<byte[]> objectBuffer;

	int undoBufferPos;
	
	public LevelViewer(MainFrame f)
	{
		mainFrame = f;
		tileSetViewer = f.tileSetViewer;
		setPreferredSize(new Dimension(0xa0*16, 0x30*16));
		addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e)
			{
				if (level != null)
				{
					requestFocus();

					Point newCursorPos = new Point(e.getX()/16, e.getY()/16);

					if (newCursorPos.x >= 0xa0 || newCursorPos.x < 0 ||
							newCursorPos.y >= 0x30 || newCursorPos.y < 0)
						return;

					cursorPos = newCursorPos;

					boolean changedRegion = false;
					if ((editMode == EDIT_LEVEL || editMode == EDIT_WARPS) && level.getRegion(cursorPos.x, cursorPos.y) != selectedRegion)
					{
						Region newRegion = level.getRegion(cursorPos.x, cursorPos.y);
						if (newRegion != null) {
							setSelectedRegion(newRegion);
						}
						changedRegion = true;
					}
					int newSector = cursorPos.y/16*0xa + cursorPos.x/16;
					if (newSector != selectedSector) {
						selectedSector = newSector;
						refreshRegionFields();
					}
					
					if (e.getButton() == MouseEvent.BUTTON1)
					{
						if (viewObjects && (mainFrame.objectSetViewer.selectedObject != 0)
							&& level.getObject(cursorPos.x, cursorPos.y) != 0)
						{
							draggingObject = level.getObject(cursorPos.x, cursorPos.y);
							level.setObject(cursorPos.x, cursorPos.y, 0);
						}
						else if (editMode == EDIT_LEVEL && !changedRegion)
						{
							if (tileMode)
							{
								if (ctrlPressed) {
									rectangleMode = true;
									rectangleStart.x = cursorPos.x;
									rectangleStart.y = cursorPos.y;
								}
								else {
									dragging = true;
									setTile(cursorPos.x, cursorPos.y, tileSetViewer.selectedTile);
								}
							}
							else
							{
								dragging = true;
								level.setObject(cursorPos.x, cursorPos.y, mainFrame.objectSetViewer.selectedObject);
							}
						}

					}
					else if (e.getButton() == MouseEvent.BUTTON3)
					{
						if (rectangleMode) {
							rectangleMode = false;
						}
						else {
							// Copy tile or object into currently selected tile
							int obj = level.getObject(cursorPos.x, cursorPos.y);
							if (viewObjects && obj != 0)
								mainFrame.objectSetViewer.setSelectedObject(obj);
							else
								tileSetViewer.setSelectedTile(level.getTile(cursorPos.x, cursorPos.y));
						}
					}

					repaint();
				}
			}
			public void mouseReleased(MouseEvent e)
			{
				if (level != null)
				{
					if (rectangleMode) {
						rectangleMode = false;

						if (cursorPos.x != -1) {
							Point rectangleEnd = new Point(cursorPos);
							if (rectangleStart.x > rectangleEnd.x) {
								int tmp = rectangleEnd.x;
								rectangleEnd.x = rectangleStart.x;
								rectangleStart.x = tmp;
							}
							if (rectangleStart.y > rectangleEnd.y) {
								int tmp = rectangleEnd.y;
								rectangleEnd.y = rectangleStart.y;
								rectangleStart.y = tmp;
							}

							for (int x=rectangleStart.x; x<=rectangleEnd.x; x++) {
								for (int y=rectangleStart.y; y<=rectangleEnd.y; y++) {
									setTile(x, y, tileSetViewer.selectedTile);
								}
							}

							saveBuffer();

							repaint();
						}
					}
					else if (dragging) {
						dragging = false;
						saveBuffer();
					}
					else if (draggingObject != 0) {
						level.setObject(cursorPos.x, cursorPos.y, draggingObject);
						draggingObject = 0;

						saveBuffer();
					}
				}
			}
			public void mouseExited(MouseEvent e) {
				cursorPos.x = -1;
				repaint();
			}
		});
		addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseMoved(MouseEvent e)
			{
				Point p = e.getPoint();
				Point newPos = new Point(p.x/16, p.y/16);

				if (newPos.x >= 0xa0 || newPos.x < 0 ||
						newPos.y >= 0x30 || newPos.y < 0)
					return;

				requestFocus();

				if (cursorPos.x != newPos.x || cursorPos.y != newPos.y)
					repaint();

				cursorPos = newPos;
			}
			public void mouseDragged(MouseEvent e)
			{
				Point p = e.getPoint();
				cursorPos = new Point(p.x/16, p.y/16);
				if (dragging)
				{
					if (tileMode)
						setTile(cursorPos.x, cursorPos.y, tileSetViewer.selectedTile);
					else
						level.setObject(cursorPos.x, cursorPos.y, mainFrame.objectSetViewer.selectedObject);
				}
				repaint();
			}
		});
		addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				switch (e.getKeyCode()) {
					case KeyEvent.VK_CONTROL:
						ctrlPressed = true;
						break;
				}
			}
			public void keyReleased(KeyEvent e) {
				switch (e.getKeyCode()) {
					case KeyEvent.VK_CONTROL:
						ctrlPressed = false;
						break;
					case KeyEvent.VK_Z:
						if (ctrlPressed)
							undo();
						break;
					case KeyEvent.VK_Y:
						if (ctrlPressed)
							redo();
						break;
				}
			}
		});
		
		
		level = null;
		
		cursorPos = new Point(0, 0);

		setFocusable(true);
		requestFocus();
	}

	public void undo() {
		if (undoBufferPos > 0) {
			undoBufferPos--;
			byte[] tiles = tileBuffer.get(undoBufferPos);
			byte[] objects = objectBuffer.get(undoBufferPos);

			level.getTileDataRecord().setData(tiles);
			level.getObjectDataRecord().setData(objects);

			level.generateImage();
			repaint();
		}
	}

	public void redo() {
		if (undoBufferPos+1 < tileBuffer.size()) {
			undoBufferPos++;

			byte[] tiles = tileBuffer.get(undoBufferPos);
			byte[] objects = objectBuffer.get(undoBufferPos);

			level.getTileDataRecord().setData(tiles);
			level.getObjectDataRecord().setData(objects);

			level.generateImage();
			repaint();
		}
	}

	// Add state to undo buffer
	public void saveBuffer() {
		// Remove anything from the "redo" end of the buffer
		while (undoBufferPos+1 < tileBuffer.size()) {
			tileBuffer.remove(undoBufferPos+1);
			objectBuffer.remove(undoBufferPos+1);
		}

		if (undoBufferPos >= UNDO_LENGTH) {
			tileBuffer.remove(0);
			objectBuffer.remove(0);
			undoBufferPos--;
		}

		byte[] tiles = level.getTileDataRecord().toArray();
		byte[] objects = level.getObjectDataRecord().toArray();

		tileBuffer.add(tiles);
		objectBuffer.add(objects);
		undoBufferPos++;
	}


	public void setSelectedRegion(Region r) {
		selectedRegion = r;
		tileSetViewer.setTileSet(selectedRegion.getTileSet());
		refreshRegionFields();

		repaint();
	}

	public void setTile(int x, int y, int tile) {
		level.setTile(x, y, tileSetViewer.selectedTile);
	}
	
	public void setRegionSector(int sector)
	{
		if (selectedRegion == null)
			return;
		int x = sector%0xa;
		int y = sector/0xa;
		if (y < 3 && (level.getRegion(x*16, y*16) == null || level.getRegion(x*16, y*16) == selectedRegion))
		{
			selectedRegion.firstHSector = x;
			selectedRegion.firstVSector = y;
			selectedRegion.lastHSector = x+1;
			selectedRegion.lastVSector = y+1;
		}
	}
	public void setRegionWidth(int width)
	{
		if (selectedRegion == null)
			return;
		boolean okay = true;
		if (width <= 0 || selectedRegion.firstHSector+width > 0xa)
			okay = false;
		
		for (int x=selectedRegion.firstHSector+1; x<selectedRegion.firstHSector+width && okay==true; x++)
		{
			for (int y=selectedRegion.firstVSector; y<selectedRegion.lastVSector; y++)
			{
				if (level.getRegion(x*16, y*16) != selectedRegion && level.getRegion(x*16, y*16) != null)
				{
					okay = false;
					break;
				}
			}
		}
		
		if (okay)
		{
			selectedRegion.lastHSector = selectedRegion.firstHSector+width;
		}
	}
	public void setRegionHeight(int height)
	{
		if (selectedRegion == null)
			return;
		boolean okay = true;
		if (height <= 0 || selectedRegion.firstVSector+height > 3)
			okay = false;
		
		for (int y=selectedRegion.firstVSector+1; y<selectedRegion.firstVSector+height && okay==true; y++)
		{
			for (int x=selectedRegion.firstHSector; x<selectedRegion.lastHSector; x++)
			{
				if (level.getRegion(x*16, y*16) != null && level.getRegion(x*16, y*16) != selectedRegion)
				{
					okay = false;
					break;
				}
			}
		}
		
		if (okay)
		{
			selectedRegion.lastVSector = selectedRegion.firstVSector+height;
		}
	}
	void refreshRegionFields()
	{
		mainFrame.disableRegionListener = true;

		mainFrame.regionSectorField.setText(Integer.toHexString(selectedRegion.firstVSector*0xa+selectedRegion.firstHSector).toUpperCase());
		mainFrame.regionWidthField.setText(Integer.toHexString(selectedRegion.lastHSector-selectedRegion.firstHSector).toUpperCase());
		mainFrame.regionHeightField.setText(Integer.toHexString(selectedRegion.lastVSector-selectedRegion.firstVSector).toUpperCase());
		mainFrame.regionScrollModeField.setSelected(selectedRegion.scrollMode&0xf);
		mainFrame.regionObjectSetField.setText(Integer.toHexString(selectedRegion.objectSetId).toUpperCase());
		mainFrame.regionTileSetField.setText(Integer.toHexString(selectedRegion.tileSetId).toUpperCase());
		mainFrame.regionByte5Field.setText(Integer.toHexString(selectedRegion.b5).toUpperCase());
		mainFrame.regionByte6Field.setText(Integer.toHexString(selectedRegion.b6).toUpperCase());

		mainFrame.cropLeft.setSelected(selectedRegion.getCropLeft());
		mainFrame.cropRight.setSelected(selectedRegion.getCropRight());
		mainFrame.cropTop.setSelected(selectedRegion.getCropTop());
		mainFrame.cropBottom.setSelected(selectedRegion.getCropBottom());

		mainFrame.sectorEditor.setBorder(BorderFactory.createTitledBorder("Sector " + Integer.toHexString(selectedSector).toUpperCase()));
		mainFrame.sectorDestinationField.setText(Integer.toHexString(level.getRegionDataRecord().getSectorDestination(selectedSector)).toUpperCase());
		mainFrame.disableRegionListener = false;
	}
	public void setSectorDestination(int dest)
	{
		level.getRegionDataRecord().setSectorDestination(selectedSector, dest);
	}
	public void setEditMode(int mode)
	{
		editMode = mode;
		
		if (editMode == EDIT_LEVEL)
		{
		//	viewSectors = false;
		//	viewRegions = false;
		}
		else if (editMode == EDIT_WARPS)
		{
		//	viewSectors = true;
		//	viewRegions = true;
		}
		repaint();
	}
	public void setLevel(Level l)
	{
		// Those images really suck up ram, so delete them when they're not needed.
		if (level != null)
			level.freeImage();

		level = l;
		if (level == null)
			setPreferredSize(new Dimension(0, 0));
		else
		{
			setPreferredSize(new Dimension(0xa00, 0x300));
			setSelectedRegion(level.getRegionDataRecord().getRegion(0));
			selectedSector = 0;
			tileSetViewer.setTileSet(selectedRegion.getTileSet());
			mainFrame.musicField.setSelected(level.getMusicId());
			mainFrame.levelField.setSelected(level.getId());

			tileBuffer = new ArrayList<byte[]>();
			objectBuffer = new ArrayList<byte[]>();
			undoBufferPos = -1;
			saveBuffer();
		}
		repaint();
	}
	
	@Override
	protected void paintComponent(Graphics g)
	{
		if (level == null)
		{
			g.setColor(getBackground());
			g.fillRect(0, 0, size().width, size().height);
		}
		else
		{
			// All tiles on the level
			g.drawImage(level.getImage(), 0, 0, null);
			
			
			if (viewSectors)
			{
				// Draw sector divisions
				for (int c=0; c<0xa; c++)
				{
					for (int r=0; r<3; r++)
					{
						int sectorNum = r*0xa + c;
						if (sectorNum == selectedSector)
							g.setColor(new Color(0, 255, 0));
						else
							g.setColor(new Color(0, 150, 0));
						Drawing.drawSquare(g, 2, c*16*16, r*16*16, 16*16);
						g.fillRect(c*16*16, r*16*16, 20, 15);
						
						g.setColor(Color.black);
						g.drawString(Integer.toHexString(sectorNum).toUpperCase(), c*16*16+1, r*16*16+13);
					}
				}
			}
			if (viewRegions)
			{
				// Draw region divisions
				for (int i=0; i<level.getRegionDataRecord().getNumRegions(); i++)
				{
					Region r = level.getRegionDataRecord().getRegion(i);
					
					if (r == selectedRegion)
						g.setColor(new Color(255, 0, 0));
					else
						g.setColor(new Color(100, 0, 0));
					Drawing.drawRect(g, 2, r.firstHSector*16*16, r.firstVSector*16*16, (r.lastHSector-r.firstHSector)*16*16, (r.lastVSector-r.firstVSector)*16*16);
				}
			}
			if (viewObjects)
			{
				// Draw objects
				for (int x=0; x<0xa0; x++)
				{
					for (int y=0; y<0x30; y++)
					{
						int obj = level.getObject(x, y);
						if (obj != 0)
						{
							drawObject(g, x, y, obj);
						}
					}
				}
				// Draw object being dragged
				if (draggingObject != 0)
					drawObject(g, cursorPos.x, cursorPos.y, draggingObject);
			}
			
			
			// Draw cursor
			g.setColor(Color.red);
			if (rectangleMode) {
				int startX = (rectangleStart.x < cursorPos.x ? rectangleStart.x : cursorPos.x);
				int startY = (rectangleStart.y < cursorPos.y ? rectangleStart.y : cursorPos.y);
				int endX = (rectangleStart.x >= cursorPos.x ? rectangleStart.x : cursorPos.x);
				int endY = (rectangleStart.y >= cursorPos.y ? rectangleStart.y : cursorPos.y);
				Drawing.drawRect(g, 2, startX*16, startY*16,
						(endX-startX+1)*16,
						(endY-startY+1)*16);
			}
			else
				Drawing.drawSquare(g, 2, cursorPos.x*16, cursorPos.y*16, 16);
		}
	}
	
	static void drawObject(Graphics g, int x, int y, int obj)
	{
		g.setColor(Color.blue);
		g.fillRect(x*16, y*16, 16, 16);
		g.setFont(new Font("monospaced", Font.BOLD, 16));
		g.setColor(Color.black);
		if (obj == 0xf)
			g.drawString("W", x*16+3, y*16+13);
		else if (obj == 0)
		{
			g.drawLine(x*16, y*16, x*16+15, y*16+15);
			g.drawLine(x*16+15, y*16, x*16, y*16+15);
		}
		else
			g.drawString(Integer.toHexString(obj).toUpperCase(), x*16+3, y*16+13);
	}
}
