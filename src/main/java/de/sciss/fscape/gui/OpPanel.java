/*
 *  OpPanel.java
 *  FScape
 *
 *  Copyright (c) 2001-2012 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.fscape.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.SyncFailedException;
import java.rmi.NotBoundException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import de.sciss.app.AbstractApplication;
import de.sciss.fscape.op.Operator;
import de.sciss.fscape.op.SlotAlreadyConnectedException;
import de.sciss.fscape.spect.SpectStreamSlot;
import de.sciss.fscape.util.Slots;
import de.sciss.gui.GUIUtil;

/**
 *  GUI panel hosting the spectral
 *	operators and allowing the user
 *	to drag them around and wire them together.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.72, 04-Jan-09
 */
public class OpPanel
extends JPanel
implements	ClipboardOwner, ActionListener, MouseListener, MouseMotionListener
{
// -------- public Variablen --------

	public static final String OBJ_NAME = "OpPanel";

// -------- private Variablen --------

	private SpectPatchDlg win;

	private	DragContext	dragContext	= null;

	// for optimal layout
	protected	int	preferredWidth	= 250;
	protected	int	preferredHeight	= 150;

	// Popup-Men�s, die �ber dem Panel bzw. einem Op aufspringen	
	private PopupStrip popPanel;
	private PopupStrip popIcon;
	private PopupStrip popCon;
	private PopupStrip popNew;
	
	private Component popSource;
	private int popX, popY;
	
		// Panel
		private static final String MI_NEW		= "New";
		private static final String MI_PASTE	= "Paste";

		// Icon
		private static final String MI_EDIT			= "Edit...";
		private static final String MI_RENAME		= "Rename...";
		private static final String MI_MAKEALIAS	= "Make alias";
		private static final String MI_CUT			= "Cut";
		private static final String MI_COPY			= "Copy";
		private static final String MI_DUPL			= "Duplicate";
		private static final String MI_REMOVE		= "Remove";

		// Connector
		private static final String MI_ORIGIN		= "Origin";
		private static final String MI_TARGET		= "Target";
		private static final String MI_HIDE			= "Hide";

		private static final String mPanel[][]	= { null, { null }, { MI_PASTE }};
		private static final String mIcon[][]	= {{ MI_EDIT }, { MI_RENAME }, { MI_MAKEALIAS },
												   { null }, { MI_CUT }, { MI_COPY }, { MI_DUPL },
												   { MI_REMOVE }};
		private static final String	mCon[][]	= { null, null, { null }, { MI_HIDE }};
		private static String mNew[][];

	private Vector		vIcon;		// alle OpIcons
	private Hashtable	hCon;		// keys = (writer) slots; values = OpConnector
	
	protected String[]  optionsAliasDel		= { "Cancel", "Remove", "Transform" };
	protected String[]  optionsAliasEdit	= { "Cancel", "Original", "Transform" };

// -------- public Methoden --------
	// public Operator addOperator( Operator op );
	// public void removeOperator( Operator op );
	// public void renameOperator( Operator op, String name );
	// public void moveOperator( Operator op, int x, int y );
	// public void updateOperator( Operator op );
	// public OpIcon getOpIconAt( int x, int y );

	public OpPanel( SpectPatchDlg win )
	{
		super();
		
		Map			ops;
		Iterator	opNames;
		
		vIcon	= new Vector();
		hCon 	= new Hashtable();

		setLayout( null );
		
		addMouseListener( this );
		addMouseMotionListener( this );
		this.win = win;

		// Popup-Men�s
		ops = Operator.getOperators();
		opNames = ops.keySet().iterator();
		
		mPanel[ 0 ]	= new String[ 1 + ops.size() ];
		mNew		= new String[ ops.size() ][ 1 ];
		mPanel[ 0 ][ 0 ] = MI_NEW;
		for( int i = 1; opNames.hasNext(); i++ ) {
			mPanel[ 0 ][ i ] = (String) opNames.next();
			mNew[ i-1 ][ 0 ] = mPanel[ 0 ][ i ];
		}
		popPanel	= new PopupStrip( mPanel, this );
		popIcon		= new PopupStrip( mIcon, this );
		popNew		= new PopupStrip( mNew, this );
		
		setOpaque( false );

//	ImageIcon icon = new ImageIcon( "images/tools.gif", "test" );

	}

	/**
	 *	@return returns OBJ_NAME so you can identify it ('==' Operator)
	 */
	public String toString()
	{
		return OBJ_NAME;
	}

	public void clear()
	{
		vIcon	= new Vector();
		hCon 	= new Hashtable();
		removeAll();
	}

	/**
	 *	Operator bzw. sein Icon auf das Panel legen
	 *	(invokes Document.addOperator)
	 */
	public Operator addOperator( Operator op, int x, int y )
	{
		OpIcon icon = (OpIcon) op.getIcon();

		synchronized( vIcon ) {
			vIcon.addElement( icon );
		}

		icon.addMouseListener( this );
		icon.getLabel().addMouseListener( this );
		icon.addMouseMotionListener( this );
		icon.setLocation( x, y );
		icon.addTo( this );
		validateSize( icon );

		win.getDoc().addOperator( op );	// setModified() wird aufgerufen
		return op;
	}

	/**
	 *	Operator bzw. sein Icon vom Panel entfernen
	 *	(invokes Document.addOperator)
	 */
	public void removeOperator( Operator op )
	{
		OpIcon			icon	= (OpIcon) op.getIcon();
		Enumeration		slots	= op.getSlots( Slots.SLOTS_LINKED ).elements();
		SpectStreamSlot	slot1;
		SpectStreamSlot	slot2;
		OpConnector		con;

		synchronized( vIcon ) {
			vIcon.removeElement( icon );
		}

		while( slots.hasMoreElements() ) {
			slot1	= (SpectStreamSlot) slots.nextElement();
			slot2	= slot1.getLinked();

			con = getConnector( slot1 );
			synchronized( hCon ) {
				hCon.remove( slot1 );
				hCon.remove( slot2 );
			}
			con.drawArrow( false );
			remove( con );

			try {
				slot1.divorce();
			}
			catch( NotBoundException e ) {}		// was solls...
		}
		
		icon.removeFrom( this );
		win.getDoc().removeOperator( op );	// setModified() wird aufgerufen
	}

	/**
	 *	Operator umbenennen
	 */
	public void renameOperator( Operator op, String name )
	{
		OpIcon icon = (OpIcon) op.getIcon();
		icon.setName( name );
//		win.getDoc().setModified( true );
	}
 
	/**
	 *	Operator verschieben
	 */
	public void moveOperator( Operator op, int x, int y )
	{
		OpIcon			icon	= (OpIcon) op.getIcon();
		Enumeration		slots	= op.getSlots( Slots.SLOTS_LINKED ).elements();
		SpectStreamSlot	slot;
		OpConnector		con;

		icon.setLocation( x, y );				// Icon verschieben

		while( slots.hasMoreElements() ) {
			slot	= (SpectStreamSlot) slots.nextElement();

			con = getConnector( slot );
			con.drawArrow( false );
			con.adjustLocation();
			con.drawArrow( true );
			validateSize( con );
		}

		validateSize( icon );

//		win.getDoc().setModified( true );
	}

	/**
	 *	OpConnector verschieben
	 */
	public void moveConnector( OpConnector con, int x, int y )
	{
		con.drawArrow( false );
		con.setLocation( x, y );
		con.drawArrow( true );

		validateSize( con );

//		win.getDoc().setModified( true );
	}

	/**
	 *	Connector zu einem Slot ermitteln
	 */
	public OpConnector getConnector( SpectStreamSlot slot )
	{
		OpConnector con;
	
		synchronized( hCon ) {
			con		= (OpConnector) hCon.get( slot );
			if( con == null ) {
				con	= (OpConnector) hCon.get( slot.getLinked() );
			}
		}
		return con;
	}		

	/**
	 *	Operatoren verknuepfen
	 *
	 *	@param	slot1	Slot des ersten Operators
	 *	@param	slot2	Slot des zweiten Operators
	 */
	public void linkOperators( SpectStreamSlot slot1, SpectStreamSlot slot2 )
	throws NoSuchElementException,
		   SyncFailedException,
		   SlotAlreadyConnectedException
	{
		SpectStreamSlot	foo;
		OpConnector		con;
		
		// make sure slot1 is the writer
		if( (slot1.getFlags() & Slots.SLOTS_TYPEMASK) != Slots.SLOTS_WRITER ) {
			foo		= slot1;
			slot1	= slot2;
			slot2	= foo;
		}

		slot1.linkTo( slot2 );

		con = new OpConnector( slot1 );
		con.addMouseListener( this );
		con.addMouseMotionListener( this );

		synchronized( hCon ) {
			hCon.put( slot1, con );
		}
		add( con );
		validateSize( con );
		con.drawArrow( true );

//		win.getDoc().setModified( true );
	}

	/**
	 *	Operatoren trennen
	 *
	 *	@param	slot1	Slot des ersten Operators
	 */
	public void divorceOperators( SpectStreamSlot slot1 )
	throws NotBoundException
	{
		SpectStreamSlot	slot2	= slot1.getLinked();
		OpConnector		con;

		slot1.divorce();

		con = getConnector( slot1 );
		synchronized( hCon ) {
			hCon.remove( slot1 );
			hCon.remove( slot2 );
		}
		con.drawArrow( false );
		remove( con );

//		win.getDoc().setModified( true );
	}

	/**
	 *	Veraenderungen am Operator mitteilen
	 */
	public void updateOperator( Operator op )
	{
//		win.getDoc().setModified( true );
	}

	/**
	 *	Liefert das OpIcon, das an einem Punkt des Containers liegt
	 *
	 *	@return null, wenn kein OpIcon dort
	 */
	public OpIcon getOpIconAt( int x, int y )
	{
		Component c = getComponentAt( x, y );
		if( (c != null) && (c != this) ) {	// ignore myself
			if( c.toString() == OpIcon.OBJ_NAME ) {
				return (OpIcon) c;
			}
			if( c.toString() == OpIconLabel.OBJ_NAME ) {
				return ((OpIconLabel) c).getOpIcon();
			}
		}
		return null;
	}

	/**
	 *	Liefert das OpIcon, das sich mit der Referenz ueberschneiden wuerde
	 *
	 *	@param	src		OpIcon, das als Referenz dient und "uebersehen" werden soll
	 *	@param	hGap	horizontaler Mindestabstand; ggf. 0
	 *	@param	vGap	vertikaler Mindestabstand; ggf. 0
	 *	@return	null, wenn kein OpIcon dort
	 */
	public OpIcon getOpIconAround( int x, int y, OpIcon src, int hGap, int vGap ) 
	{
		OpIconLabel	srcLab	= src.getLabel();

		Rectangle	icon	= src.getBounds();
		Rectangle	lab		= srcLab.getBounds();
		Rectangle	union	= src.getUnionBounds();
		int			dx		= x - (icon.x + (icon.width  >> 1));
		int			dy		= y - (icon.y + (icon.height >> 1));

		icon.translate(  dx - hGap, dy - vGap );
		lab.translate(   dx - hGap, dy - vGap);
		union.translate( dx - hGap, dy - vGap );
		
		icon.width		+= hGap << 1;
		lab.width		+= hGap << 1;
		union.width		+= hGap << 1;
		icon.height		+= vGap << 1;
		lab.height		+= vGap << 1;
		union.height	+= vGap << 1;

		Component	cs[]	= getComponents();
		Component	c;
		Rectangle	dest;
		
		for( int i = 0; i < cs.length; i++ ) {
			c = cs[ i ];
			if( (c == src) || (c == srcLab) ) continue;
			dest = c.getBounds();
			if( (dest.x >= union.x + union.width)  ||
				(dest.y >= union.y + union.height) ||
				(dest.x <= union.x - dest.width)   ||
				(dest.y <= union.y - dest.height) ) continue;	// grobe Abschaetzung

			if( ((dest.x >= icon.x + icon.width)   ||
				 (dest.y >= icon.y + icon.height)  ||
				 (dest.x <= icon.x - dest.width)   ||
				 (dest.y <= icon.y - dest.height)) &&
				((dest.x >= lab.x + lab.width)     ||
				 (dest.y >= lab.y + lab.height)    ||
				 (dest.x <= lab.x - dest.width)    ||
				 (dest.y <= lab.y - dest.height)) )	continue;	// genaue Berechnung

			if( c.toString() == OpIcon.OBJ_NAME ) {
				return (OpIcon) c;
			}
			if( c.toString() == OpIconLabel.OBJ_NAME ) {
				return ((OpIconLabel) c).getOpIcon();
			}
		}
		return null;
	}

//	public void update( Graphics g )
//	{
//		Dimension d = getSize();
//		g.clearRect( 0, 0, d.width, d.height );
//		paint( g );
//	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
	
		Enumeration cons;
		OpConnector	con;
	
		synchronized( hCon ) {
			cons	= hCon.elements();
			while( cons.hasMoreElements() ) {
			
				con	= (OpConnector) cons.nextElement();
				con.drawArrow( true );
			}
		}
	}

