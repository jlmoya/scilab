/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2007 - INRIA - Allan CORNET
 * Copyright (C) 2007 - INRIA - Sylvestre Ledru
 *
 * Copyright (C) 2012 - 2016 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.gui.utils;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Class used to modify look and feel in Scilab GUIs
 * @author Allan CORNET
 * @author Sylvestre Ledru
 */
public class LookAndFeelManager {

    private boolean ret;

    /**
     * The installed look-and-feels, queried LIVE rather than cached in a static.
     *
     * This used to be a static field initialised at class-load time. That made the
     * list depend on WHEN this class happened to be loaded: a look and feel
     * registered afterwards -- as FlatLafSetup.install() does for the macOS themes
     * -- was invisible here, so isSupportedLookAndFeel() returned false for a theme
     * that was in fact available and the caller silently fell back to the system
     * look and feel. Querying UIManager each time removes that ordering hazard;
     * UIManager already holds the array, so there is nothing to cache.
     */
    private static UIManager.LookAndFeelInfo[] installedLookAndFeels() {
        return UIManager.getInstalledLookAndFeels();
    }

    /**
     * Constructor
     */
    public LookAndFeelManager() {
    }

    /**
     * Get Installed Look and Feels
     * @return an array of String
     */
    public String[] getInstalledLookAndFeels() {
        UIManager.LookAndFeelInfo[] available = installedLookAndFeels();
        String[] retStrings = new String[available.length];
        for (int i = 0; i < available.length; i++) {
            retStrings[i] =  available[i].getClassName();
        }
        return retStrings;
    }

    /**
     * Get numbers of Installed Look and Feels
     * @return size of array of String
     */
    public int numbersOfInstalledLookAndFeels() {
        return installedLookAndFeels().length;
    }

    /**
     * Get Look and Feel
     * @return a String
     */
    public String getCurrentLookAndFeel() {
        return UIManager.getLookAndFeel().getClass().getName();
    }

    /**
     * Is this look and feel exists or not
     * @param lookandfeel the name of the lookandfeel we want check if exists
     * @return if it exists or not
     */
    public boolean isSupportedLookAndFeel(String lookandfeel) {
        UIManager.LookAndFeelInfo[] available = installedLookAndFeels();
        for (int i = 0; i < available.length; i++) {
            if (available[i].getClassName().equals(lookandfeel)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set Look and Feel
     * @param lookandfeel a String
     * @return a boolean
     */
    public boolean setLookAndFeel(final String lookandfeel) {
        ret = false;
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    try {
                        UIManager.setLookAndFeel(lookandfeel);
                        ret = true;
                    } catch (UnsupportedLookAndFeelException e) {
                        System.err.println("Cannot find this look and feel:");
                        System.err.println(e.getLocalizedMessage());
                    } catch (ClassNotFoundException e) {
                        System.err.println("LookAndFeel class could not be found:");
                        System.err.println(e.getLocalizedMessage());
                    } catch (IllegalAccessException e) {
                        System.err.println("Illegal access while setting the Look And Feel:");
                        System.err.println(e.getLocalizedMessage());
                    } catch (InstantiationException e) {
                        System.err.println("Instantiation error while setting the Look And Feel:");
                        System.err.println(e.getLocalizedMessage());
                    }
                }
            });
        } catch (InterruptedException e) { }
        catch (InvocationTargetException e) {
            System.err.println(e);
        }

        return ret;
    }

    /**
     * Set System Look and Feel
     * @return a boolean
     */
    public boolean setSystemLookAndFeel() {
        return setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
}
