/*
 * Copyright 2017 Idorsia Pharmaceuticals Ltd., Hegenheimermattweg 91, CH-4123 Allschwil, Switzerland
 *
 * This file is part of DataWarrior.
 * 
 * DataWarrior is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * DataWarrior is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with DataWarrior.
 * If not, see http://www.gnu.org/licenses/.
 *
 * @author Thomas Sander
 */

package com.actelion.research.gui;

import com.actelion.research.chem.*;
import com.actelion.research.gui.clipboard.IClipboardHandler;
import com.actelion.research.gui.dnd.MoleculeDragAdapter;
import com.actelion.research.gui.dnd.MoleculeDropAdapter;
import com.actelion.research.gui.dnd.MoleculeTransferable;
import com.actelion.research.gui.hidpi.HiDPIHelper;
import com.actelion.research.util.ColorHelper;

import javax.swing.*;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

public class JStructureView extends JPanel implements ActionListener,MouseListener,StructureListener {
    static final long serialVersionUID = 0x20061113;

    private static final String ITEM_COPY = "Copy Structure";
	private static final String ITEM_PASTE = "Paste Structure";

    private ArrayList<StructureListener> mListener;
	private String mIDCode;
	private StereoMolecule mMol,mDisplayMol;
    private Depictor2D mDepictor;
	private boolean mShowBorder, mAllowFragmentStatusChangeOnPasteOrDrop,mIsDraggingThis;
	private int mChiralTextPosition,mDisplayMode;
	private String[] mAtomText;
	private IClipboardHandler mClipboardHandler;
	protected MoleculeDropAdapter mDropAdapter = null;
	protected int mAllowedDragAction;
	protected int mAllowedDropAction;
	protected boolean borderFlag = true; // Allow subclasses to disable border painting

	public JStructureView() {
        this(null);
		}

	/**
	 * This creates a standard structure view where the displayed molecule is
	 * used for D&D and clipboard transfer after removing atom colors and bond highlights.
	 * @param mol used for display, clipboard copy and d&d
	 */
	public JStructureView(StereoMolecule mol) {
        this(mol, DnDConstants.ACTION_COPY_OR_MOVE, DnDConstants.ACTION_COPY_OR_MOVE);
	    }

	/**
	 * This creates a structure view that distinguishes between displayed molecule
	 * and the one being used for D&D and clipboard transfer. Use this if the displayed
	 * molecule is structurally different, e.g. uses custom atom labels or additional
	 * illustrative atoms or bonds, which shall not be copied.
	 * Custom atom colors or highlighted bonds don't require a displayMol.
	 * @param mol used for clipboard copy and d&d; used for display if displayMol is null
	 * @param displayMol null if mol shall be displayed
	 */
	public JStructureView(StereoMolecule mol, StereoMolecule displayMol) {
        this(mol, displayMol, DnDConstants.ACTION_COPY_OR_MOVE, DnDConstants.ACTION_COPY_OR_MOVE);
	    }

	public JStructureView(int dragAction, int dropAction) {
        this(null, dragAction, dropAction);
	    }

	/**
	 * This creates a standard structure view where the displayed molecule is
	 * used for D&D and clipboard transfer after removing atom colors and bond highlights.
	 * @param mol used for display, clipboard copy and d&d
	 * @param dragAction
	 * @param dropAction
	 */
	public JStructureView(StereoMolecule mol, int dragAction, int dropAction) {
        this(mol, null, dragAction, dropAction);
		}

	/**
	 * This creates a structure view that distinguishes between displayed molecule
	 * and the one being used for D&D and clipboard transfer. Use this if the displayed
	 * molecule is structurally different, e.g. uses custom atom labels or additional
	 * illustrative atoms or bonds, which shall not be copied.
	 * Custom atom colors or highlighted bonds don't require a displayMol.
	 * @param mol used for clipboard copy and d&d; used for display if displayMol is null
	 * @param displayMol null if mol shall be displayed
	 * @param dragAction
	 * @param dropAction
	 */
	public JStructureView(StereoMolecule mol, StereoMolecule displayMol, int dragAction, int dropAction) {
		mMol = (mol == null) ? new StereoMolecule() : new StereoMolecule(mol);
		mDisplayMol = (displayMol == null) ? mMol : displayMol;
		mDisplayMode = AbstractDepictor.cDModeHiliteAllQueryFeatures;
		addMouseListener(this);
		initializeDragAndDrop(dragAction, dropAction);
	    }