/*
	public void paint( Graphics g )
	{
		paintComponent( g );
		paintBorder( g );
	
//		super.paint( g );
		
		Enumeration cons;
		OpConnector	con;
	
		synchronized( hCon ) {
			cons	= hCon.elements();
			while( cons.hasMoreElements() ) {
			
				con	= (OpConnector) cons.nextElement();
				con.drawArrow( true );
			}
		}
		
		paintChildren( g );
	}
*/

//	public boolean isFocusTraversable()
//	{
//		return false;
//	}

	public Dimension getPreferredSize()
	{
		return new Dimension( preferredWidth, preferredHeight );
	}

// -------- ClipboardOwner Methoden --------

	public void lostOwnership( Clipboard clipBoard, Transferable contents )
	{
		Operator op;
		
		try {
			op = (Operator) contents.getTransferData( Operator.flavor );
			op.dispose();
		}
		catch( Exception e1 ) {
			GUIUtil.displayError( win.getWindow(), e1, "lostOwnership" );
		}
	}
                          
// -------- Action Listener Methoden (only Popup-Menus!) --------

	public void actionPerformed( ActionEvent e )
	{
//		PromptDlg		pDlg;
		String			action	= e.getActionCommand();
		Map				ops;
		String			opName;
		Operator		op, op2;
		Operator		newOp	= null;	// duplicate, make alias, new op ...
		OpIcon			opIcon;
		Transferable	clip;
		Enumeration		aliases;
//		ConfirmDlg		confirm;

		SpectStreamSlot	slot1, slot2, slot3;
		OpConnector		con;

		Point			loc;
		Rectangle		bounds;
		Dimension		dim;
		int				i, j;

		if( !isEnabled() ) return;	// not while running the operators

		if( action == MI_EDIT ) {
			mouseClicked( new MouseEvent( popSource, MouseEvent.MOUSE_CLICKED, 0,		// Doppelklick
										  InputEvent.BUTTON1_MASK, 0, 0, 2, false ));
			
		} else if( action == MI_RENAME ) {							//-------- Rename --------
			opName = JOptionPane.showInputDialog( win.getWindow(), "Rename Operator", popSource.getName() );
			if( opName != null ) {
				renameOperator( ((OpIcon) popSource).getOperator(), opName );
			}

		} else if( action == MI_CUT ) {								//-------- Cut --------
			op = (Operator) ((OpIcon) popSource).getOperator().clone();
			if( op != null ) {
				AbstractApplication.getApplication().getClipboard().setContents( op, this );
				removeOperator( ((OpIcon) popSource).getOperator() );
			}

		} else if( action == MI_COPY ) {							//-------- Copy --------
			op = (Operator) ((OpIcon) popSource).getOperator().clone();
			if( op != null ) {
				AbstractApplication.getApplication().getClipboard().setContents( op, this );
			}

		} else if( action == MI_DUPL ) {							//-------- Duplicate --------
			newOp = (Operator) ((OpIcon) popSource).getOperator().clone();

			if( newOp != null ) {

				opIcon	= (OpIcon) newOp.getIcon();
				opName	= opIcon.getName();
				i		= opName.lastIndexOf( " copy" );	
				if( i >= 0 ) {
					try {
						if( i + 5 == opName.length() ) {
							j = 2;
						} else {
							j = Integer.parseInt( opName.substring( i + 6 )) + 1;
						}
						opIcon.setName( opName.substring( 0, i + 5 ) + ' ' + j );
					}
					catch( NumberFormatException e1 ) {
						opIcon.setName( opName + " copy" );
					}			
				} else {
					opIcon.setName( opName + " copy" );
				}
			}
			
		} else if( action == MI_MAKEALIAS ) {						//-------- Make Alias --------
			op		= ((OpIcon) popSource).getOperator();
			newOp	= (Operator) op.clone();

			if( newOp != null ) {

				try {
					newOp.turnIntoAlias( op );
					opIcon	= (OpIcon) newOp.getIcon();
					opIcon.setName( opIcon.getName() + " alias" );
				}
				catch( SyncFailedException e1 ) {
					newOp.dispose();
					newOp = null;
				}
				catch( SlotAlreadyConnectedException e2 ) {}	// ist ok, weil schon vom clone() ein Alias erzeugt wurde
			}
			
		} else if( action == MI_REMOVE ) {							//-------- Remove --------
			op		= ((OpIcon) popSource).getOperator();
			aliases	= op.getAliases();

			if( aliases.hasMoreElements() ) {
				i = JOptionPane.showOptionDialog( win.getWindow(), "You have made aliases from this Operator.\n" +
							"Do you want to remove them or shall they\n" +
							"be transformed to genuine objects?", "Request",
							JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
							optionsAliasDel, optionsAliasDel[2] );
								   
				switch( i ) {
				case 2:
					while( aliases.hasMoreElements() ) {
						op2 = (Operator) aliases.nextElement();
						op2.turnIntoGenuine();
						opIcon = (OpIcon) op2.getIcon();
						opName = opIcon.getName();
						if( opName.endsWith( " alias" )) {
							opIcon.setName( opName.substring( 0, opName.length() - 6 ));
						}
						aliases	= op.getAliases();
					}
					break;

				case 1:
					while( aliases.hasMoreElements() ) {
						op2 = (Operator) aliases.nextElement();
						removeOperator( op2 );
						aliases	= op.getAliases();	// !java bug: old Enum is obsolete after remove()
					}
					break;

				default:	// Cancel
					return;
				}
			}
			removeOperator( op );

		} else if( action == MI_PASTE ) {							//-------- Paste --------
			clip = AbstractApplication.getApplication().getClipboard().getContents( this );
			if( clip != null ) {
				try {
					newOp = (Operator) clip.getTransferData( Operator.flavor );
					newOp = (Operator) newOp.clone();
				}
				catch( Exception e1 ) {
					GUIUtil.displayError( win.getWindow(), e1, action );
				}
			}
              			
		} else if( action == MI_HIDE ) {
			popSource.dispatchEvent( new MouseEvent( popSource, MouseEvent.MOUSE_CLICKED,
											  System.currentTimeMillis(), InputEvent.ALT_MASK,
											  0, 0, 1, false ));

		} else {													//-------- unknown ---
		
			if( popSource.toString() == OpPanel.OBJ_NAME ) {					// "new"
				ops = Operator.getOperators();
				opName = (String) ops.get( action );
				if( opName != null ) {
					try {
						newOp = (Operator) Class.forName( Operator.PACKAGE + "." +
									opName ).newInstance();
					}
					catch( Exception e1 ) {
						GUIUtil.displayError( win.getWindow(), e1, action );
					}
				}

			} else if( popSource.toString() == OpConnector.OBJ_NAME ) {		// change slot
			
				bounds = popSource.getBounds();
				try {
					slot1 = ((OpConnector) popSource).getOrigin();
					slot2 = slot1.getLinked();
					divorceOperators( slot1 );
					slot3 = (SpectStreamSlot) slot1.getOwner().getSlot( action );
					con	  = null;
					
					if( slot3 != null ) {
						linkOperators( slot3, slot2 );
					} else {
						slot3 = (SpectStreamSlot) slot2.getOwner().getSlot( action );
						if( slot3 != null ) {
							linkOperators( slot1, slot3 );
						}
					}
					// relocate
					if( slot3 != null ) {
						con = getConnector( slot3 );
						if( con != null ) {
							dim = con.getSize();
							moveConnector( con, bounds.x + ((bounds.width - dim.width) >> 1),
										   bounds.y + ((bounds.height - dim.height) >> 1));
						}
					}
				}
				catch( NotBoundException e2 ) {}		// whole lotta shakin
				catch( SlotAlreadyConnectedException e3 ) {}	// whole lottaxception
				catch( SyncFailedException e4 ) {}
				catch( NoSuchElementException e5 ) {}
			}
		}

		if( newOp != null ) {		// neuer Operator angelegt, den fuegen wir ein:

//			GUISupport.sendComponentAsleep( this );
			opIcon	= (OpIcon) newOp.getIcon();
			loc		= findFreePlaceAround( popX, popY, opIcon );
			addOperator( newOp, loc.x - (OpIcon.ICON_WIDTH>>1), 
						 		loc.y - (OpIcon.ICON_HEIGHT>>1) );
//			GUISupport.wakeComponent( this );
			opIcon.requestFocus();
		}
	}

// -------- Mouse Listener Methoden --------

	public void mouseClicked( MouseEvent e ) {}

	public void mousePressed( MouseEvent e )
	{
		if( !isEnabled() ) return;	// not while running the operators

		String name	= e.getSource().toString();

		if( name == OpPanel.OBJ_NAME ) {
			panelPressed( e );
		} else if( name == OpIcon.OBJ_NAME ) {
			iconPressed( e );
		} else if( name == OpConnector.OBJ_NAME ) {
			connectorPressed( e );
		} else if( name == OpIconLabel.OBJ_NAME ) {
			labelPressed( e );
		}
	}
	
	public void mouseReleased( MouseEvent e )
	{
		if( !isEnabled() ) return;	// not while running the operators

		String name	= e.getSource().toString();

		if( name == OpPanel.OBJ_NAME ) {
			panelReleased( e );
		} else if( name == OpIcon.OBJ_NAME ) {
			iconReleased( e );
		} else if( name == OpConnector.OBJ_NAME ) {
			connectorReleased( e );
		} else if( name == OpIconLabel.OBJ_NAME ) {
			labelReleased( e );
		}
		
		if( dragContext != null ) {
			dragContext.mouseReleased( e );
			dragContext	= null;
		}
	}
	
	public void mouseEntered( MouseEvent e ) {}
	public void mouseExited( MouseEvent e ) {}