    /**
     * Call this in order to get clipboard support:
     * setClipboardHandler(new ClipboardHandler());
     */
	public void setClipboardHandler(IClipboardHandler h) {
		mClipboardHandler = h;
	    }

	public IClipboardHandler getClipboardHandler() {
		return mClipboardHandler;
	    }

	/**
	 * Sets the display mode for the Depictor. The default is
	 * AbstractDepictor.cDModeHiliteAllQueryFeatures.
	 * @param mode
	 */
	public void setDisplayMode(int mode) {
	    mDisplayMode = mode;
	    }

	/**
	 * Defines additional atom text to be displayed in top right
	 * position of some/all atom labels. If the atom is charged, then
	 * the atom text is drawn right of the atom charge.
	 * If using atom text make sure to update it accordingly, if atom
	 * indexes change due to molecule changes.
	 * Atom text is not supported for MODE_REACTION, MODE_MULTIPLE_FRAGMENTS or MODE_MARKUSH_STRUCTURE.
	 * @param atomText null or String array matching atom indexes (may contain null entries)
	 */
	public void setAtomText(String[] atomText) {
		mAtomText = atomText;
		}

	public void setEnabled(boolean enable) {
		if (enable != isEnabled()) {
			repaint();
			if (mDropAdapter != null)
				mDropAdapter.setActive(enable);
			}
		super.setEnabled(enable);
		}

	/**
	 * When fragment status change on drop is allowed then dropping a fragment (molecule)
	 * on a molecule (fragment) inverts the status of the view's chemical object.
	 * As default status changes are prohibited.
	 * @param allow
	 */
	public void setAllowFragmentStatusChangeOnPasteOrDrop(boolean allow) {
		mAllowFragmentStatusChangeOnPasteOrDrop = allow;
		}

	public boolean canDrop() {
		return isEnabled() && !mIsDraggingThis;
	    }