// -------- MouseMotion Listener Methoden --------

	public void mouseDragged( MouseEvent e )
	{
		if( !isEnabled() || (dragContext == null) ) return;

		dragContext.mouseDragged( e );
	}

	public void mouseMoved( MouseEvent e ) {}

// -------- listens to Panel --------

	public void panelPressed( MouseEvent e )
	{
		SpectStreamSlot	slot;

		if( e.isAltDown() ) {			// Link loeschen
			slot = getLinkAround( e.getX(), e.getY(), true );
			if( slot != null ) {
				try {
					divorceOperators( slot );
					// neuen Link ziehen
//					dragSource	= (OpIcon) slot.getOwner().getIcon();
//					dragLastX	= e.getX();
//					dragLastY	= e.getY();
//					dragType	= DRAG_LINK;
					mouseDragged( e );		// sofort den Pfeil zeichnen
	
				} catch( NotBoundException e1 ) {
					GUIUtil.displayError( win.getWindow(), e1, "panelPressed" );
				}
			}

		} else {
			requestFocus();

			if( e.isControlDown() ) {	// PopUp-Men�
				popSource	= this;
				popX		= e.getX();
				popY		= e.getY();

				this.add( popPanel );
				popPanel.show( this, e.getX(), e.getY() );
				this.remove( popPanel );
			}
		}
	}

	public void panelReleased( MouseEvent e )
	{
		SpectStreamSlot slot;
		OpConnector		con;
		Dimension		dim;
	
		if( (e.getClickCount() == 2) && !e.isAltDown() ) {
			slot = getLinkAround( e.getX(), e.getY(), true );

			if( slot == null ) {		// kein Link; dann Popup-Men�
				popSource	= this;
				popX		= e.getX();
				popY		= e.getY();
	
				this.add( popNew );
				popNew.show( this, e.getX(), e.getY() );
				this.remove( popNew );

			} else {					// Link ==> evtl. Connector sichtbar machen

				con = getConnector( slot );
//				if( (con != null) && !con.isVisible() ) {
				if( con != null ) {

					con.drawArrow( false );
					dim = con.getSize();				
					con.setLocation( e.getX() - (dim.width >> 1), e.getY() - (dim.height >> 1) );
					con.setVisible( true );
					con.drawArrow( true );
					
					updateOperator( slot.getOwner() );
				}
			}

		} else if( dragContext != null ) {
			dragContext.mouseReleased( e );
			dragContext = null;
		}

		repaint();
	}

// -------- listens to Icon --------

	public void iconPressed( MouseEvent e )
	{
		OpIcon	opIcon	= (OpIcon) e.getSource();
		Point	opLoc	= opIcon.getLocation();

		if( e.isControlDown() ) {	// PopUp-Men�
			popSource	= opIcon;
			popX		= e.getX() + opLoc.x;
			popY		= e.getY() + opLoc.y;

			this.add( popIcon );
			popIcon.show( popSource, e.getX(), e.getY() );
			this.remove( popIcon );

		} else if( e.isAltDown() ) {

			if( !opIcon.getOperator().getSlots( Slots.SLOTS_WRITER | Slots.SLOTS_FREE ).isEmpty() ) {
			
				dragContext	= new DragContext( e, DragContext.LINK );
			}
		} else {
			dragContext	= new DragContext( e, DragContext.MOVE );
		}
	}

	public void iconReleased( MouseEvent e )
	{
		Operator		op1, op2;
		SpectStreamSlot	slot1, slot2;
		OpIcon			dragSource;
		Rectangle		srcBounds;

		EditOpDlg	opDlg;
		OpIcon		opIcon	= (OpIcon) e.getSource();;
//		ConfirmDlg	confirm;
		String		opName;
		int			i;

		if( e.getClickCount() == 2 ) {

			op1	= opIcon.getOperator();
			op2	= op1.getOriginal();
			if( op2 != null ) {
				i = JOptionPane.showOptionDialog( win.getWindow(), "Aliases cannot be edited.\n" + 
						"Do you want to edit the original Operator\n" +
						"or shall this Alias be transformed\n" +
						"into a genuine object?", "Request",
						JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
						optionsAliasEdit, optionsAliasEdit[2] );

				switch( i ) {
				case 2:
					op1.turnIntoGenuine();
					opIcon = (OpIcon) op1.getIcon();
					opName = opIcon.getName();
					if( opName.endsWith( " alias" )) {
						opIcon.setName( opName.substring( 0, opName.length() - 6 ));
					}
					break;

				case 1:
					op1 = op2;
					break;

				default:	// Cancel
					return;
				}
			}

//			GUISupport.sendComponentAsleep( win );
			opDlg = new EditOpDlg( win.getWindow(), op1 );
			opDlg.setVisible( true );
			if( opDlg.getChoice() ) {
				updateOperator( op1 );
			}
			opDlg.dispose();
//			GUISupport.wakeComponent( win );

		} else {

			if( dragContext == null ) return;

			if( dragContext.hasDragStarted() ) {
				dragContext.mouseReleased( e );
				
				if( dragContext.wasDragSuccessful() ) {

					dragSource	= (OpIcon) e.getComponent();
					srcBounds	= dragSource.getBounds();
				
					switch( dragContext.getType() ) {
					case DragContext.MOVE:				//-------- Moved the Icon -------------------
	
						moveOperator( dragSource.getOperator(),
									  srcBounds.x + e.getX() - (srcBounds.width >> 1),
									  srcBounds.y + e.getY() - (srcBounds.height >> 1));
						break;
				
					case DragContext.LINK:				//-------- Drew an Arrow -------------------
	
						op1 = dragSource.getOperator();
						try {
							// for now just take the first one
							slot1	= (SpectStreamSlot) op1.getSlots( Slots.SLOTS_WRITER |
																	  Slots.SLOTS_FREE ).firstElement();
							op2		= ((OpIcon) dragContext.getTarget()).getOperator();
							slot2	= (SpectStreamSlot) op2.getSlots( Slots.SLOTS_READER |
																	  Slots.SLOTS_FREE ).firstElement();
	
							linkOperators( slot1, slot2 );
	
						}
						catch( Exception e1 ) {
							GUIUtil.displayError( win.getWindow(), e1, "link" );
						}
						break;
	
					default:
						break;
					}
				}
	
			} else {		
				dragContext.mouseReleased( e );
			}
			dragContext	= null;
		}
		
		repaint();
	}
	