	@Override
	public synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);

        Dimension theSize = getSize();
		Insets insets = getInsets();
		theSize.width -= insets.left + insets.right;
		theSize.height -= insets.top + insets.bottom;

        if(theSize.width <= 0 || theSize.height <= 0)
            return;

        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		if (mDisplayMol != null && mDisplayMol.getAllAtoms() != 0) {
			mDepictor = new Depictor2D(mDisplayMol);
            mDepictor.setDisplayMode(mDisplayMode);
            mDepictor.setAtomText(mAtomText);

			if (!isEnabled())
                mDepictor.setOverruleColor(ColorHelper.getContrastColor(Color.GRAY, getBackground()), getBackground());
			else
				mDepictor.setForegroundColor(getForeground(), getBackground());

			int avbl = HiDPIHelper.scale(AbstractDepictor.cOptAvBondLen);
			mDepictor.validateView(g, new Rectangle2D.Double(insets.left, insets.top, theSize.width,theSize.height),
								   AbstractDepictor.cModeInflateToMaxAVBL | mChiralTextPosition | avbl);
            mDepictor.paint(g);
			}

		if (borderFlag && mShowBorder) {
			g.setColor(Color.gray);
			g.drawRect(insets.left,insets.top,theSize.width - 1,theSize.height - 1);
			g.drawRect(insets.left + 1,insets.top + 1,theSize.width - 3,theSize.height - 3);
			}
		}

	public void setIDCode(String idcode) {
		setIDCode(idcode, null);
	    }

	public synchronized void setIDCode(String idcode, String coordinates) {
		if (idcode != null && idcode.length() == 0)
			idcode = null;

		if (mIDCode == null && idcode == null)
			return;

		if (mIDCode != null && idcode != null && mIDCode.equals(idcode))
			return;

		new IDCodeParser(true).parse(mMol, idcode, coordinates);
		mDisplayMol = mMol;

        mIDCode = idcode;
        repaint();
        informListeners();
		}

	/**
	 * Updates the molecule used for display, drag & drop and clipboard transfer.
	 * Also triggers a repaint().
	 * @param mol new molecule used for display, clipboard copy and d&d; may be null
	 */
	public synchronized void structureChanged(StereoMolecule mol) {
		if (mol == null) {
			mMol.deleteMolecule();
			}
		else {
			mol.copyMolecule(mMol);
			}

		mDisplayMol = mMol;
        structureChanged();
		}

	/**
	 * Updates both molecules used for display and for drag & drop/clipboard transfer.
	 * Also triggers a repaint().
	 * @param mol new molecule used for display; may be null
	 * @param displayMol new molecule used for clipboard copy and d&d, may be null
	 */
	public synchronized void structureChanged(StereoMolecule mol, StereoMolecule displayMol) {
		if (mol == null) {
			mMol.deleteMolecule();
			}
		else {
			mol.copyMolecule(mMol);
			}

		mDisplayMol = displayMol;
        structureChanged();
		}

	/**
	 * Should only be called if JStructureView's internal Molecule is changed
	 * from outside as: theStructureView.getMolecule().setFragment(false);
	 * The caller is responsible to update displayMol also, if it is different from
	 * the molecule.
	 */
	public synchronized void structureChanged() {
		mIDCode = null;
		repaint();
		informListeners();
		}

	public StereoMolecule getMolecule() {
		return mMol;
		}

	public StereoMolecule getDisplayMolecule() {
		return mDisplayMol;
		}

    public AbstractDepictor getDepictor() {
        return mDepictor;
        }

    public void addStructureListener(StructureListener l) {
		if(mListener == null)
			mListener = new ArrayList<StructureListener>();

		mListener.add(l);
		}

    public void removeStructureListener(StructureListener l) {
        if(mListener != null)
            mListener.remove(l);
        }

	public void setChiralDrawPosition(int p) {
		mChiralTextPosition = p;
		}

	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}

	public void mousePressed(MouseEvent e) {
		handlePopupTrigger(e);
		}

	public void mouseReleased(MouseEvent e) {
		handlePopupTrigger(e);
		}

	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand().equals(ITEM_COPY)) {
			mClipboardHandler.copyMolecule(mMol);
			}
		if (e.getActionCommand().equals(ITEM_PASTE)) {
			StereoMolecule mol = mClipboardHandler.pasteMolecule();
			if (mol != null) {
				if (!mAllowFragmentStatusChangeOnPasteOrDrop)
					mol.setFragment(mMol.isFragment());
				mMol = mol;
				mDisplayMol = mol;
				structureChanged();
				}
			}
		}

	private void handlePopupTrigger(MouseEvent e) {
		if (mMol != null && e.isPopupTrigger() && mClipboardHandler != null) {
			JPopupMenu popup = new JPopupMenu();

			if (mMol.getAllAtoms() != 0) {
				JMenuItem item = new JMenuItem(ITEM_COPY);
				item.addActionListener(this);
				popup.add(item);
				}

			JMenuItem item = new JMenuItem(ITEM_PASTE);
			item.addActionListener(this);
			popup.add(item);

			popup.show(this, e.getX(), e.getY());
			}
		}

	private void informListeners() {
		if (mListener != null)
			for (int i = 0; i<mListener.size(); i++)
				mListener.get(i).structureChanged(mMol);
		}

	private void initializeDragAndDrop(int dragAction, int dropAction) {
		final JStructureView outer = this;
		mAllowedDragAction = dragAction;
		mAllowedDropAction = dropAction;
		mAllowFragmentStatusChangeOnPasteOrDrop = false;

		if(dragAction != DnDConstants.ACTION_NONE){
			new MoleculeDragAdapter(this) {
				public Transferable getTransferable(Point origin) {
					return getMoleculeTransferable(origin);
				}

				public void onDragEnter() {
					outer.onDragEnter();
				}

				public void dragIsValidAndStarts() {
					mIsDraggingThis = true;
					}

				/*	public void onDragOver() {
					 outer.onDragOver();
					 }
				 */
				public void onDragExit() {
					outer.onDragExit();
				}

				public void dragDropEnd(DragSourceDropEvent e) {
					mIsDraggingThis = false;
				}
			};
		}

		if(dropAction != DnDConstants.ACTION_NONE) {
			mDropAdapter = new MoleculeDropAdapter() {
				public void onDropMolecule(StereoMolecule m,Point pt) {
					if (m != null && canDrop()){
						boolean isFragment = mMol.isFragment();
						mMol = new StereoMolecule(m);
				        mMol.removeAtomColors();
				        mMol.removeBondHiliting();
				        if (!mAllowFragmentStatusChangeOnPasteOrDrop)
				        	mMol.setFragment(isFragment);
				        mDisplayMol = mMol;
						repaint();
						informListeners();
						onDrop();
					}
					updateBorder(false);
				}

				public void dragEnter(DropTargetDragEvent e) {
					boolean drop = canDrop() && isDropOK(e) ;
					if (!drop)
						e.rejectDrag();
					updateBorder(drop);
				}

				public void dragExit(DropTargetEvent e) {
					updateBorder(false);
				}
			};

			new DropTarget(this, mAllowedDropAction, mDropAdapter, true);
//			new DropTarget(this,mAllowedDropAction,mDropAdapter,true, getSystemFlavorMap());
		}
	}


	protected Transferable getMoleculeTransferable(Point pt) {
		return new MoleculeTransferable(mMol);
	}

	// Drag notifications if needed by subclasses
	protected void onDragEnter() {}
	protected void onDragExit() {}
	protected void onDragOver() {}
	protected void onDrop() {}

	private void updateBorder(boolean showBorder) {
		if(mShowBorder != showBorder){
			mShowBorder = showBorder;
			repaint();
		}
	}

	public java.awt.datatransfer.FlavorMap getSystemFlavorMap() {
	    return new OurFlavorMap();
	    }

    // This class is needed for inter-jvm drag&drop. Although not neccessary for standard environments, it prevents
    // nasty "no native data was transfered" errors. It still might create ClassNotFoundException in the first place by
    // the SystemFlavorMap, but as I found it does not hurt, since the context classloader will be installed after
    // the first call. I know, that this depends heavely on a specific behaviour of the systemflavormap, but for now
    // there's nothing I can do about it.
    static class OurFlavorMap implements FlavorMap, FlavorTable {
    	public java.util.Map<DataFlavor,String> getNativesForFlavors(DataFlavor[] dfs) {
    /*	    System.out.println("getNativesForFlavors " + dfs.length);
    	    for (int i = 0; i < dfs.length; i++)
    		    System.out.println(" -> " + dfs[i]);
    */
    	    return SystemFlavorMap.getDefaultFlavorMap().getNativesForFlavors(dfs);
    	    }
    
    	public java.util.Map<String,DataFlavor> getFlavorsForNatives(String[] natives) {
    /*	    System.out.println("getFlavorsForNatives " + natives.length);
    	    for (int i = 0; i < natives.length; i++)
    	        System.out.println(" -> " + natives[i]);
    */
    	    return SystemFlavorMap.getDefaultFlavorMap().getFlavorsForNatives(natives);
    	    }
    
    	public synchronized java.util.List<DataFlavor> getFlavorsForNative(String nat) {
    //	    System.out.println("getFlavorsForNative " + nat);
    	    
    	    return ((SystemFlavorMap)SystemFlavorMap.getDefaultFlavorMap()).getFlavorsForNative(nat);
    	    }
    
    	public synchronized java.util.List<String> getNativesForFlavor(DataFlavor flav) {
    //	    System.out.println("getNativesForFlavor " + flav);
    	    return ((SystemFlavorMap)SystemFlavorMap.getDefaultFlavorMap()).getNativesForFlavor(flav);
    	    }
        }
    }