// -------- listens to Connector --------

	public void connectorPressed( MouseEvent e )
	{
		OpConnector	opCon	= (OpConnector) e.getSource();
		Point		conLoc	= opCon.getLocation();
		Vector		slots;
		String		mName;

		if( e.isControlDown() ) {	// PopUp-Men�
			popSource	= opCon;
			popX		= e.getX() + conLoc.x;
			popY		= e.getY() + conLoc.y;

			// construct menu
			slots		= opCon.getOrigin().getOwner().getSlots(
							Slots.SLOTS_FREE | Slots.SLOTS_WRITER );
			mName		= MI_ORIGIN;

			for( int i = 0; i < 2; i++ ) {
			
				if( !slots.isEmpty() ) {
					mCon[ i ]	= new String[ 1 + slots.size() ];
					mCon[ i ][ 0 ] = mName;
					for( int j = 0; j < slots.size(); j++ ) {
						mCon[ i ][ j + 1 ] = slots.elementAt( j ).toString();
					}
					
				} else {
					mCon[ i ]	= new String[ 0 ];	// will not be displayed
				}
				
				slots	= opCon.getOrigin().getLinked().getOwner().getSlots(
							Slots.SLOTS_FREE | Slots.SLOTS_READER );
				mName	= MI_TARGET;
			}

			popCon = new PopupStrip( mCon, this );
			this.add( popCon );
			popCon.show( popSource, e.getX(), e.getY() );
			this.remove( popCon );

		} else {	// prepare Drag
			dragContext	= new DragContext( e, DragContext.MOVE );
		}
	}

	public void connectorReleased( MouseEvent e )
	{
		OpConnector	dragSource;
		Rectangle	srcBounds;
		OpConnector con = (OpConnector) e.getComponent();
	
		if( e.isAltDown() ) {		// hide connector

			con.drawArrow( false );
			con.setVisible( false );
			con.drawArrow( true );
					
			updateOperator( con.getOrigin().getOwner() );

		} else {

			if( dragContext == null ) return;

			if( dragContext.hasDragStarted() ) {
				dragContext.mouseReleased( e );
				
				if( dragContext.wasDragSuccessful() ) {
	
					dragSource	= (OpConnector) e.getComponent();
					srcBounds	= dragSource.getBounds();
	
					moveConnector( dragSource,
								  srcBounds.x + e.getX() - (srcBounds.width >> 1),
								  srcBounds.y + e.getY() - (srcBounds.height >> 1));
				}
		
			} else {
				dragContext.mouseReleased( e );
			}
			dragContext	= null;
		}
		
		repaint();
	}

// -------- listens to IconJLabel --------

	public void labelPressed( MouseEvent e ) {}

	public void labelReleased( MouseEvent e )
	{
		if( e.getClickCount() == 2 ) {
			
			popSource	= ((OpIconLabel) e.getComponent()).getOpIcon();
			actionPerformed( new ActionEvent( this, ActionEvent.ACTION_PERFORMED,
											  MI_RENAME ));
		}
	}

// -------- private Methoden --------

	/*
	 *	Vergroessert die Panelflaeche, wenn eine Componente
	 *	ueber dessen Dimensionen hinausragt
	 */
	protected void validateSize( Component c )
	{
		Dimension	dim = getSize();
		Rectangle	bnd;
		Container	parent;

		if( c.toString() == OpIcon.OBJ_NAME ) {
			bnd = ((OpIcon) c).getUnionBounds();
		} else {
			bnd = c.getBounds();
		}
		
		if( !contains( bnd.x + bnd.width + 3, bnd.y + bnd.height + 3 )) {
			
			preferredWidth	= Math.max( dim.width,  bnd.x + bnd.width  + 3 );
			preferredHeight	= Math.max( dim.height, bnd.y + bnd.height + 3 );
			parent			= getParent();
			parent.doLayout();
		}
	}

	/*
	 *	Sucht nach einem freien Ort fuer ein OpIcon
	 *
	 *	@param	x		Ausgangs-Punkt, X-Koordinate
	 *	@param	y		Ausgangs-Punkt, Y-Koordinate
	 *	@param	src		OpIcon, fuer das Platz gesucht werden soll
	 *	@return	Mittelpunkt fuer das neue Icon
	 */
	private Point findFreePlaceAround( int x, int y, OpIcon src )
	{
		double	maxArc	= 2.0 * Math.PI;
		double	arc		= maxArc;
		double	r		= 0.0;
		int		cx, cy;
		
		do {
			cx	= x + ((int) (r * Math.cos( arc )));
			cy 	= y + ((int) (r * Math.sin( arc )));
			if( getOpIconAround( cx, cy, src, 4, 4 ) == null ) {
				
				// keine Punkte zu weit links/oben
				if( (cx >= (OpIcon.ICON_WIDTH>>1)) && (cy >= (OpIcon.ICON_HEIGHT>>1)) ) {
					return new Point( cx, cy );
				}
			}
			if( arc >= maxArc ) {	// naechster konzentrischer Kreis mit groesserem Radius
				arc  = 0.0;
				r	+= 6.0;
			} else {				// Kreis ziehen mit konstanter Strecke
				arc += 6.0 / r;
			}
		} while( true );	// irgendwann kommen wir unten/rechts an einen freien Punkt
	}
	
	/*
	 *	Liefert den Link, der in der Naehe eines Punktes des Containers liegt
	 *
	 *	@param	origin	true, wenn der Writer-Slot geliefert werden soll
	 *					(also der Pfeilanfang), false fuer den Reader (Pfeilende)
	 *	@return	null, wenn kein Link in der Naehe
	 */
	private SpectStreamSlot getLinkAround( int x, int y, boolean origin ) 
	{
		Enumeration 	cons;
		OpConnector		con;
		int				dist		= 5;
		int				newDist;
		SpectStreamSlot	nearSlot	= null;
	
		synchronized( hCon ) {
			cons	= hCon.elements();
			while( cons.hasMoreElements() ) {
			
				con		= (OpConnector) cons.nextElement();
				newDist	= OpConnector.getDistance( con, x, y );
				if( (newDist >= 0) && (newDist < dist) ) {
				
					nearSlot	= con.getOrigin();
				}
			}
		}

		if( nearSlot != null ) {
		
			if( (nearSlot.getFlags() & (origin ? Slots.SLOTS_WRITER : Slots.SLOTS_READER )) == 0 ) {
			
				nearSlot = nearSlot.getLinked();
			}
		}
		return nearSlot;
	}
}
// class OpPanel